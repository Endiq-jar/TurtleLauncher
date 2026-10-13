package com.endiq.turtlelauncher.renderer.angle

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.feature.pluginupdate.GithubRelease
import com.endiq.turtlelauncher.feature.update.UpdateUtils
import com.endiq.turtlelauncher.launch.AutoSettingsOptimizer
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.task.TaskExecutors
import com.endiq.turtlelauncher.task.TaskExecutors.Companion.runInUIThread
import com.endiq.turtlelauncher.utils.ZHTools
import com.endiq.turtlelauncher.utils.http.NetworkUtils
import com.endiq.turtlelauncher.utils.path.PathManager
import com.endiq.turtlelauncher.utils.path.UrlManager
import net.endiq.launcher.Tools
import net.endiq.launcher.utils.JREUtils
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

/**
 * Discovery, install/update and runtime state for MojoLauncher's ANGLE plugin
 * (`git.mojo.angle`) - see [AnglePlugin] for what the plugin is and why it is the preferred ANGLE
 * source for PowerVR devices.
 *
 * Three things live here, in order of how often they run:
 *
 * 1. **Finding a usable ANGLE pair** - the plugin's own `nativeLibraryDir` first, then the same files
 *    unpacked out of the plugin APK by the launcher (some devices/installs leave `nativeLibraryDir`
 *    empty until the plugin app is launched once, and a user who only installs the plugin never opens
 *    it), then the ANGLE copy bundled inside the launcher APK as a last resort. Either way the pair is
 *    pre-loaded by absolute path before the renderer loads: every later bare-name `dlopen` of
 *    `libEGL_angle.so` / `libGLESv2_angle.so` (the native EGL bridge, SDL's `SDL_EGL_LIBRARY`, ANGLE's
 *    own lookup of its GLES library, MobileGlues' `enableAngle` path) then resolves - by soname - to
 *    these exact files, so the game process cannot end up with two ANGLE instances.
 * 2. **Eligibility state** - whether this device is a PowerVR/Imagination one, and whether a previous
 *    launch already died because the platform driver produced no GL context. Both are persisted in
 *    `files/angle_plugin_state.json` because the game process (`:game`) runs the launch path while the
 *    launcher process (`:launcher`) shows the UI.
 * 3. **Keeping the plugin current** - the `rel` release is re-uploaded by CI, so the asset's
 *    size/updated-at is the version signal; a newer asset is offered for install through the normal
 *    package-installer flow ([UpdateUtils.installApk], same as the launcher's own self-update).
 */
object AnglePluginManager {
    private const val TAG = "AnglePlugin"

    /** A GL-context failure is remembered for this long - long enough that "relaunch the game now"
     *  picks the ANGLE path up, short enough that a one-off crash a month ago can't pin the launcher
     *  to ANGLE forever. */
    private const val GL_FAILURE_MEMORY_MS = 7L * 24 * 60 * 60 * 1000

    /** Don't re-offer the plugin install more than once per this window (the user may have declined). */
    private const val INSTALL_PROMPT_COOLDOWN_MS = 12L * 60 * 60 * 1000

    /** And don't even ask GitHub more often than this: the releases API is rate-limited to 60
     *  requests/hour per IP for unauthenticated clients, and this runs on every launch. */
    private const val RELEASE_CHECK_COOLDOWN_MS = 6L * 60 * 60 * 1000

