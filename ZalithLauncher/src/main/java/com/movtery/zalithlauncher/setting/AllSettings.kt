package com.movtery.zalithlauncher.setting

import com.movtery.zalithlauncher.context.ContextExecutor
import com.movtery.zalithlauncher.setting.unit.BooleanSettingUnit
import com.movtery.zalithlauncher.setting.unit.IntSettingUnit
import com.movtery.zalithlauncher.setting.unit.LongSettingUnit
import com.movtery.zalithlauncher.setting.unit.StringSettingUnit
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.prefs.LauncherPreferences

class AllSettings {
    companion object {
        // ── Video ──────────────────────────────────────────────────────────────
        @JvmStatic val renderer = StringSettingUnit("renderer", "f7e985d8-6d4c-f63c-d9f1-06074dab823a")
        @JvmStatic val driver   = StringSettingUnit("driver", "Turnip")

        // ── Renderer automatic fallback (see RendererFallbackManager) ────────────
        /** UUID of the renderer used on the most recent launch attempt - compared against
         *  the current AllSettings.renderer on the next launch to tell whether the user
         *  already changed renderer themselves (in which case we don't override their
         *  choice) versus this being a retry of the same one that just failed. */
        @JvmStatic val lastLaunchRendererId = StringSettingUnit("lastLaunchRendererId", "")
        /** Set right before launch, cleared on a clean (exit code 0) exit. Still true at
         *  the start of the next launch means the previous attempt never exited cleanly. */
        @JvmStatic val lastLaunchFailed = BooleanSettingUnit("lastLaunchFailed", false)

        // ── Advanced Renderer Settings ────────────────────────────────────────────
        /** Off = skip setting MESA_GLSL_CACHE_DIR entirely (Zink/VirGL/VGPU/Freedreno only
         *  - GL4ES-family renderers don't use it either way). Recompiles shaders from
         *  scratch every launch instead of reusing yesterday's compiled cache; the only
         *  reason to turn it off is suspecting a corrupted cache is itself causing issues. */
        @JvmStatic val rendererShaderCacheEnabled = BooleanSettingUnit("rendererShaderCacheEnabled", true)
        /** Extra renderer-selection/env-var logging to the in-app log, for diagnosing a
         *  renderer that won't start. Not a rendering-internals debug layer - that would
         *  need support from the renderer libraries themselves, which this launcher can't add. */
        @JvmStatic val rendererDebugLogging = BooleanSettingUnit("rendererDebugLogging", false)

        @JvmStatic val ignoreNotch              = BooleanSettingUnit("ignoreNotch", true)
        @JvmStatic val ignoreNotchLauncher      = BooleanSettingUnit("ignoreNotchLauncher", true)
        @JvmStatic val resolutionRatio          = IntSettingUnit("resolutionRatio", 100)
        @JvmStatic val sustainedPerformance     = BooleanSettingUnit("sustainedPerformance", false)
        @JvmStatic val alternateSurface         = BooleanSettingUnit("alternate_surface", false)
        @JvmStatic val forceVsync               = BooleanSettingUnit("force_vsync", false)
        @JvmStatic val vsyncInZink              = BooleanSettingUnit("vsync_in_zink", false)
        @JvmStatic val zinkPreferSystemDriver   = BooleanSettingUnit("zinkPreferSystemDriver", false)

        /** Auto Settings Optimizer: automatically tunes renderer/driver, RAM allocation, resolution scale, and FPS boost flags for this device at launch. */
        @JvmStatic val autoSettingsOptimizer    = BooleanSettingUnit("autoSettingsOptimizer", false)

        // ── Control ───────────────────────────────────────────────────────────
        @JvmStatic val disableGestures          = BooleanSettingUnit("disableGestures", true)
        @JvmStatic val disableDoubleTap         = BooleanSettingUnit("disableDoubleTap", false)
        @JvmStatic val timeLongPressTrigger     = IntSettingUnit("timeLongPressTrigger", 300)
        @JvmStatic val buttonScale              = IntSettingUnit("buttonscale", 100)
        @JvmStatic val buttonAllCaps            = BooleanSettingUnit("buttonAllCaps", false)
        @JvmStatic val mouseScale               = IntSettingUnit("mousescale", 100)
        @JvmStatic val mouseSpeed               = IntSettingUnit("mousespeed", 100)
        @JvmStatic val virtualMouseStart        = BooleanSettingUnit("mouse_start", true)
        @JvmStatic val customMouse              = StringSettingUnit("custom_mouse", "")
        @JvmStatic val enableGyro               = BooleanSettingUnit("enableGyro", false)
        @JvmStatic val gyroSensitivity          = IntSettingUnit("gyroSensitivity", 100)
        @JvmStatic val gyroSampleRate           = IntSettingUnit("gyroSampleRate", 16)
        @JvmStatic val gyroSmoothing            = BooleanSettingUnit("gyroSmoothing", true)
        @JvmStatic val gyroInvertX              = BooleanSettingUnit("gyroInvertX", false)
        @JvmStatic val gyroInvertY              = BooleanSettingUnit("gyroInvertY", false)
        @JvmStatic val deadZoneScale            = IntSettingUnit("gamepad_deadzone_scale", 100)

        // ── Game ──────────────────────────────────────────────────────────────
        @JvmStatic val versionIsolation         = BooleanSettingUnit("versionIsolation", true)
        @JvmStatic val versionCustomInfo        = StringSettingUnit("versionCustomInfo", "TurtleLauncher")
        @JvmStatic val autoSetGameLanguage      = BooleanSettingUnit("autoSetGameLanguage", true)
        @JvmStatic val gameLanguageOverridden   = BooleanSettingUnit("gameLanguageOverridden", false)
        @JvmStatic val setGameLanguage          = StringSettingUnit("setGameLanguage", "system")
        @JvmStatic val selectRuntimeMode        = StringSettingUnit("selectRuntimeMode", "auto")
        @JvmStatic val javaArgs = StringSettingUnit(
            "javaArgs",
            "-XX:+UseG1GC -XX:MaxGCPauseMillis=20 -XX:G1HeapRegionSize=16M " +
            "-XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20"
        )
        @JvmStatic val ramAllocation = lazy {
            IntSettingUnit("allocation", LauncherPreferences.findBestRAMAllocation(ContextExecutor.getApplication()))
        }
        @JvmStatic val javaSandbox              = BooleanSettingUnit("java_sandbox", true)
        @JvmStatic val gameMenuShowMemory       = BooleanSettingUnit("gameMenuShowMemory", false)
        @JvmStatic val gameMenuShowFPS          = BooleanSettingUnit("gameMenuShowFPS", true)
        @JvmStatic val gameMenuMemoryText       = StringSettingUnit("gameMenuMemoryText", "M:")
        @JvmStatic val gameMenuLocation         = StringSettingUnit("gameMenuLocation", "center")
        @JvmStatic val gameMenuInfoRefreshRate  = IntSettingUnit("gameMenuInfoRefreshRate", 250)
        @JvmStatic val gameMenuAlpha            = IntSettingUnit("gameMenuAlpha", 100)
        /** TurtleLauncher: opacity of the in-game info HUD (FPS/CPS/Keystrokes/etc window), independent of the menu button's own alpha. */
        @JvmStatic val hudAlpha                 = IntSettingUnit("hudAlpha", 100)
        /** TurtleLauncher: one-tap preset that toggles CPS + Keystrokes + Mousestrokes together for PvP. */
        @JvmStatic val pvpOverlayPreset         = BooleanSettingUnit("pvpOverlayPreset", false)
        /** TurtleLauncher: static pre-launch scan for mods whose Mixin configs both target the same class. */
        @JvmStatic val modConflictDetection     = BooleanSettingUnit("modConflictDetection", true)

        // ── Launcher ──────────────────────────────────────────────────────────
        @JvmStatic val checkLibraries           = BooleanSettingUnit("checkLibraries", true)
        @JvmStatic val verifyManifest           = BooleanSettingUnit("verifyManifest", true)
        @JvmStatic val resourceImageCache       = BooleanSettingUnit("resourceImageCache", true)
        @JvmStatic val addFullResourceName      = BooleanSettingUnit("addFullResourceName", true)
        @JvmStatic val downloadSource           = StringSettingUnit("downloadSource", "default")
        @JvmStatic val maxDownloadThreads       = IntSettingUnit("maxDownloadThreads", 128)
        @JvmStatic val launcherTheme            = StringSettingUnit("launcherTheme", "dark")
        @JvmStatic val animation                = BooleanSettingUnit("animation", false)
        @JvmStatic val animationSpeed           = IntSettingUnit("animationSpeed", 300)
        @JvmStatic val pageOpacity              = IntSettingUnit("pageOpacity", 100)
        @JvmStatic val enableLogOutput          = BooleanSettingUnit("enableLogOutput", false)
        @JvmStatic val quitLauncher             = BooleanSettingUnit("quitLauncher", true)
        @JvmStatic val acceptPreReleaseUpdates  = BooleanSettingUnit("acceptPreReleaseUpdates", false)

        // ── TurtleLauncher Mod Auto-Maintenance ─────────────────────────────────
        /** Automatically fetch & install a mod's missing mandatory dependencies from Modrinth before launch. */
        @JvmStatic val autoDependencyInstall    = BooleanSettingUnit("autoDependencyInstall", true)
        /** Automatically check installed mods for newer Modrinth versions before launch and offer to update them. */
        @JvmStatic val autoModUpdateCheck       = BooleanSettingUnit("autoModUpdateCheck", true)

        // ── Experimental ──────────────────────────────────────────────────────
        @JvmStatic val dumpShaders              = BooleanSettingUnit("dump_shaders", false)
        @JvmStatic val bigCoreAffinity          = BooleanSettingUnit("bigCoreAffinity", true)
        /** Fast Boot: skips file checksum/manifest verification and the pre-launch RAM check dialog to launch faster. */
        @JvmStatic val fastBoot                 = BooleanSettingUnit("fastBoot", false)

        // ── TurtleLauncher FPS Boost ──────────────────────────────────────────
        /** Remove Minecraft's 300-FPS cap; adds -XX:+DisableExplicitGC */
        @JvmStatic val unlimitedFps             = BooleanSettingUnit("unlimitedFps", false)
        /** Low-latency rendering: JVM string/compile optimizations */
        @JvmStatic val lowLatencyRendering      = BooleanSettingUnit("lowLatencyRendering", false)
        /** LWJGL frame-pacing hints for smoother mobile GPU frame delivery */
        @JvmStatic val framePacing              = BooleanSettingUnit("framePacing", false)
        /** Drop frames when overloaded instead of queuing (reduces input lag) */
        @JvmStatic val frameSkipping            = BooleanSettingUnit("frameSkipping", false)
        /** Short GC pause target to avoid mid-frame GC stops */
        @JvmStatic val adaptiveFrameTiming      = BooleanSettingUnit("adaptiveFrameTiming", false)

        // ── TurtleLauncher Renderer/Driver Plugin Updater ───────────────────────
        /** Automatically check for renderer/driver plugin updates from upstream sources on launcher start. */
        @JvmStatic val autoCheckPluginUpdates   = BooleanSettingUnit("autoCheckPluginUpdates", true)
        /** Timestamp (ms) of the last renderer/driver plugin update check, used for the 5-minute cooldown. */
        @JvmStatic val lastPluginUpdateCheck    = LongSettingUnit("lastPluginUpdateCheck", 0L)
        /** TurtleLauncher Fast Boot: Minecraft version id Auto Settings Optimizer last ran picks for; skips re-running when unchanged. */
        @JvmStatic val lastOptimizedVersion     = StringSettingUnit("lastOptimizedVersion", "")
        /** TurtleLauncher: RAM value (MB) Auto Settings Optimizer last wrote to ramAllocation, used to detect a manual user change so it isn't clobbered on the next launch. -1 = never run. */
        @JvmStatic val lastAutoRamAllocation    = IntSettingUnit("lastAutoRamAllocation", -1)

        // ── TurtleLauncher In-Game HUD Modules ───────────────────────────────────
        /** Clicks-per-second counter, tracked from the virtual left mouse button (attack). */
        @JvmStatic val showCpsHud                = BooleanSettingUnit("showCpsHud", true)
        /** Live WASD + Space key-press indicator. */
        @JvmStatic val showKeystrokesHud         = BooleanSettingUnit("showKeystrokesHud", true)
        /** Live left/right mouse-button-press indicator. */
        @JvmStatic val showMousestrokesHud       = BooleanSettingUnit("showMousestrokesHud", true)
        /** Session stopwatch — elapsed time since the current game session started. */
        @JvmStatic val showStopwatchHud          = BooleanSettingUnit("showStopwatchHud", false)
        /** Cumulative playtime across all sessions. */
        @JvmStatic val showPlaytimeHud           = BooleanSettingUnit("showPlaytimeHud", false)
        /** Battery percentage readout. */
        @JvmStatic val showSystemResourcesHud    = BooleanSettingUnit("showSystemResourcesHud", false)
        /** Real-world wall-clock time readout. */
        @JvmStatic val showTimeHud               = BooleanSettingUnit("showTimeHud", false)
        /** Persisted cumulative playtime in milliseconds, across all sessions. */
        @JvmStatic val totalPlaytimeMs           = LongSettingUnit("totalPlaytimeMs", 0L)

        // ── Other ─────────────────────────────────────────────────────────────
        @JvmStatic val tcVibrateDuration        = IntSettingUnit("tcVibrateDuration", 100)
        @JvmStatic val currentAccount           = StringSettingUnit("currentAccount", "")
        @JvmStatic val launcherProfile          = StringSettingUnit("launcherProfile", "default")
        @JvmStatic val defaultCtrl              = StringSettingUnit("defaultCtrl", PathManager.FILE_CTRLDEF_FILE)
        @JvmStatic val defaultRuntime           = StringSettingUnit("defaultRuntime", "")
        @JvmStatic val notificationPermissionRequest       = BooleanSettingUnit("notification_permission_request", false)
        @JvmStatic val skipNotificationPermissionCheck     = BooleanSettingUnit("skipNotificationPermissionCheck", false)
        @JvmStatic val localAccountReminders    = BooleanSettingUnit("localAccountReminders", true)
        @JvmStatic val updateCheck              = LongSettingUnit("updateCheck", 0L)
        @JvmStatic val ignoreUpdate             = StringSettingUnit("ignoreUpdate", "")
        @JvmStatic val noticeCheck              = LongSettingUnit("noticeCheck", 0L)
        @JvmStatic val noticeNumbering          = IntSettingUnit("noticeNumbering", 0)
        @JvmStatic val noticeDefault            = BooleanSettingUnit("noticeDefault", false)

        /**
         * TurtleLauncher: Last version for which What's New dialog was shown.
         * Accessed from Java via AllSettings.getWhatsNewShownVersion()
         * (Kotlin @JvmStatic generates a static getter, accessible as a property in Kotlin
         * and as a static method in Java).
         */
        @JvmStatic val whatsNewShownVersion     = StringSettingUnit("whatsNewShownVersion", "")

        @JvmStatic val buttonSnapping           = BooleanSettingUnit("buttonSnapping", true)
        @JvmStatic val buttonSnappingDistance   = IntSettingUnit("buttonSnappingDistance", 8)
        @JvmStatic val hotbarType               = StringSettingUnit("hotbarType", "auto")
        @JvmStatic val hotbarWidth = lazy {
            IntSettingUnit("hotbarWidth", Tools.currentDisplayMetrics.widthPixels / 3)
        }
        @JvmStatic val hotbarHeight = lazy {
            IntSettingUnit("hotbarHeight", Tools.currentDisplayMetrics.heightPixels / 4)
        }

        // ── TurtleLauncher v10 ────────────────────────────────────────────────
        // HUD / performance
        @JvmStatic val backgroundAssetPrefetch  = BooleanSettingUnit("backgroundAssetPrefetch", true)
        @JvmStatic val hudModuleIndependentDrag = BooleanSettingUnit("hudModuleIndependentDrag", false)
        @JvmStatic val hudModuleScale           = IntSettingUnit("hudModuleScale", 100)
        @JvmStatic val showRamGraphHud          = BooleanSettingUnit("showRamGraphHud", false)
        @JvmStatic val showPingHud              = BooleanSettingUnit("showPingHud", false)
        @JvmStatic val showScreenshotButtonHud  = BooleanSettingUnit("showScreenshotButtonHud", true)
        @JvmStatic val hudModulePositions       = StringSettingUnit("hudModulePositions", "{}")

        // Mods
        @JvmStatic val forgeConflictDetection   = BooleanSettingUnit("forgeConflictDetection", true)

        // Settings / UX
        @JvmStatic val settingsSearchHistory    = StringSettingUnit("settingsSearchHistory", "")
        @JvmStatic val resolutionAutoDetect     = BooleanSettingUnit("resolutionAutoDetect", false)
        @JvmStatic val compactMode              = BooleanSettingUnit("compactMode", false)
        @JvmStatic val leftHandedMode           = BooleanSettingUnit("leftHandedMode", false)
        @JvmStatic val fontScale                = IntSettingUnit("fontScale", 100)
        @JvmStatic val customBackgroundPath     = StringSettingUnit("customBackgroundPath", "")
        @JvmStatic val customBackgroundIsVideo  = BooleanSettingUnit("customBackgroundIsVideo", false)
        @JvmStatic val iconPackPath             = StringSettingUnit("iconPackPath", "")

        // Diagnostics
        @JvmStatic val offlineModeFallback      = BooleanSettingUnit("offlineModeFallback", true)
        @JvmStatic val crashHistoryList         = StringSettingUnit("crashHistoryList", "[]")
        @JvmStatic val customCrashRules         = StringSettingUnit("customCrashRules", "[]")
        @JvmStatic val anrDetectorEnabled       = BooleanSettingUnit("anrDetectorEnabled", true)
        @JvmStatic val anrTimeoutMs             = IntSettingUnit("anrTimeoutMs", 5000)
        @JvmStatic val logRegexFilterHistory    = StringSettingUnit("logRegexFilterHistory", "")

        // AI-assisted crash diagnosis: only used as a fallback when no local
        // CrashAnalyzer rule (including custom rules) recognises the crash, and only
        // if the user has supplied their own API key. Nothing is sent anywhere unless
        // both of those are true.
        @JvmStatic val aiCrashHelpEnabled       = BooleanSettingUnit("aiCrashHelpEnabled", false)
        @JvmStatic val aiCrashHelpApiKey        = StringSettingUnit("aiCrashHelpApiKey", "AIzaSyB-_Ncw-g5IRF-NxcOX6HlfqMpQTMFUjtQ")
        @JvmStatic val aiCrashHelpModel         = StringSettingUnit("aiCrashHelpModel", "gemini-flash-latest")

        // Custom DNS resolver for the launcher's own network requests (downloads/API
        // calls), independent of the download-source (BMCLAPI) mirror above.
        @JvmStatic val dnsServer                = StringSettingUnit("dnsServer", "cloudflare")
    }
}
