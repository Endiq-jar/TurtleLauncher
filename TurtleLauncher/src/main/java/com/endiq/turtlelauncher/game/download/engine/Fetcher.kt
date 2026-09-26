/*
 * Turtle Launcher
 * Copyright (C) 2025 Endiq and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.endiq.turtlelauncher.game.download.engine

import com.endiq.turtlelauncher.path.URL_USER_AGENT
import com.endiq.turtlelauncher.utils.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "Fetcher"

/** Response status code, final URL and header set (case-insensitive keys) */
class ResponseInfo(
    val code: Int,
    val url: URL,
    headers: Map<String, List<String>>
) {
    private val values: Map<String, String> = headers.entries
        .associate { it.key.lowercase() to it.value.first() }

    fun header(name: String): String? = values[name.lowercase()]

    fun headerAsLong(name: String): Long = header(name)?.trim()?.toLongOrNull() ?: -1L

    companion object {
        /**
         * Enumerates response headers by index
         */
        fun of(code: Int, url: URL, connection: HttpURLConnection): ResponseInfo {
            val headers = LinkedHashMap<String, MutableList<String>>()
            var index = 0
            while (true) {
                val value = connection.getHeaderField(index) ?: break
                val name = connection.getHeaderFieldKey(index)
                if (!name.isNullOrEmpty()) {
                    headers.getOrPut(name) { mutableListOf() }.add(value)
                }
                index++
            }
            return ResponseInfo(code, url, headers)
        }
    }
}

/** Response body transfer encoding; brokered compression (e.g. br) is unsupported and fails the attempt */
enum class ContentEncoding {
    IDENTITY, GZIP;

    fun wrap(input: InputStream): InputStream =
        if (this == GZIP) GZIPInputStream(input) else input

    companion object {
        fun fromHeaders(response: ResponseInfo): ContentEncoding {
            val encoding = response.header("content-encoding") ?: return IDENTITY
            return when {
                encoding.isEmpty() || encoding == "identity" -> IDENTITY
                encoding.equals("gzip", ignoreCase = true) -> GZIP
                else -> throw IOException("Unsupported content encoding: $encoding")
            }
        }
    }
}

/**
 * Range-resume eligibility context: only a response with 200 OK + accept-ranges: bytes + identity encoding + known length,
 * plus a strong ETag or Last-Modified, qualifies for resume.
 */
class ResumeContext private constructor(
    private val url: URL,
    private val contentLength: Long,
    private val strongETag: String?,
    private val lastModified: String?
) {
    /** Received bytes in uncompressed terms, aligned with the Content-Range start semantics */
    var countUncompressed = 0L
        private set

    fun addBytes(count: Long) {
        countUncompressed += count
    }

    fun ifRange(): String = strongETag ?: lastModified!!

    /** Whether a 206 response matches the existing partial: validates length, validators and Content-Range field by field */
    fun canResume(code: Int, response: ResponseInfo): Boolean {
        if (code != HttpURLConnection.HTTP_PARTIAL) return false
        if (ContentEncoding.fromHeaders(response) != ContentEncoding.IDENTITY) return false

        val bodyLength = response.headerAsLong("content-length")
        if (contentLength != bodyLength + countUncompressed) return false

        if (strongETag != null) {
            if (strongETag != response.header("etag")) return false
        } else {
            if (url != response.url) return false
            if (lastModified != response.header("last-modified").orEmpty()) return false
        }

        val match = CONTENT_RANGE_PATTERN.find(response.header("content-range").orEmpty())
            ?: return false
        val (start, end, total) = match.destructured
        val startValue = start.toLongOrNull() ?: return false
        val endValue = end.toLongOrNull() ?: return false
        val totalValue = total.toLongOrNull() ?: return false

        if (startValue != countUncompressed || endValue < startValue || totalValue != contentLength) {
            return false
        }
        return endValue - startValue + 1 == bodyLength
    }

    fun hasPartialContent(): Boolean = countUncompressed in 1 until contentLength

    companion object {
        private val CONTENT_RANGE_PATTERN = Regex("""bytes ([0-9]+)-([0-9]+)/([0-9]+)""")

        fun of(response: ResponseInfo): ResumeContext? {
            if (response.code != HttpURLConnection.HTTP_OK) return null
            if (!response.header("accept-ranges").orEmpty().equals("bytes", ignoreCase = true)) return null
            if (ContentEncoding.fromHeaders(response) != ContentEncoding.IDENTITY) return null

            val contentLength = response.headerAsLong("content-length")
            if (contentLength < 0) return null

            val eTag = response.header("etag")
            val strongETag = eTag?.takeIf { it.isNotBlank() && !it.startsWith("W/", ignoreCase = true) }
            val lastModified = response.header("last-modified").orEmpty()
            if (strongETag == null && lastModified.isBlank()) return null

            return ResumeContext(response.url, contentLength, strongETag, lastModified)
        }
    }
}

/** Failure wrapper for one candidate source after its retries are exhausted */
class DownloadException(
    val url: String,
    cause: Throwable
) : IOException("Unable to download $url, ${cause.message}", cause)