    private val client by lazy {
        UrlManager.createOkHttpClientBuilder().build().newBuilder()
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val stateFile: File get() = File(PathManager.DIR_FILE, "angle_plugin_state.json")
    private val extractRoot: File get() = File(PathManager.DIR_FILE, "angle_plugin")

    // ── Runtime cache (per process) ──────────────────────────────────────────────────────────────

    private var cachedPluginInfo: InstalledPlugin? = null
    private var pluginInfoQueried = false
    private var cachedLibraries: AngleLibraries? = null
    private var librariesResolved = false
    private var cachedHostGpu: Boolean? = null

    /** A GL-context failure signature recorded by CrashAnalyzer, kept in memory so a failure that
     *  happened in this same process applies to the next launch without a disk round-trip. */
    @Volatile
    private var pendingGlFailureReason: String? = null

    /** Reset every per-process cache. Used after the plugin is installed/updated, and by tests. */
    fun clearCaches() {
        cachedPluginInfo = null
        pluginInfoQueried = false
        cachedLibraries = null
        librariesResolved = false
        cachedHostGpu = null
        preloadedLibraries = null
    }

    // ── State file ───────────────────────────────────────────────────────────────────────────────

    data class AngleState(
        /** Result of the (cached) PowerVR probe: null = never probed on this install. */
        val powerVrHost: Boolean? = null,
        val powerVrSignal: String? = null,
        /** Wall-clock time of the last launch that failed without a GL context, 0 = none. */
        val glContextFailureAt: Long = 0L,
        val glContextFailureReason: String? = null,
        val lastForcedLaunchAt: Long = 0L,
        val lastForcedLaunchVersion: String? = null,
        val lastForcedLaunchSource: String? = null,
        /** Fingerprint of the last `rel` asset we installed (or auto-offered), for update detection. */
        val knownAssetSize: Long = 0L,
        val knownAssetUpdatedAt: String? = null,
        val lastInstallPromptAt: Long = 0L,
        /** Last time the `rel` release was queried (see RELEASE_CHECK_COOLDOWN_MS). */
        val lastReleaseCheckAt: Long = 0L,
        /** Version code of the plugin package at the time we last recorded it, for logs only. */
        val installedVersionCode: Int = 0
    )

    private fun loadState(): AngleState = runCatching {
        if (!stateFile.exists()) return AngleState()
        Tools.GLOBAL_GSON.fromJson(stateFile.readText(), AngleState::class.java) ?: AngleState()
    }.getOrElse { e ->
        Logging.w(TAG, "Could not read ${stateFile.name} (${e.message}) - starting from a clean state")
        AngleState()
    }

    private fun saveState(state: AngleState) {
        runCatching { stateFile.writeText(Tools.GLOBAL_GSON.toJson(state)) }
            .onFailure { e -> Logging.e(TAG, "Could not persist ${stateFile.name}", e) }
    }

    private fun updateState(block: (AngleState) -> AngleState) {
        saveState(block(loadState()))
    }

    // ── Installed plugin ─────────────────────────────────────────────────────────────────────────

    data class InstalledPlugin(
        val packageName: String,
        val versionCode: Int,
        val versionName: String?,
        /** The plugin's own extracted native lib dir (`/data/app/.../lib/<abi>`); may be missing
         *  files until the plugin app has been launched once - see [ensureLibrariesOnDisk]. */
        val nativeLibraryDir: String?,
        /** Path of the installed APK itself, the fallback source for the .so files. */
        val sourceDir: String?
    )

    /**
     * The installed ANGLE plugin, or null when it isn't installed.
     *
     * Queried with GET_SHARED_LIBRARY_FILES (that is what fills in `nativeLibraryDir` for a
     * `hasCode=false` library-only APK like this plugin). The plugin package is declared in the
     * launcher manifest's `<queries>`, which is what makes this visible on Android 11+.
     */
    @Suppress("DEPRECATION")
    fun installedPlugin(context: Context): InstalledPlugin? {
        if (pluginInfoQueried) return cachedPluginInfo
        pluginInfoQueried = true
        cachedPluginInfo = runCatching {
            val info = context.packageManager.getPackageInfo(
                AnglePlugin.PACKAGE_NAME,
                PackageManager.GET_SHARED_LIBRARY_FILES
            ) ?: return@runCatching null
            InstalledPlugin(
                packageName = AnglePlugin.PACKAGE_NAME,
                versionCode = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode,
                versionName = info.versionName,
                nativeLibraryDir = info.applicationInfo?.nativeLibraryDir,
                sourceDir = info.applicationInfo?.sourceDir
            )
        }.getOrElse { e ->
            if (e !is PackageManager.NameNotFoundException) {
                Logging.w(TAG, "ANGLE plugin lookup failed: ${e.message}")
            }
            null
        }
        return cachedPluginInfo
    }

    fun isPluginInstalled(context: Context): Boolean = installedPlugin(context) != null

    /** All four ABIs the plugin publishes; the launcher's ABI is picked by the device itself. */
    private fun deviceAbi(): String = UpdateUtils.getArchModel() ?: "arm64-v8a"

    /**
     * Makes sure the plugin's two libraries exist as real files on disk and returns the pair.
     *
     * Fast path: the plugin's `nativeLibraryDir` already contains them (normal case once the plugin
     * app has been launched by the system at least once). Slow path: unpack the `lib/<abi>/*.so`
     * entries of the installed APK into the launcher's own files dir - an extractNativeLibs APK is
     * only guaranteed to be unpacked by the installer on older Android versions, so this keeps the
     * feature working on installs where that directory is empty. Extracted copies are keyed by the
     * plugin's version code + install time, so an updated plugin never reuses stale files.
     */
    fun ensureLibrariesOnDisk(context: Context): AngleLibraries? {
        val plugin = installedPlugin(context) ?: return null

        val nativeDir = plugin.nativeLibraryDir
        if (!nativeDir.isNullOrBlank()) {
            val egl = File(nativeDir, AnglePlugin.EGL_LIBRARY)
            val gles = File(nativeDir, AnglePlugin.GLES_LIBRARY)
            if (isUsableLibrary(egl) && isUsableLibrary(gles)) {
                return AngleLibraries(egl.absolutePath, gles.absolutePath, AngleSource.PLUGIN, plugin.versionCode)
            }
            Logging.i(
                TAG,
                "Plugin nativeLibraryDir has no usable ANGLE pair ($nativeDir) - unpacking it from the installed APK"
            )
        }

        val apkPath = plugin.sourceDir
        if (apkPath.isNullOrBlank() || !File(apkPath).isFile) {
            Logging.w(TAG, "ANGLE plugin is installed but neither its lib dir nor its APK is readable")
            return null
        }

        val abi = deviceAbi()
        val outDir = File(extractRoot, "${plugin.versionCode}-${File(apkPath).lastModified()}-$abi")
        val egl = File(outDir, AnglePlugin.EGL_LIBRARY)
        val gles = File(outDir, AnglePlugin.GLES_LIBRARY)
        if (isUsableLibrary(egl) && isUsableLibrary(gles)) {
            return AngleLibraries(egl.absolutePath, gles.absolutePath, AngleSource.PLUGIN, plugin.versionCode, extracted = true)
        }

        return runCatching {
            outDir.mkdirs()
            ZipFile(apkPath).use { zip ->
                AnglePlugin.LIBRARIES.forEach { name ->
                    val entry = zip.getEntry("lib/$abi/$name")
                        ?: zip.entries().asSequence().find { it.name == "lib/$abi/$name" }
                        ?: throw IOException("$abi/$name is not in the plugin APK")
                    val target = File(outDir, name)
                    val temp = File(outDir, "$name.part")
                    zip.getInputStream(entry).use { input ->
                        temp.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
                    }
                    if (!temp.renameTo(target)) {
                        FileUtils.copyFile(temp, target)
                        FileUtils.deleteQuietly(temp)
                    }
                }
            }
            pruneExtractions(outDir)
            if (!isUsableLibrary(egl) || !isUsableLibrary(gles)) {
                Logging.w(TAG, "Unpacked ANGLE libraries failed validation - ignoring them")
                return@runCatching null
            }
            Logging.i(TAG, "Unpacked the ANGLE plugin's $abi libraries into ${outDir.absolutePath}")
            AngleLibraries(egl.absolutePath, gles.absolutePath, AngleSource.PLUGIN, plugin.versionCode, extracted = true)
        }.getOrElse { e ->
            Logging.e(TAG, "Could not unpack the ANGLE plugin's libraries from $apkPath", e)
            null
        }
    }

    /** Removes extraction folders left over from previous plugin versions. */
    private fun pruneExtractions(keep: File) {
        runCatching {
            extractRoot.listFiles()?.forEach { dir ->
                if (dir.isDirectory && dir.absolutePath != keep.absolutePath) FileUtils.deleteQuietly(dir)
            }
        }
    }

    /** ELF sanity check: catches a truncated copy, an ":<abi>" wrapper file or an HTML error page
     *  saved under a .so name - any of which would otherwise fail much later, inside dlopen. */
    private fun isUsableLibrary(file: File): Boolean {
        if (!file.isFile || file.length() < 64 * 1024) return false
        return runCatching {
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                if (input.read(magic) != 4) return false
                magic[0] == 0x7F.toByte() && magic[1] == 'E'.code.toByte() &&
                    magic[2] == 'L'.code.toByte() && magic[3] == 'F'.code.toByte()
            }
        }.getOrDefault(false)
    }

