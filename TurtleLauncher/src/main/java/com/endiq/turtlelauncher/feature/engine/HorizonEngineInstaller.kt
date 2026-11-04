package com.endiq.turtlelauncher.feature.engine

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.task.TaskExecutors
import com.endiq.turtlelauncher.utils.path.UrlManager
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * The built-in Horizon engine installer.
 *
 * Horizon itself is a native Rust/Vulkan application, so it cannot be compiled into this APK as a
 * library - but it *is* shipped inside this APK: the build packs the Android port's APK into
 * `assets/horizon/`, so choosing the engine in Phone settings never downloads anything. The
 * release-feed path below stays as the fallback for builds where that asset was stripped:
 *
 *  - the latest Android build is resolved from a GitHub release feed (configurable in Settings,
 *    defaulting to the multi-ABI Horizon Android port);
 *  - the APK matching this device's ABI is selected, downloaded with progress, and verified
 *    against the release's `SHA256SUMS.txt` when the feed publishes one;
 *  - the installed package id is read from the APK and remembered, so the engine is recognised
 *    immediately even when a build uses a package name this launcher has never seen;
 *  - the download is handed to the system installer (user consent stays with Android - the
 *    launcher never installs anything silently).
 *
 * Nothing here runs on the main thread except the final `startActivity`.
 */
object HorizonEngineInstaller {
    private const val TAG = "HorizonInstaller"

    private const val API_BASE = "https://api.github.com"
    private const val USER_AGENT = "TurtleLauncher-HorizonEngineInstaller"

    /** Multi-ABI feed with published SHA-256 sums; override in Phone settings if you prefer. */
    const val DEFAULT_SOURCE: String = "Vasysik/horizon-android"

    private const val CACHE_DIR = "horizon-engine"
    private const val SUMS_ASSET = "SHA256SUMS.txt"

    /**
     * The engine APKs packed into this launcher at build time (`bundleHorizonEngine` in
     * build.gradle.kts): one per ABI, under assets/horizon/. Because they travel inside the
     * launcher's own signed APK there is no download, no size check and no network permission
     * involved in switching engines, and the device always has a build that matches its CPU.
     */
    const val BUNDLED_ASSET_DIR: String = "horizon"

    /** Written by the build next to the APKs; the source of truth for the bundled version. */
    const val VERSION_ASSET: String = "horizon/version"

    /** Horizon Android port packed into the APK; fallback when the version asset is missing. */
    const val BUNDLED_VERSION: String = "0.7.7"

    /** Build-time cache directories are named per version so an upgrade never reuses an old one. */
    private const val CACHE_VERSION_PREFIX = "v"

    /** Every Horizon Android ABI build the launcher can carry, in device-preference order. */
    private val BUNDLED_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

    /** One resolved release asset plus what is known about it. */
    data class EngineRelease(
        val repo: String,
        val tag: String,
        val versionName: String,
        val assetName: String,
        val downloadUrl: String,
        val sizeBytes: Long,
        val sha256: String?,
    )

    class InstallException(message: String) : Exception(message)

    // ── Source configuration ───────────────────────────────────────────────────────

    fun sourceRepo(): String =
        AllSettings.horizonEngineSource.getValue().trim()
            .ifBlank { DEFAULT_SOURCE }
            .removePrefix("https://github.com/")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')

    fun setSourceRepo(repo: String) {
        AllSettings.horizonEngineSource.put(repo.trim()).save()
    }

    fun isSourceConfigured(): Boolean = sourceRepo().contains('/')

    // ── Release resolution ─────────────────────────────────────────────────────────

    /** Latest release for [repo], with the APK that matches this device picked out. */
    fun latestRelease(repo: String = sourceRepo()): Result<EngineRelease> = runCatching {
        if (!repo.contains('/')) throw InstallException("Engine source must look like owner/repository.")
        val release = latestJson(repo)
        val tag = release.string("tag_name") ?: release.string("name").orEmpty()
        val assets = release.array("assets") ?: JsonArray()
        val asset = pickAsset(assets) ?: throw InstallException(
            "The latest $repo release publishes no APK for this device."
        )
        EngineRelease(
            repo = repo,
            tag = tag,
            versionName = tag.removePrefix("v").removePrefix("android-"),
            assetName = asset.string("name").orEmpty(),
            downloadUrl = asset.string("browser_download_url")
                ?: throw InstallException("Release asset has no download URL."),
            sizeBytes = asset.string("size")?.toLongOrNull() ?: asset.long("size") ?: 0L,
            sha256 = sha256For(assets, asset.string("name").orEmpty()),
        )
    }

