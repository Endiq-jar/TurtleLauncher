package com.endiq.turtlelauncher.feature.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.endiq.turtlelauncher.context.ContextExecutor
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.path.UrlManager
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong

/**
 * Dual-channel network acceleration.
 *
 * The launcher's downloads are single TCP flows, and a single flow is limited by the Wi-Fi link it
 * happens to ride on. When the device has both Wi-Fi and mobile data validated, this splits one
 * file into two byte ranges and fetches them at the same time - one range bound to Wi-Fi, the other
 * bound to mobile data - then joins the halves. Two carriers instead of one, per file.
 *
 * It is deliberately conservative, because a launcher download must never come out corrupt:
 *
 *  - only HTTP downloads that advertise byte ranges and have a usable Content-Length are split;
 *  - only when two *different* transports are validated (Wi-Fi plus cellular, not Wi-Fi plus VPN);
 *  - only above [MIN_SPLIT_BYTES], where a split pays for the extra handshakes;
 *  - the joined file is size-checked, and *any* failure throws the partials away and returns
 *    false, so the caller silently falls back to the ordinary single-stream download.
 *
 * Everything is opt-out in Launcher settings ("Dual channel network acceleration"); mobile data
 * usage is the user's call, so the switch explains that half of each large download can use it.
 */
object DualChannelNetwork {
    private const val TAG = "DualChannel"

    /** Below this, a split costs more than it saves. */
    const val MIN_SPLIT_BYTES: Long = 4L * 1024 * 1024

    /**
     * Hosts that already answered "no ranges" or "too small" this session. Asset-heavy downloads
     * touch hundreds of files on the same CDN; without this every single one would pay a probe.
     */
    private val skipHosts = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private const val BUFFER_SIZE = 64 * 1024
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 60_000

    /** One usable path to the internet. [network] is null for the system default route. */
    data class Channel(val id: String, val label: String, val network: Network?)

    /** Progress callback that Java callers can implement with a lambda. */
    fun interface ProgressSink {
        fun update(done: Long, total: Long)
    }

    fun isEnabled(): Boolean = runCatching { AllSettings.dualChannelNetwork.getValue() }.getOrDefault(true)

    private fun connectivity(context: Context): ConnectivityManager? =
        runCatching {
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        }.getOrNull()