    /** The ANGLE pair bundled inside the launcher APK, if this build ships one. */
    fun bundledLibraries(): AngleLibraries? {
        val egl = File(PathManager.DIR_NATIVE_LIB, AnglePlugin.EGL_LIBRARY)
        val gles = File(PathManager.DIR_NATIVE_LIB, AnglePlugin.GLES_LIBRARY)
        if (!isUsableLibrary(egl) || !isUsableLibrary(gles)) return null
        return AngleLibraries(egl.absolutePath, gles.absolutePath, AngleSource.BUNDLED)
    }

    /**
     * The ANGLE pair this device should use: the plugin when it is installed and usable, otherwise the
     * bundled copy. Resolved once per process - it may unpack the plugin's libraries on the first call,
     * which is why the launch path is the only hot caller.
     */
    fun resolveLibraries(context: Context): AngleLibraries? {
        if (librariesResolved) return cachedLibraries
        val resolved = ensureLibrariesOnDisk(context) ?: bundledLibraries()
        cachedLibraries = resolved
        librariesResolved = true
        if (resolved != null) {
            Logging.i(TAG, "ANGLE pair selected: source=${resolved.source} egl=${resolved.eglPath} gles=${resolved.glesPath}")
        } else {
            Logging.w(TAG, "No ANGLE libraries available (plugin not installed and none bundled for this ABI)")
        }
        return resolved
    }