/** The downloaded content's checksum doesn't match the declared one */
class ChecksumMismatchException(
    val algorithm: String,
    val expected: String,
    val actual: String
) : IOException("Checksum mismatch ($algorithm): expected=$expected, actual=$actual")

/**
 * [Modified from HMCL FetchTask](https://github.com/HMCL-dev/HMCL/blob/59bcc7fe/HMCLCore/src/main/java/org/jackhuang/hmcl/task/FetchTask.java)
 *
 * Core of ordered candidate downloading: at most [DEFAULT_RETRY] retries within one source (200ms apart),
 * manual redirect following (20 hops max), Range resume (strict validation), and 10s connect/read timeouts;
 * 4xx means the source has nothing (no retry, straight to the next candidate); write corruption retries from scratch without consuming retries.
 */
object Fetcher {
    const val DEFAULT_RETRY = 5

    private const val TIMEOUT_MILLIS = 10_000
    private const val MAX_REDIRECTS = 20
    private const val RETRY_BACKOFF_MILLIS = 200L
    private const val BUFFER_SIZE = 32 * 1024

    /**
     * Downloads a file through candidates in order; throws [AllSourcesFailedException] once all are exhausted,
     * with every source in the message and the last failure kept in the cause chain.
     *
     * @param onBytes incremental callback of bytes successfully written (uncompressed terms)
     */
    suspend fun downloadFile(
        urls: List<String>,
        targetFile: File,
        sha1: String? = null,
        retry: Int = DEFAULT_RETRY,
        onBytes: (Long) -> Unit = {}
    ) {
        require(urls.isNotEmpty()) { "At least one URL is required" }
        require(retry > 0) { "Retry count must be greater than 0" }

        val failures = mutableListOf<DownloadException>()
        for (candidate in urls) {
            val url = try {
                URL(candidate)
            } catch (e: Exception) {
                failures += DownloadException(candidate, IOException("Invalid URL", e))
                continue
            }
            try {
                downloadCandidate(url, targetFile, sha1, retry, onBytes)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: DownloadException) {
                Logger.warning(TAG, "Candidate source failed: $candidate", e)
                failures += e
            }
        }

        val last = failures.removeAt(failures.lastIndex)
        failures.forEach { last.addSuppressed(it) }
        val detail = urls.joinToString(separator = "\n") { "- $it" }
        throw AllSourcesFailedException("All ${urls.size} candidate source(s) failed:\n$detail", last)
    }

    /** Cross-attempt state of one candidate source: the sink context and resume eligibility persist across retries */
    private class CandidateState {
        var sink: FileSink? = null
        var resume: ResumeContext? = null

        /** Discards the sink context: closes and deletes the temp file */
        fun discardSink() {
            val current = sink
            if (current != null) {
                runCatching { current.close() }
                sink = null
            }
        }
    }

    private enum class AttemptResult { DONE, RESTART_FREE }

    private suspend fun downloadCandidate(
        url: URL,
        targetFile: File,
        sha1: String?,
        retry: Int,
        onBytes: (Long) -> Unit
    ) {
        val state = CandidateState()
        val exceptions = mutableListOf<Exception>()
        var retryLimit = retry
        var retryTime = 0
        try {
            while (retryTime < retryLimit) {
                currentCoroutineContext().ensureActive()
                Logger.info(TAG, "Download started: $url")
                val attempt = retryTime
                retryTime++

                try {
                    val result = runInterruptible(Dispatchers.IO) {
                        attempt(url, targetFile, sha1, state, onBytes)
                    }
                    if (result == AttemptResult.DONE) return
                    //Resume invalidated or 416: restart from scratch without consuming a retry
                    retryLimit++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: HttpResultException) {
                    Logger.warning(TAG, "Download failed: $url", e)
                    if (e.code in 400..499) throw DownloadException(url.toString(), e)
                    exceptions += e
                    if (attempt < retryLimit - 1) delay(RETRY_BACKOFF_MILLIS.milliseconds)
                } catch (e: Exception) {
                    Logger.warning(TAG, "Download failed: $url", e)
                    exceptions += e
                    if (attempt < retryLimit - 1) delay(RETRY_BACKOFF_MILLIS.milliseconds)
                }
            }
        } finally {
            state.discardSink()
        }

        val last = exceptions.lastOrNull() ?: IOException("No exceptions")
        exceptions.dropLast(1).forEach { last.addSuppressed(it) }
        throw DownloadException(url.toString(), last)
    }

