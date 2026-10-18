package com.endiq.turtlelauncher.launch

import android.content.Context
import android.opengl.EGL14
import android.os.Build
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.renderer.renderers.FreedrenoRenderer
import com.endiq.turtlelauncher.renderer.renderers.MobileGluesRenderer
import com.endiq.turtlelauncher.renderer.renderers.ZinkRenderer
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.platform.BatterySaverManager
import com.endiq.turtlelauncher.utils.platform.ThermalManager
import net.endiq.launcher.Tools
import net.endiq.launcher.prefs.LauncherPreferences

object AutoSettingsOptimizer {
    private const val TAG = "AutoSettingsOptimizer"

    private const val LOW_TIER_RAM_MB = 3072
    private const val HIGH_TIER_RAM_MB = 6144

    /**
     * @param context       Activity context
     * @param mcVersionId   Minecraft version string, e.g. "26.1.2" (empty = legacy path)
     */
    fun apply(context: Context, mcVersionId: String = "") {
        applyGraphics(context, mcVersionId)
        // Fast Boot must not cache a cold launch's thermal decision for all later sessions.
        applyPerformanceTier(context)
        if (mcVersionId.isNotEmpty()) {
            AllSettings.lastOptimizedVersion.put(mcVersionId).save()
        }
    }

    private fun applyGraphics(context: Context, mcVersionId: String) {
        val gpu = detectGpu()
        val isMC26Plus = LaunchArgs.isMinecraftVersionAtLeast(mcVersionId, 26, 1, 0)
        @Suppress("DEPRECATION")
        val display = if (Build.VERSION.SDK_INT >= 30) context.display
            else (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay
        val refreshRate = display?.refreshRate?.toInt() ?: 60
        val metrics = context.resources.displayMetrics
        val cpuCores = Runtime.getRuntime().availableProcessors()
        // Queried via ActivityManager, not a live GL context (none exists yet at this point -
        // the game hasn't started) - the standard, correct way to get this ahead of time.
        val glEsVersionPacked = (context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager)
            ?.deviceConfigurationInfo?.reqGlEsVersion ?: 0
        val glEsVersion = "${glEsVersionPacked shr 16}.${glEsVersionPacked and 0xffff}"
        Logging.i(TAG, "Detected GPU: $gpu | Manufacturer: ${Build.MANUFACTURER} | Android ${Build.VERSION.SDK_INT} | " +
                "MC26+: $isMC26Plus | Refresh rate: ${refreshRate}Hz | Resolution: ${metrics.widthPixels}x${metrics.heightPixels} | " +
                "CPU cores: $cpuCores | OpenGL ES: $glEsVersion")

        val hasVulkan = Tools.checkVulkanSupport(context.packageManager)

        val (rendererUuid, driver) = when {
            gpu.contains("adreno", ignoreCase = true) -> {
                // Freedreno is explicitly "optimized primarily for Qualcomm Adreno GPUs"
                // (see FreedrenoRenderer's doc comment) - a better match than a generic
                // GL4ES build now that it's an actual available option.
                val driverChoice = if (hasVulkan) "Turnip" else "default"
                Pair(FreedrenoRenderer().getUniqueIdentifier(), driverChoice)
            }
            gpu.contains("mali", ignoreCase = true) -> {
                if (hasVulkan) {
                    Pair(ZinkRenderer().getUniqueIdentifier(), "default")
                } else {
                    // MobileGlues, not LTW: matches the launcher default (see class doc).
                    Pair(MobileGluesRenderer().getUniqueIdentifier(), "default")
                }
            }
            gpu.contains("powervr", ignoreCase = true) ||
            gpu.contains("sgx", ignoreCase = true) ||
            gpu.contains("apple", ignoreCase = true) -> {
                // MobileGlues on every version, including MC 26.1+ - the Zink pick this used to make
                // for 26.x avoided the CubeMap GL_INVALID_ENUM crash but lands in the opposite
                // problem on 26.3+: Amethyst-Android's release notes list MobileGlues and Krypton
                // Wrapper as crashing there because of how SDL now creates its EGL window, and MC
                // 26.3 hangs on PowerVR unless the renderer is MobileGlues (see CrashAnalyzer's rule
                // for that log shape). MobileGlues is also what the ANGLE hook has to sit under:
                // GL -> MobileGlues -> ANGLE -> Vulkan, with the same wrapper on every MC version.
                Logging.i(
                    TAG,
                    "PowerVR/Apple GPU: selecting MobileGlues (MC26+: $isMC26Plus, Vulkan: $hasVulkan); " +
                        "ANGLE sits under it when the ANGLE hook applies"
                )
                Pair(MobileGluesRenderer().getUniqueIdentifier(), "default")
            }
            else -> {
                if (isMC26Plus && hasVulkan) {
                    Logging.i(TAG, "MC 26.1+ unknown GPU: attempting Zink to avoid CubeMap GL_INVALID_ENUM crash")
                    Pair(ZinkRenderer().getUniqueIdentifier(), "default")
                } else {
                    Logging.i(TAG, "Unknown GPU, leaving renderer/driver unchanged")
                    return
                }
            }
        }

        val previous = AllSettings.lastAutoGraphics.getValue().split('|')
        val currentRenderer = AllSettings.renderer.getValue()
        val currentDriver = AllSettings.driver.getValue()
        val mayChangeRenderer = PerformancePolicy.mayAutoTune(
            currentRenderer, previous.getOrNull(0)?.takeIf { it.isNotBlank() }, AllSettings.renderer.defaultValue
        )
        val mayChangeDriver = PerformancePolicy.mayAutoTune(
            currentDriver, previous.getOrNull(1)?.takeIf { it.isNotBlank() }, AllSettings.driver.defaultValue
        )
        if (!mayChangeRenderer || !mayChangeDriver) {
            Logging.i(TAG, "Keeping manually selected renderer/driver instead of overwriting it with auto recommendations")
            return
        }
        Logging.i(TAG, "Auto-selected renderer=$rendererUuid driver=$driver")
        if (currentRenderer != rendererUuid) AllSettings.renderer.put(rendererUuid).save()
        if (currentDriver != driver) AllSettings.driver.put(driver).save()
        val snapshot = "$rendererUuid|$driver"
        if (AllSettings.lastAutoGraphics.getValue() != snapshot) AllSettings.lastAutoGraphics.put(snapshot).save()
    }

    private fun applyPerformanceTier(context: Context) {
        val totalRamMb = Tools.getTotalDeviceMemory(context)

        val appliedRam: Int
        if (!AllSettings.autoRamCalculator.getValue()) {
            appliedRam = AllSettings.ramAllocation.value.getValue()
            Logging.i(TAG, "Auto RAM Calculator disabled in Phone Settings: leaving RAM at ${appliedRam}MB")
        } else {
            val bestRam = presetAdjustedRam(context, LauncherPreferences.findBestRAMAllocation(context))

            val currentRam = AllSettings.ramAllocation.value.getValue()
            val lastAutoRam = AllSettings.lastAutoRamAllocation.getValue()
            if (lastAutoRam == -1 || currentRam == lastAutoRam) {
                AllSettings.ramAllocation.value.put(bestRam).save()
                AllSettings.lastAutoRamAllocation.put(bestRam).save()
                appliedRam = bestRam
            } else {
                Logging.i(TAG, "Skipping RAM auto-tune: user manually set ${currentRam}MB (last auto pick was ${lastAutoRam}MB)")
                appliedRam = currentRam
            }
        }

        val powerSaving = BatterySaverManager.isPowerSaveMode(context)
        val throttled = ThermalManager.isThrottled(context) || powerSaving
        val severelyThrottled = ThermalManager.isSeverelyThrottled(context)
        if (throttled) {
            Logging.i(TAG, "Capping FPS-boost profile regardless of RAM tier (thermal severe=$severelyThrottled, batterySaver=$powerSaving)")
        }

        val previous = AllSettings.lastAutoPerformanceProfile.getValue().split(';')
            .mapNotNull { entry ->
                if ('=' !in entry) null else entry.substringBefore('=') to entry.substringAfter('=')
            }.toMap().toMutableMap()
        fun <T> tune(setting: com.endiq.turtlelauncher.setting.unit.AbstractSettingUnit<T>, value: T) {
            val current = setting.getValue().toString()
            if (PerformancePolicy.mayAutoTune(current, previous[setting.key], setting.defaultValue.toString())) {
                if (current != value.toString()) setting.put(value).save()
                previous[setting.key] = value.toString()
            }
        }
        tune(AllSettings.resolutionRatio, PerformancePolicy.resolutionPercent(totalRamMb.toLong(), severelyThrottled))
        // Never equate a large RAM capacity with permission to uncap the GPU or pre-touch
        // a large heap. These features remain available as explicit user choices.
        tune(AllSettings.unlimitedFps, false)
        tune(AllSettings.lowLatencyRendering, false)
        tune(AllSettings.framePacing, false)
        tune(AllSettings.adaptiveFrameTiming, true)
        val snapshot = previous.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" }
        if (AllSettings.lastAutoPerformanceProfile.getValue() != snapshot) {
            AllSettings.lastAutoPerformanceProfile.put(snapshot).save()
        }
        Logging.i(TAG, "Sustainable auto profile: RAM=${appliedRam}MB, thermal capped=$throttled; manual overrides preserved")
    }

    private fun presetAdjustedRam(context: Context, baseline: Int): Int {
        val totalRamMb = Tools.getTotalDeviceMemory(context)
        return when (AllSettings.ramPreset.getValue()) {
            "low" -> (baseline * 0.7).toInt().coerceAtLeast(384)
            "high" -> (baseline * 1.4).toInt().coerceAtMost((totalRamMb * 0.75).toInt()).coerceAtLeast(baseline)
            else -> baseline // "balanced" or "custom"
        }
    }

    /**
     * Best-effort GPU name for the auto-picker. Internal rather than private because
     * Used to identify the hardware for automatic settings.
     * instead of running a second EGL probe of its own.
     */
    internal fun detectGpu(): String {
        queryGlRenderer()?.let { return it }
        return try {
            // queryGlRenderer owns a balanced EGL probe. Do not initialize another
            // display here and leak its reference on the fallback path.
            val renderer = System.getProperty("ro.hardware.egl") ?: ""
            "$renderer ${Build.HARDWARE}"
        } catch (e: Exception) {
            Build.HARDWARE
        }
    }

    private fun queryGlRenderer(): String? {
        val previousDisplay = EGL14.eglGetCurrentDisplay()
        val previousContext = EGL14.eglGetCurrentContext()
        val previousRead = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
        val previousDraw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        var initialized = false
        var display = EGL14.EGL_NO_DISPLAY
        var context = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        return try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return null
            val version = IntArray(2)
            if (display != previousDisplay) {
                if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
                initialized = true
            }

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) return null
            val config = configs[0] ?: return null

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (context == EGL14.EGL_NO_CONTEXT) return null

            val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            surface = EGL14.eglCreatePbufferSurface(display, config, pbufferAttribs, 0)
            if (surface == EGL14.EGL_NO_SURFACE) return null

            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return null

            val renderer = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER)
            val vendor = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VENDOR)
            if (renderer.isNullOrBlank()) null else "$vendor $renderer"
        } catch (e: Exception) {
            Logging.w(TAG, "GL_RENDERER probe failed, falling back to weaker GPU signals: ${e.message}")
            null
        } finally {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                if (previousDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(previousDisplay, previousDraw, previousRead, previousContext)
                }
                if (initialized) EGL14.eglTerminate(display)
            }
        }
    }
}