    /**
     * Where ANGLE would come from *without* touching the disk - for UI/AI status text, which must not
     * unpack an APK on the main thread. Says "unpacked on the next launch" when the plugin is installed
     * but its files still have to be extracted.
     */
    fun sourceLabelCheap(context: Context): String {
        val plugin = installedPlugin(context)
        if (plugin != null) {
            val dir = plugin.nativeLibraryDir
            val readyOnDisk = !dir.isNullOrBlank() &&
                isUsableLibrary(File(dir, AnglePlugin.EGL_LIBRARY)) &&
                isUsableLibrary(File(dir, AnglePlugin.GLES_LIBRARY))
            return if (readyOnDisk) "ANGLE plugin (v${plugin.versionCode})"
            else "ANGLE plugin (v${plugin.versionCode}, unpacked on the next launch)"
        }
        return if (bundledLibraries() != null) "built into the launcher" else "unavailable"
    }

    // ── Pre-loading ──────────────────────────────────────────────────────────────────────────────

    private var preloadedLibraries: AngleLibraries? = null

    /**
     * dlopen()s the pair, GLES first, before anything else in the launch touches EGL/GLES.
     *
     * Order matters twice over: ANGLE's `libEGL_angle.so` internally opens `libGLESv2_angle.so` by
     * name (the bare string is in the binary), and every consumer that asks for either library by
     * bare name later gets an already-loaded library back - so the GLES copy must be the one that's
     * registered first, or ANGLE could pair its EGL with the *bundled* GLES of a different version.
     *
     * Both paths are absolute, and identical to what the launch then advertises via
     * [AngleLibraries], so no consumer can end up with a second copy of the same file.
     */
    fun preload(libraries: AngleLibraries): Boolean {
        if (preloadedLibraries == libraries) return true
        // JREUtils.dlopen is libpojavexec's own dlopen (RTLD_GLOBAL | RTLD_LAZY), i.e. the same
        // loader call the launcher already uses to pull in renderer/plugin libraries - including
        // absolute paths inside another app's folder, which is the case for the plugin's own pair.
        val glesOk = runCatching { JREUtils.dlopen(libraries.glesPath) }.getOrDefault(false)
        if (!glesOk) {
            Logging.w(TAG, "dlopen failed for ${libraries.glesPath} - falling back to the other ANGLE source")
            return false
        }
        val eglOk = runCatching { JREUtils.dlopen(libraries.eglPath) }.getOrDefault(false)
        if (!eglOk) {
            Logging.w(TAG, "dlopen failed for ${libraries.eglPath} - falling back to the other ANGLE source")
            return false
        }
        preloadedLibraries = libraries
        Logging.i(TAG, "Preloaded ANGLE (${libraries.source}): ${libraries.glesName} + ${libraries.eglName}")
        return true
    }