    private fun latestJson(repo: String): JsonObject {
        val latest = runCatching {
            JsonParser.parseString(get("$API_BASE/repos/$repo/releases/latest")).asJsonObject
        }.getOrNull()
        if (latest != null && latest.string("tag_name") != null) return latest

        // "latest" is unset when every release is a pre-release; fall back to the newest one.
        val list = runCatching {
            JsonParser.parseString(get("$API_BASE/repos/$repo/releases?per_page=5")).asJsonArray
        }.getOrNull()
        val newest = list?.firstOrNull { it.isJsonObject }?.asJsonObject
            ?: throw InstallException("No release found for $repo.")
        return newest
    }

    /** Prefers this device's ABI, then a universal build, then any APK. */
    private fun pickAsset(assets: JsonArray): JsonObject? {
        val apks = assets.filter { it.isJsonObject }
            .map { it.asJsonObject }
            .filter { it.string("name")?.endsWith(".apk", ignoreCase = true) == true }
        if (apks.isEmpty()) return null

        val abis = Build.SUPPORTED_ABIS.orEmpty().map { it.lowercase() }
        // arm64-v8a matches "arm64-v8a" and "arm64"; armeabi-v7a matches "armeabi-v7a"/"arm".
        val tokens = abis.flatMap { abi ->
            when (abi) {
                "arm64-v8a" -> listOf("arm64-v8a", "arm64")
                "armeabi-v7a" -> listOf("armeabi-v7a", "armv7", "arm")
                else -> listOf(abi)
            }
        }.distinct()

        for (token in tokens) {
            apks.firstOrNull { it.string("name").orEmpty().lowercase().contains(token) }?.let { return it }
        }
        apks.firstOrNull { it.string("name").orEmpty().contains("universal", ignoreCase = true) }
            ?.let { return it }
        // A single APK with no ABI in its name is usually either universal or device-agnostic.
        return apks.firstOrNull { apk ->
            val name = apk.string("name").orEmpty().lowercase()
            tokens.none { name.contains(it) }
        } ?: apks.first()
    }