    /** One complete attempt: follow redirects manually, handle the resume handshake, stream to disk and commit. Runs blocking; cancellation relies on thread interruption */
    private fun attempt(
        url: URL,
        targetFile: File,
        sha1: String?,
        state: CandidateState,
        onBytes: (Long) -> Unit
    ): AttemptResult {
        var redirects: MutableList<URL>? = null

        val headers = linkedMapOf("accept-encoding" to "gzip")
        val resumeRequested = state.resume?.hasPartialContent() == true
        if (resumeRequested) {
            val resume = state.resume!!
            headers["range"] = "bytes=${resume.countUncompressed}-"
            headers["if-range"] = resume.ifRange()
        }

        var currentUri = url
        var connection: HttpURLConnection?
        var response: ResponseInfo?

        while (true) {
            val conn = openConnection(currentUri)
            var keep = false
            try {
                headers.forEach { (name, value) -> conn.setRequestProperty(name, value) }
                val code = conn.responseCode
                response = ResponseInfo.of(code, conn.url, conn)

                if (code in 300..308 && code != HttpURLConnection.HTTP_NOT_MODIFIED && code != 306) {
                    val seen = redirects ?: mutableListOf<URL>().also { redirects = it }
                    if (seen.size >= MAX_REDIRECTS) throw IOException("Too much redirects")

                    val location = conn.getHeaderField("Location")
                    if (location.isNullOrBlank()) throw IOException("Redirected to an empty location")

                    val target = currentUri.toURI().resolve(location).toURL()
                    seen.add(target)
                    if (!isHttpUri(target)) throw IOException("Redirected to non-HTTP URL: $target")
                    currentUri = target
                } else {
                    keep = true
                    connection = conn
                    break
                }
            } finally {
                if (!keep) disconnectQuietly(conn)
            }
        }

        val code = response.code

        if (resumeRequested && code == 416 /* HTTP_RANGE_NOT_SATISFIABLE */) {
            //The existing partial is no longer usable: discard the resume context and download from scratch
            state.resume = null
            state.discardSink()
            return AttemptResult.RESTART_FREE
        }
        if (code / 100 == 4) throw HttpResultException(code, "HTTP $code: $url")
        if (code / 100 != 2) throw HttpResultException(code, "HTTP $code: $url")

        val contentLength = response.headerAsLong("content-length")
        val contentEncoding = ContentEncoding.fromHeaders(response)

        if (state.sink == null) {
            state.sink = FileSink(targetFile, sha1)
            state.resume = ResumeContext.of(response)
        } else if (resumeRequested) {
            if (state.resume!!.canResume(code, response)) {
                Logger.info(TAG, "Resuming $url from ${state.resume!!.countUncompressed}")
            } else {
                //The server couldn't resume by range: discard what we have and start over
                state.resume = null
                state.discardSink()
                return AttemptResult.RESTART_FREE
            }
        } else {
            state.discardSink()
            state.sink = FileSink(targetFile, sha1)
            state.resume = ResumeContext.of(response)
        }

        var inputStream: InputStream? = null
        var bodyConsumed = false
        try {
            inputStream = connection.inputStream
            transfer(state.sink!!, state.resume, inputStream, contentLength, contentEncoding, onBytes)
            inputStream = null
            bodyConsumed = true
        } catch (e: Throwable) {
            if (state.sink?.broken == true) {
                //Write failure (disk error etc.): the whole context is invalidated and the retry starts from zero
                state.resume = null
                state.discardSink()
            }
            throw e
        } finally {
            runCatching { inputStream?.close() }
            if (!bodyConsumed) disconnectQuietly(connection)
        }

        try {
            state.sink!!.finish()
        } catch (e: Throwable) {
            state.resume = null
            state.discardSink()
            throw e
        }
        state.resume = null
        state.sink = null
        return AttemptResult.DONE
    }

    private fun transfer(
        sink: FileSink,
        resume: ResumeContext?,
        rawInput: InputStream,
        contentLength: Long,
        contentEncoding: ContentEncoding,
        onBytes: (Long) -> Unit
    ) {
        val counter = CountingInputStream(rawInput)
        contentEncoding.wrap(counter).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            var lastDownloaded = 0L
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Cancelled")
                val len = input.read(buffer)
                if (len == -1) break
                sink.write(buffer, 0, len)
                resume?.addBytes(len.toLong())
                onBytes(counter.downloaded - lastDownloaded)
                lastDownloaded = counter.downloaded
            }
            if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Cancelled")
            onBytes(counter.downloaded - lastDownloaded)

            if (contentLength >= 0 && counter.downloaded != contentLength) {
                throw IOException("Unexpected file size: ${counter.downloaded}, expected: $contentLength")
            }
        }
    }

    private fun openConnection(url: URL): HttpURLConnection {
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", URL_USER_AGENT)
        return connection
    }

    private fun isHttpUri(url: URL): Boolean =
        url.protocol.equals("http", ignoreCase = true) || url.protocol.equals("https", ignoreCase = true)

    private fun disconnectQuietly(connection: HttpURLConnection?) {
        if (connection == null) return
        runCatching { connection.errorStream?.close() }
        connection.disconnect()
    }

    private class CountingInputStream(input: InputStream) : InputStream() {
        private val delegate = input
        var downloaded = 0L
            private set

        override fun read(): Int {
            val value = delegate.read()
            if (value >= 0) downloaded++
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = delegate.read(buffer, offset, length)
            if (count >= 0) downloaded += count
            return count
        }

        override fun close() {
            delegate.close()
        }
    }
}