    /** Pre-loads [primary] and silently retries with [fallback] - the pattern the launch path uses
     *  so a plugin that is installed but unusable degrades to the bundled ANGLE instead of failing. */
    fun preloadWithFallback(primary: AngleLibraries?, fallback: AngleLibraries?): AngleLibraries? {
        primary?.let { if (preload(it)) return it }
        if (fallback != null && fallback != primary && preload(fallback)) {
            Logging.w(TAG, "Falling back to the bundled ANGLE libraries (source=${fallback.source})")
            return fallback
        }
        return null
    }

    // ── Device eligibility ───────────────────────────────────────────────────────────────────────

    /** GL renderer/vendor strings that identify a PowerVR (Imagination) GPU family. */
    private val powerVrTokens = listOf(
        "powervr", "power vr", "imagination", "img gpu", "imgtech", "img ",
        "rogue", "sgx", "ge8320", "ge9215", "gm9446", "series6", "series7", "bxm", "bxm-"
    )

    /**
     * Whether this device runs a PowerVR/Imagination GPU - the family where the platform GLES driver
     * is what breaks for Minecraft 26.x (hangs, GL_INVALID_ENUM CubeMap crashes, no context for SDL3).
     *
     * Cached in memory for the process and persisted to the state file, so the `:game` process reads
     * the same answer the UI computed instead of running its own EGL probe mid-launch.
     */
    fun isPowerVrHost(context: Context): Boolean {
        cachedHostGpu?.let { return it }
        loadState().let { state ->
            if (state.powerVrHost != null) {
                cachedHostGpu = state.powerVrHost
                return state.powerVrHost
            }
        }

        val signal = runCatching { AutoSettingsOptimizer.detectGpu() }.getOrElse { e ->
            Logging.w(TAG, "GPU probe failed (${e.message}) - using Build.HARDWARE only")
            Build.HARDWARE
        }
        val haystack = "$signal ${Build.HARDWARE} ${Build.BOARD} ${System.getProperty("ro.hardware.egl") ?: ""}"
            .lowercase()
        val isPowerVr = powerVrTokens.any { haystack.contains(it) }
        Logging.i(TAG, "PowerVR probe: '$signal' -> powerVr=$isPowerVr")
        cachedHostGpu = isPowerVr
        updateState { it.copy(powerVrHost = isPowerVr, powerVrSignal = signal) }
        return isPowerVr
    }

    /** True when a recent launch already failed on this device without ever getting a GL context -
     *  the second, device-independent reason to route the launch through ANGLE. */
    fun hasRecentGlContextFailure(): Boolean {
        pendingGlFailureReason?.let { return true }
        val state = loadState()
        if (state.glContextFailureAt <= 0L) return false
        val age = ZHTools.getCurrentTimeMillis() - state.glContextFailureAt
        if (age > GL_FAILURE_MEMORY_MS) return false
        return true
    }

    fun glContextFailureReason(): String? = pendingGlFailureReason ?: loadState().glContextFailureReason

    /**
     * Records "this launch died because the platform GPU driver gave the renderer no usable GL
     * context". Called from CrashAnalyzer for the crash families that mean exactly that, and by the
     * crash screen's "Force ANGLE" repair action.
     */
    fun noteGlContextFailure(reason: String) {
        if (pendingGlFailureReason == reason) return
        pendingGlFailureReason = reason
        Logging.w(TAG, "Recording a GL-context failure for this device: $reason")
        updateState {
            it.copy(
                glContextFailureAt = ZHTools.getCurrentTimeMillis(),
                glContextFailureReason = reason
            )
        }
    }