    /** Reads `SHA256SUMS.txt` from the same release, when present, so downloads are verifiable. */
    private fun sha256For(assets: JsonArray, assetName: String): String? = runCatching {
        val sumsUrl = assets.filter { it.isJsonObject }
            .map { it.asJsonObject }
            .firstOrNull { it.string("name").equals(SUMS_ASSET, ignoreCase = true) }
            ?.string("browser_download_url")
            ?: return null
        val sums = get(sumsUrl)
        sums.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"), limit = 2)
                if (parts.size == 2) parts[0].lowercase() to parts[1].trim().removePrefix("*") else null
            }
            .firstOrNull { (_, name) -> name == assetName }
            ?.first
    }.onFailure { Logging.w(TAG, "Could not read checksums", it) }.getOrNull()

    // ── Download + install ─────────────────────────────────────────────────────────

    /**
     * Resolves the newest release and downloads its APK. Blocking - call from a worker thread.
     * [onProgress] receives 0..100.
     */
    fun downloadLatest(context: Context, onProgress: (Int) -> Unit): Result<Pair<EngineRelease, File>> =
        latestRelease().mapCatching { release ->
            release to download(context, release, onProgress)
        }

    // ── Built-in engine ───────────────────────────────────────────────────────────

    fun bundledAssetName(abi: String): String = "$BUNDLED_ASSET_DIR/horizon-engine-$abi.apk"

    /** Version of the engine packed in this APK (written by the build next to the APKs). */
    fun bundledVersion(context: Context): String = runCatching {
        context.assets.open(VERSION_ASSET).bufferedReader().use { it.readText().trim() }
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: BUNDLED_VERSION

    private fun hasBundledAsset(context: Context, abi: String): Boolean =
        runCatching { context.assets.open(bundledAssetName(abi)).close() }.isSuccess

    /** Every ABI this launcher actually ships an engine for. */
    fun bundledAbis(context: Context): List<String> =
        BUNDLED_ABIS.filter { abi -> hasBundledAsset(context, abi) }

    /**
     * The bundled engine build this device should install, or null when the launcher carries none.
     * The launcher is a universal APK, so this is what turns "the engine is in here somewhere"
     * into "the right engine for this phone".
     */
    fun bundledAbi(context: Context): String? =
        pickAbi(Build.SUPPORTED_ABIS.orEmpty().toList(), bundledAbis(context))

    /**
     * Pure ABI selection so the rule can be unit-tested: the first of the device's own ABIs that
     * this launcher ships, understanding the aliases Android reports ("arm64" for "arm64-v8a").
     *
     * No guessing beyond that: an engine built for another CPU would only fail to install, so a
     * device with no matching build is told to use the download instead.
     */
    fun pickAbi(deviceAbis: List<String>, availableAbis: List<String>): String? {
        if (availableAbis.isEmpty()) return null
        val available = availableAbis.map { it.lowercase() }.toSet()
        for (abi in deviceAbis) {
            val normalized = when (abi.lowercase()) {
                "arm64" -> "arm64-v8a"
                "arm", "armv7", "armeabi" -> "armeabi-v7a"
                else -> abi.lowercase()
            }
            if (normalized in available) return normalized
        }
        return null
    }

    /**
     * True when this launcher carries an engine *for this device*, i.e. switching engines needs no
     * download. [bundledAbis] reports everything the APK carries, which is what the version label
     * and the packaging checks use.
     */
    fun isBundled(context: Context): Boolean = bundledAbi(context) != null

    /**
     * Copies the bundled engine for this device out of the launcher's assets into the cache and
     * returns it, ready for [install]. Blocking - call from a worker thread.
     *
     * The cache is keyed by version and ABI: a launcher update that carries a newer engine
     * re-extracts instead of reusing the old file, while re-installing the same version reuses it.
     */
    fun extractBundled(context: Context, onProgress: (Int) -> Unit = {}): File {
        val abi = bundledAbi(context)
            ?: throw InstallException("This launcher build does not contain the Horizon engine.")
        val version = bundledVersion(context)
        val asset = bundledAssetName(abi)

        val root = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val currentDirName = "$CACHE_VERSION_PREFIX$version"
        // Older engines (and stray downloads) are deleted, so only the shipped version lives here.
        root.listFiles()?.forEach { stale ->
            if (stale.name != currentDirName) runCatching { stale.deleteRecursively() }
        }
        val directory = File(root, currentDirName).apply { mkdirs() }
        val target = File(directory, "horizon-engine-$abi.apk")
        val stamp = File(directory, "ready")
        if (target.isFile && target.length() > 0L && stamp.isFile) {
            onProgress(100)
            return target
        }

        val partial = File(directory, "horizon-engine-$abi.apk.part").apply { delete() }
        onProgress(0)
        val total = runCatching { context.assets.open(asset).use { it.available().toLong() } }
            .getOrDefault(0L)
        runCatching {
            context.assets.open(asset).use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val percent = ((written * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        }.onFailure { error ->
            partial.delete()
            throw InstallException(
                "The built-in Horizon engine could not be unpacked (${error.message ?: "unknown error"})."
            )
        }

        if (partial.length() <= 0L) {
            partial.delete()
            throw InstallException("This launcher build does not contain a Horizon engine for $abi.")
        }
        stamp.delete()
        if (!partial.renameTo(target)) {
            target.writeBytes(partial.readBytes())
            partial.delete()
        }
        stamp.writeText("$version $abi")
        onProgress(100)
        Logging.i(TAG, "Unpacked the bundled Horizon engine v$version ($abi)")
        return target
    }

    /**
     * Prepares the engine without any user action: when this launcher carries a build for this
     * device and it is not unpacked yet, it is unpacked in the background at start-up. That is what
     * makes the engine "already installed" from the user's point of view - selecting it never waits
     * on a 35 MB copy out of assets. Failures are silent: the UI can still install on demand.
     */
    @JvmStatic
    fun prepareInBackground(context: Context) {
        if (isPrepared(context)) return
        if (bundledAbi(context) == null) return
        val app = context.applicationContext
        TaskExecutors.getDefault().execute {
            runCatching { extractBundled(app) }
                .onFailure { error -> Logging.w(TAG, "Could not prepare the bundled engine", error) }
        }
    }

    /** True once [extractBundled] produced this version's APK for this device. */
    fun isPrepared(context: Context): Boolean {
        val abi = bundledAbi(context) ?: return false
        return runCatching {
            val directory = File(File(context.cacheDir, CACHE_DIR), "$CACHE_VERSION_PREFIX${bundledVersion(context)}")
            val apk = File(directory, "horizon-engine-$abi.apk")
            val stamp = File(directory, "ready")
            apk.isFile && apk.length() > 0L && stamp.isFile
        }.getOrDefault(false)
    }

    /**
     * The APK a settings action should install: the engine packed in this launcher for this
     * device when present, otherwise a download from the configured feed (stripped builds).
     */
    fun acquire(context: Context, onProgress: (Int) -> Unit = {}): Result<File> = runCatching {
        val abi = bundledAbi(context)
        if (abi != null) {
            extractBundled(context, onProgress)
        } else {
            // Either nothing is bundled, or the bundled builds are for other CPUs.
            downloadLatest(context, onProgress).getOrThrow().second
        }
    }

    /**
     * Numeric version comparison for the bundled/installed engine versions, so "always the latest"
     * can be checked without a semver dependency: "0.7.10" > "0.7.9", "0.7.6-rc1" == "0.7.6".
     */
    fun compareVersions(left: String?, right: String?): Int {
        fun parts(value: String?): List<Int> = value.orEmpty().trim()
            .removePrefix("v")
            // A pre-release suffix ("0.7.6-rc1") counts as that release, not as a later one.
            .substringBefore('-')
            .substringBefore('+')
            .split(Regex("[^0-9]+"))
            .filter { it.isNotEmpty() }
            .map { it.toIntOrNull() ?: 0 }
        val a = parts(left)
        val b = parts(right)
        for (index in 0 until maxOf(a.size, b.size)) {
            val difference = (a.getOrNull(index) ?: 0) - (b.getOrNull(index) ?: 0)
            if (difference != 0) return if (difference > 0) 1 else -1
        }
        return 0
    }

    /** Human-readable description of what this launcher carries, e.g. "v0.7.7, arm64-v8a". */
    fun bundledLabel(context: Context): String? {
        val abi = bundledAbi(context) ?: return null
        return "v${bundledVersion(context)}, $abi"
    }

    fun download(context: Context, release: EngineRelease, onProgress: (Int) -> Unit): File {
        // Downloads live next to, not inside, the extracted built-in engines.
        val directory = File(context.cacheDir, "$CACHE_DIR/download").apply { mkdirs() }
        // Drop anything the previous download left behind (old versions, partial files).
        directory.listFiles()?.forEach { stale ->
            if (stale.name != release.assetName) runCatching { stale.deleteRecursively() }
        }

        val target = File(directory, release.assetName)
        val partial = File(directory, "${release.assetName}.part")
        partial.delete()

        onProgress(0)
        val request = Request.Builder()
            .url(release.downloadUrl)
            .header("User-Agent", USER_AGENT)
            .build()

        val response = UrlManager.createOkHttpClient().newCall(request).execute()
        response.use { result ->
            if (!result.isSuccessful) {
                throw InstallException("Engine download failed (HTTP ${result.code}).")
            }
            val body = result.body ?: throw InstallException("Engine download returned no data.")
            val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes
            body.byteStream().use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val percent = ((written * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        }

        if (release.sizeBytes > 0 && partial.length() != release.sizeBytes) {
            val written = partial.length()
            partial.delete()
            throw InstallException(
                "Engine download was incomplete ($written of ${release.sizeBytes} bytes)."
            )
        }
        val digest = sha256(partial)
        release.sha256?.takeIf { it.isNotBlank() }?.let { expected ->
            if (!digest.equals(expected, ignoreCase = true)) {
                partial.delete()
                throw InstallException("Engine download failed its SHA-256 check.")
            }
        }

        partial.delete()
        if (!partial.renameTo(target)) {
            target.writeBytes(partial.readBytes())
            partial.delete()
        }
        onProgress(100)
        Logging.i(TAG, "Downloaded ${release.assetName} (${release.versionName}) from ${release.repo}")
        return target
    }

    /**
     * Hands a downloaded APK to the system installer and remembers which package it contains, so
     * the engine is detected in the very next session even if its id is not one we guess.
     */
    fun install(context: Context, apk: File): Result<String> = runCatching {
        if (!apk.isFile) throw InstallException("The engine file is gone - install it again.")
        val packageName = packageNameOf(context, apk)
        context.startActivity(installIntent(context, apk))
        if (!packageName.isNullOrBlank()) {
            GameEngineManager.selectHorizonPackage(packageName)
        }
        packageName.orEmpty()
    }.onFailure { Logging.w(TAG, "Could not start the engine installer", it) }

    fun installIntent(context: Context, apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".provider", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, HorizonEngine.APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Package id inside a downloaded APK, read without installing anything. */
    fun packageNameOf(context: Context, apk: File): String? =
        runCatching {
            context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)?.packageName
        }.getOrNull()

    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:$packageName"))

    fun clearCache(context: Context) {
        runCatching { File(context.cacheDir, CACHE_DIR).deleteRecursively() }
    }

    // ── Plumbing ───────────────────────────────────────────────────────────────────

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .build()
        UrlManager.createOkHttpClient().newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw InstallException(
                    when (response.code) {
                        404 -> "No Horizon release found at $url"
                        403, 429 -> "GitHub is rate limiting the engine download - try again later."
                        else -> "Engine feed returned HTTP ${response.code}."
                    }
                )
            }
            return body
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asString }.getOrNull() }

    private fun JsonObject.long(key: String): Long? =
        get(key)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asLong }.getOrNull() }

    private fun JsonObject.array(key: String): JsonArray? =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray
}