    /**
     * The transports this device can download through right now, Wi-Fi first. Cellular is only
     * listed when it is validated, i.e. actually carrying data - a SIM with mobile data off never
     * appears here, so the feature cannot silently dial out.
     */
    fun availableChannels(context: Context): List<Channel> {
        val manager = connectivity(context) ?: return emptyList()
        val networks = runCatching { manager.allNetworks.toList() }.getOrDefault(emptyList())
        val channels = mutableListOf<Channel>()
        for (network in networks) {
            val capabilities = runCatching { manager.getNetworkCapabilities(network) }.getOrNull() ?: continue
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) continue
            val id = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cell"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> null
            } ?: continue
            if (channels.any { it.id == id }) continue
            val label = when (id) {
                "wifi" -> "Wi-Fi"
                "cell" -> "Mobile data"
                else -> "Ethernet"
            }
            channels.add(Channel(id, label, network))
        }
        // Wi-Fi/Ethernet first: the cheap link starts the file, the metered one joins it.
        return channels.sortedBy { when (it.id) { "wifi" -> 0; "ethernet" -> 1; else -> 2 } }
    }

    /** Human summary for settings, e.g. "Wi-Fi + Mobile data". */
    fun describe(context: Context): String {
        val channels = availableChannels(context)
        return if (channels.isEmpty()) "No network" else channels.joinToString(" + ") { it.label }
    }

    /** True when the feature is on and two links could actually be used right now. */
    fun isAccelerating(context: Context): Boolean = isEnabled() && availableChannels(context).size >= 2

    /**
     * Tries to fetch [url] into [target] over two channels. Returns false whenever the fancy path
     * does not apply or fails - by then all partial files are deleted and [target] is untouched,
     * so the caller's normal downloader takes over as if nothing happened.
     */
    @JvmStatic
    fun accelerate(url: String, target: File, sink: ProgressSink?): Boolean {
        if (!isEnabled()) return false
        val context = runCatching { ContextExecutor.getApplication() }.getOrNull() ?: return false
        val channels = availableChannels(context)
        if (channels.size < 2) return false
        if (target.isFile && target.length() > 0L) return false

        val cleanUrl = url.trim()
        if (!cleanUrl.startsWith("https://") && !cleanUrl.startsWith("http://")) return false

        val host = runCatching { java.net.URI(cleanUrl).host }.getOrNull().orEmpty()
        if (host.isNotEmpty() && host in skipHosts) return false

        val layout = runCatching { probe(cleanUrl) }.getOrElse { error ->
            Logging.d(TAG, "No range support for $cleanUrl: ${error.message}")
            if (host.isNotEmpty()) skipHosts.add(host)
            null
        } ?: return false
        if (!layout.ranges || layout.size < MIN_SPLIT_BYTES) {
            if (host.isNotEmpty()) skipHosts.add(host)
            return false
        }

        val parts = listOf(
            File(target.parentFile, target.name + ".dc0"),
            File(target.parentFile, target.name + ".dc1"),
        )
        parts.forEach { runCatching { it.delete() } }
        val done = AtomicLong(0L)
        val total = layout.size
        val half = total / 2

        fun report() = runCatching { sink?.update(done.get().coerceAtMost(total), total) }

        report()
        val outcome = runCatching {
            target.parentFile?.mkdirs()
            // A channel failure must not escape as an unhandled thread exception (that would print
            // a stack trace and leave the caller guessing); it is collected and rethrown below.
            val failure = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
            val threads = listOf(
                Thread {
                    runCatching { fetchRange(channels[0], cleanUrl, 0L, half - 1, parts[0], done, ::report) }
                        .onFailure { failure.compareAndSet(null, it) }
                }.apply { name = "dual-channel-wifi"; isDaemon = true },
                Thread {
                    runCatching { fetchRange(channels[1], cleanUrl, half, total - 1, parts[1], done, ::report) }
                        .onFailure { failure.compareAndSet(null, it) }
                }.apply { name = "dual-channel-cell"; isDaemon = true },
            )
            threads.forEach { it.start() }
            threads.forEach { it.join() }
            failure.get()?.let { throw it }
            if (parts.any { !it.isFile || it.length() != expectedLength(it, total, half) }) {
                throw IllegalStateException("a channel returned an incomplete range")
            }
            join(parts, target, total)
        }

        parts.forEach { runCatching { it.delete() } }
        if (outcome.isFailure) {
            outcome.exceptionOrNull()?.let { error ->
                Logging.w(TAG, "Dual channel download failed, falling back to one stream", error)
            }
            runCatching { target.delete() }
            return false
        }
        report()
        Logging.i(TAG, "Dual channel download finished ($total bytes over 2 links)")
        return true
    }

    private fun expectedLength(part: File, total: Long, half: Long): Long =
        if (part.name.endsWith(".dc0")) half else total - half

    private data class Layout(val size: Long, val ranges: Boolean)

    /** HEAD (and, if the server refuses HEAD, a one-byte ranged GET) to learn size + range support. */
    private fun probe(url: String): Layout {
        val client = UrlManager.createOkHttpClient().newBuilder()
            .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val head = Request.Builder().url(url).head().build()
        client.newCall(head).execute().use { response ->
            val length = response.header("Content-Length")?.toLongOrNull() ?: -1L
            val ranges = response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
            if (response.isSuccessful && length > 0L) return Layout(length, ranges)
        }
        // Some CDNs reject HEAD but honour Range: prove both at once.
        val rangedGet = Request.Builder().url(url).header("Range", "bytes=0-0").get().build()
        client.newCall(rangedGet).execute().use { response ->
            val contentRange = response.header("Content-Range")
            val length = contentRange?.substringAfter('/')?.toLongOrNull() ?: -1L
            if (response.code == 206 && length > 0L) return Layout(length, true)
        }
        throw IllegalStateException("no usable size/range information")
    }

    /** Streams one byte range of [url] into [part], reporting bytes into [done]. */
    private fun fetchRange(
        channel: Channel,
        url: String,
        from: Long,
        to: Long,
        part: File,
        done: AtomicLong,
        report: () -> Unit,
    ) {
        val client = clientFor(channel)
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$from-$to")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code != 206 && response.code != 200) {
                throw IllegalStateException("range request failed with HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("empty body")
            body.byteStream().use { input ->
                RandomAccessFile(part, "rw").use { output ->
                    output.setLength(0L)
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        done.addAndGet(read.toLong())
                        report()
                    }
                }
            }
        }
    }

    /** Concatenates the two ranges into the final file and checks the length. */
    private fun join(parts: List<File>, target: File, total: Long) {
        target.parentFile?.mkdirs()
        target.outputStream().use { out ->
            for (part in parts) part.inputStream().use { it.copyTo(out, BUFFER_SIZE) }
        }
        if (target.length() != total) {
            throw IllegalStateException("joined ${target.length()} of $total bytes")
        }
    }

    /**
     * An OkHttp client bound to one [Channel]: its sockets go out through that network, and names
     * are resolved by that network's own DNS, which is what makes the two ranges genuinely use two
     * different links instead of both following the default route.
     */
    private fun clientFor(channel: Channel): OkHttpClient {
        val network = channel.network
            ?: return UrlManager.createOkHttpClient()
        // An explicit Dns implementation rather than a lambda: okhttp3.Dns is a Kotlin interface,
        // so it has no SAM conversion from Kotlin code.
        val dns = object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> =
                runCatching { network.getAllByName(hostname).toList() }
                    .getOrElse { Dns.SYSTEM.lookup(hostname) }
        }
        return UrlManager.createOkHttpClientBuilder { builder ->
            builder
                .socketFactory(network.socketFactory)
                .dns(dns)
                .connectTimeout(CONNECT_TIMEOUT_MS.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT_MS.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
        }.build()
    }
}