    /** A launch that reached the game and exited cleanly proves the driver/ANGLE setup works, so the
     *  failure memory is dropped (it exists only to get the *next* launch over a known-bad path). */
    fun noteCleanGameExit() {
        if (pendingGlFailureReason == null && loadState().glContextFailureAt <= 0L) return
        pendingGlFailureReason = null
        updateState { it.copy(glContextFailureAt = 0L, glContextFailureReason = null) }
        Logging.i(TAG, "Game exited cleanly - clearing the remembered GL-context failure")
    }

    fun noteForcedLaunch(versionName: String?, libraries: AngleLibraries) {
        updateState {
            it.copy(
                lastForcedLaunchAt = ZHTools.getCurrentTimeMillis(),
                lastForcedLaunchVersion = versionName,
                lastForcedLaunchSource = libraries.source.name
            )
        }
    }

    /** True when this device should use ANGLE automatically (the user-facing hook), i.e. Settings →
     *  Game → "Force ANGLE on PowerVR" is on and the device qualifies. */
    fun isAutoForceWanted(context: Context): Boolean {
        if (!AllSettings.angleForceOnPowerVr.getValue()) return false
        if (isPowerVrHost(context)) return true
        return hasRecentGlContextFailure()
    }

    /**
     * One-line status for the settings UI, the AI answers and log banners. Deliberately uses the cheap
     * [sourceLabelCheap] rather than a full [resolveLibraries] (which can unpack the plugin APK).
     */
    fun describe(context: Context): String = buildString {
        append(if (installedPlugin(context) != null) "plugin installed" else "plugin not installed")
        append("; ANGLE from ").append(sourceLabelCheap(context))
        if (isPowerVrHost(context)) append("; PowerVR device")
        if (hasRecentGlContextFailure()) {
            append("; last GL-context failure: ").append(glContextFailureReason() ?: "recorded")
        }
    }

    // ── Install / update of the plugin APK ───────────────────────────────────────────────────────

    data class AssetInfo(val size: Long, val updatedAt: String?, val downloadUrl: String)

    /** Fetches the blessed `rel` release's APK metadata. Blocking - call from a background thread. */
    fun fetchReleaseAsset(): AssetInfo {
        val request = UrlManager.createRequestBuilder(AnglePlugin.RELEASE_API_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body?.string() ?: throw IOException("Empty release response")
            val release = Tools.GLOBAL_GSON.fromJson(body, GithubRelease::class.java)
                ?: throw IOException("Malformed release JSON")
            val asset = release.assets.firstOrNull { it.name.equals(AnglePlugin.ASSET_NAME, ignoreCase = true) }
                ?: throw IOException("${AnglePlugin.ASSET_NAME} is not in the ${AnglePlugin.RELEASE_TAG} release")
            return AssetInfo(asset.size, asset.updatedAt, asset.downloadUrl)
        }
    }

    /**
     * True when the installed copy is not the one the release currently offers.
     *
     * The signal is the asset's size + updated-at, exactly like PluginUpdateManager does for the
     * renderer/driver plugins: Mojo's CI re-uploads the same `rel` tag, so those two change on every
     * build, while the APK's own version code can only be compared *after* an install has completed
     * (which is why it is not the trigger - it would re-prompt forever after a declined install).
     */
    fun isUpdateAvailable(context: Context, asset: AssetInfo): Boolean {
        if (installedPlugin(context) == null) return false
        val state = loadState()
        if (state.knownAssetSize == 0L) return true
        return state.knownAssetSize != asset.size || state.knownAssetUpdatedAt != asset.updatedAt
    }

    /**
     * Downloads the release APK and hands it to the package installer.
     *
     * @param onComplete called on the UI thread; `success` means the APK was downloaded and the
     *                    install prompt was shown (Android still asks the user to confirm).
     */
    fun downloadAndInstall(context: Context, onComplete: (Boolean, String) -> Unit) {
        TaskExecutors.getDefault().execute {
            val tempFile = File(PathManager.DIR_APP_CACHE, "angle_plugin.apk")
            try {
                val asset = fetchReleaseAsset()
                val request = UrlManager.createRequestBuilder(asset.downloadUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty download")
                    tempFile.outputStream().use { out -> body.byteStream().copyTo(out) }
                }
                if (tempFile.length() < 1_000_000L) throw IOException("Downloaded file is too small to be the plugin")
                updateState {
                    it.copy(
                        knownAssetSize = asset.size,
                        knownAssetUpdatedAt = asset.updatedAt,
                        lastInstallPromptAt = ZHTools.getCurrentTimeMillis()
                    )
                }
                Logging.i(TAG, "ANGLE plugin APK downloaded (${tempFile.length()} bytes) - showing the install prompt")
                updateState { it.copy(installedVersionCode = installedPlugin(context)?.versionCode ?: 0) }
                // The user may install it right after this prompt: drop the process caches so the next
                // lookup (and the next launch) reads the freshly installed package and its files.
                clearCaches()
                UpdateUtils.installApk(context, tempFile)
                runInUIThread { onComplete(true, "ANGLE plugin downloaded - confirm the install prompt") }
            } catch (e: Exception) {
                Logging.e(TAG, "Failed to download/install the ANGLE plugin", e)
                runInUIThread { onComplete(false, e.message ?: "Unknown error") }
                FileUtils.deleteQuietly(tempFile)
            }
        }
    }

    /**
     * The launch-path half of the feature: on an eligible device, if the plugin is missing or older
     * than the release, fetch and install it - once per [INSTALL_PROMPT_COOLDOWN_MS] so a user who
     * declines is not asked again on every launch.
     *
     * Never blocks the launch: it runs on a background thread and the install prompt is a system
     * dialog. Until the user accepts it, the launch still runs on the bundled ANGLE copy (see
     * [resolveLibraries]'s fallback), so nothing is gated on this succeeding.
     */
    fun maybeInstallOrUpdate(context: Context, onMessage: (String) -> Unit = {}) {
        if (!AllSettings.angleAutoInstall.getValue()) return
        if (!isAutoForceWanted(context)) return
        TaskExecutors.getDefault().execute {
            runCatching {
                val plugin = installedPlugin(context)
                val state = loadState()
                val now = ZHTools.getCurrentTimeMillis()

                // Only touch GitHub once per RELEASE_CHECK_COOLDOWN_MS (this runs on every launch),
                // and only offer an install the user has not already declined recently.
                if (now - state.lastReleaseCheckAt < RELEASE_CHECK_COOLDOWN_MS) return@runCatching
                updateState { it.copy(lastReleaseCheckAt = now) }

                val asset = fetchReleaseAsset()
                val needed = plugin == null || isUpdateAvailable(context, asset)
                if (!needed) {
                    Logging.i(TAG, "ANGLE plugin v${plugin?.versionCode} is current (asset ${asset.size} bytes, updated ${asset.updatedAt})")
                    return@runCatching
                }
                if (now - state.lastInstallPromptAt < INSTALL_PROMPT_COOLDOWN_MS) {
                    Logging.i(TAG, "ANGLE plugin install was offered recently - not asking again yet")
                    return@runCatching
                }

                Logging.i(
                    TAG,
                    (if (plugin == null) "ANGLE plugin is not installed" else "ANGLE plugin update available (installed v${plugin.versionCode})") +
                        " - offering the release APK (${asset.size} bytes, updated ${asset.updatedAt})"
                )
                downloadAndInstall(context) { _, message -> onMessage(message) }
            }.onFailure { e ->
                // Offline, rate-limited, or GitHub unreachable - all expected on a phone.
                Logging.i(TAG, "ANGLE plugin check skipped: ${e.message}")
            }
        }
    }

    /** Called from the settings screen: remember that the user asked for the install on purpose. */
    fun openReleasePage(context: Context) {
        ZHTools.openLink(context, AnglePlugin.RELEASE_PAGE_URL)
    }

    fun isNetworkAvailable(context: Context): Boolean = NetworkUtils.isNetworkAvailable(context)
}
