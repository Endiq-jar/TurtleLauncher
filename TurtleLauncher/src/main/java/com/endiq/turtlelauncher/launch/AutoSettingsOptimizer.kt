package com.endiq.turtlelauncher.launch

import android.content.Context
import android.opengl.EGL14
import android.os.Build
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.renderer.renderers.FreedrenoRenderer
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
    // Bump when the automatic performance policy changes so Fast Boot applies the new profile once.
    private const val OPTIMIZER_SCHEMA_VERSION = "sd665-profile-v2"
    private val ADRENO_610_PATTERN = Regex("adreno[^0-9]{0,12}610", RegexOption.IGNORE_CASE)

    // GPU detection is expensive enough to avoid repeating on every launch. The value is
    // hardware-specific for the lifetime of this process.
    @Volatile private var cachedGpuDescription: String? = null

    /**
     * @param context       Activity context
     * @param mcVersionId   Minecraft version string, e.g. "26.1.2" (empty = legacy path)
     */
    fun apply(context: Context, mcVersionId: String = "") {
        val optimizationKey = if (mcVersionId.isNotEmpty()) {
            "$mcVersionId@$OPTIMIZER_SCHEMA_VERSION"
        } else {
            ""
        }
        // Fast Boot must short-circuit BEFORE probing EGL or rewriting renderer settings.
        // The schema suffix makes the new profile run once after this update, even if the old
        // app already saved the same Minecraft version ID.
        if (AllSettings.fastBoot.getValue() && optimizationKey.isNotEmpty() &&
            AllSettings.lastOptimizedVersion.getValue() == optimizationKey) {
            Logging.i(TAG, "Fast Boot: skipping GPU probe and optimizer (already applied for $optimizationKey)")
            return
        }
        val gpu = detectGpu()
        applyGraphics(context, mcVersionId, gpu)
        applyPerformanceTier(context, gpu)
        if (optimizationKey.isNotEmpty()) {
            AllSettings.lastOptimizedVersion.put(optimizationKey).save()
        }
    }

    private fun applyGraphics(context: Context, mcVersionId: String, gpu: String) {
        // Renderer auto-selection intentionally disabled. Some renderer implementations
        // were removed from this source tree; do not reference or instantiate them here.
        // Keep the renderer and driver selected by the user, and let the launch-time
        // validity check handle a renderer that is no longer installed.
        Logging.i(
            TAG,
            "GPU detected: $gpu | Minecraft=$mcVersionId | preserving user-selected renderer=" +
                "${AllSettings.renderer.getValue()} and driver=${AllSettings.driver.getValue()}"
        )
    }

    private fun applyPerformanceTier(context: Context, gpu: String) {
        val totalRamMb = Tools.getTotalDeviceMemory(context)
        val targetDeviceClass = isSnapdragon665Class(gpu)

        val appliedRam: Int
        if (!AllSettings.autoRamCalculator.getValue()) {
            appliedRam = AllSettings.ramAllocation.value.getValue()
            Logging.i(TAG, "Auto RAM Calculator disabled in Phone Settings: leaving RAM at ${appliedRam}MB")
        } else {
            val bestRam = presetAdjustedRam(
                context,
                LauncherPreferences.findBestRAMAllocation(context),
                targetDeviceClass
            )

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

        when {
            severelyThrottled -> {
                Logging.i(TAG, "Severe thermal throttling: forcing conservative profile, RAM=${appliedRam}MB")
                AllSettings.resolutionRatio.put(70).save()
                AllSettings.frameSkipping.put(true).save()
                AllSettings.unlimitedFps.put(false).save()
                AllSettings.lowLatencyRendering.put(false).save()
                AllSettings.framePacing.put(false).save()
                AllSettings.adaptiveFrameTiming.put(true).save()
            }
            targetDeviceClass -> {
                // RAM amount alone misclassifies an 8 GB phone as high-end even when its
                // Adreno 610 GPU is the bottleneck. Use a balanced GPU-aware profile.
                Logging.i(TAG, "Snapdragon 665 / Adreno 610 profile: 80% resolution, capped FPS boost, RAM=${appliedRam}MB")
                AllSettings.resolutionRatio.put(80).save()
                AllSettings.frameSkipping.put(false).save()
                AllSettings.unlimitedFps.put(false).save()
                AllSettings.lowLatencyRendering.put(false).save()
                AllSettings.framePacing.put(false).save()
                AllSettings.adaptiveFrameTiming.put(true).save()
            }
            totalRamMb < LOW_TIER_RAM_MB -> {
                Logging.i(TAG, "Low-tier device (${totalRamMb}MB RAM): 80% resolution, frame skipping on, RAM=${appliedRam}MB")
                AllSettings.resolutionRatio.put(80).save()
                AllSettings.frameSkipping.put(true).save()
                AllSettings.unlimitedFps.put(false).save()
                AllSettings.lowLatencyRendering.put(false).save()
                AllSettings.framePacing.put(false).save()
                AllSettings.adaptiveFrameTiming.put(false).save()
            }
            totalRamMb < HIGH_TIER_RAM_MB || throttled -> {
                Logging.i(TAG, "Mid-tier profile (${totalRamMb}MB RAM, capped=$throttled): full resolution, adaptive frame timing on, RAM=${appliedRam}MB")
                AllSettings.resolutionRatio.put(100).save()
                AllSettings.frameSkipping.put(false).save()
                AllSettings.unlimitedFps.put(false).save()
                AllSettings.lowLatencyRendering.put(false).save()
                AllSettings.framePacing.put(false).save()
                AllSettings.adaptiveFrameTiming.put(true).save()
            }
            else -> {
                Logging.i(TAG, "High-tier device (${totalRamMb}MB RAM): full FPS Boost, RAM=${appliedRam}MB")
                AllSettings.resolutionRatio.put(100).save()
                AllSettings.frameSkipping.put(false).save()
                AllSettings.unlimitedFps.put(true).save()
                AllSettings.lowLatencyRendering.put(true).save()
                AllSettings.framePacing.put(true).save()
                AllSettings.adaptiveFrameTiming.put(true).save()
            }
        }
    }

    private fun presetAdjustedRam(context: Context, baseline: Int, targetDeviceClass: Boolean): Int {
        val totalRamMb = Tools.getTotalDeviceMemory(context)
        val deviceCapMb = if (targetDeviceClass) {
            // Keep enough physical RAM for Android, the launcher and native graphics buffers.
            minOf(3072, (totalRamMb * 0.42).toInt().takeIf { totalRamMb > 0 } ?: 3072)
        } else {
            (totalRamMb * 0.50).toInt().takeIf { totalRamMb > 0 } ?: baseline
        }.coerceAtLeast(768)

        return when (AllSettings.ramPreset.getValue()) {
            "low" -> minOf((baseline * 0.70).toInt(), deviceCapMb).coerceAtLeast(512)
            "high" -> minOf((baseline * 1.40).toInt(), deviceCapMb).coerceAtLeast(512)
            "balanced" -> minOf(baseline, deviceCapMb).coerceAtLeast(512)
            "custom" -> AllSettings.ramAllocation.value.getValue() // Respect explicit user allocation.
            else -> minOf(baseline, deviceCapMb).coerceAtLeast(512)
        }
    }

    private fun isSnapdragon665Class(gpu: String): Boolean {
        val normalizedGpu = gpu.lowercase()
        val gpuLooksLikeAdreno610 = normalizedGpu.contains("adreno") &&
            ADRENO_610_PATTERN.containsMatchIn(normalizedGpu)
        val deviceId = "${Build.MODEL} ${Build.DEVICE}".lowercase()
        return gpuLooksLikeAdreno610 || deviceId.contains("v1965a")
    }

    private fun detectGpu(): String {
        cachedGpuDescription?.let { return it }
        val detected = queryGlRenderer()?.takeIf { it.isNotBlank() } ?: buildString {
            append(Build.MANUFACTURER).append(' ')
            append(Build.MODEL).append(' ')
            append(Build.HARDWARE)
            System.getProperty("ro.hardware.egl")?.takeIf { it.isNotBlank() }?.let {
                append(' ').append(it)
            }
        }
        return synchronized(this) {
            cachedGpuDescription ?: detected.also { cachedGpuDescription = it }
        }
    }

    private fun queryGlRenderer(): String? {
        var display = EGL14.EGL_NO_DISPLAY
        var eglContext = EGL14.EGL_NO_CONTEXT
        var surface = EGL14.EGL_NO_SURFACE
        var initialized = false
        return try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return null

            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
            initialized = true

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
                return null
            }
            val config = configs[0] ?: return null

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) return null

            val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            surface = EGL14.eglCreatePbufferSurface(display, config, pbufferAttribs, 0)
            if (surface == EGL14.EGL_NO_SURFACE) return null
            if (!EGL14.eglMakeCurrent(display, surface, surface, eglContext)) return null

            val renderer = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_RENDERER)
            val vendor = android.opengl.GLES20.glGetString(android.opengl.GLES20.GL_VENDOR)
            if (renderer.isNullOrBlank()) null else "$vendor $renderer"
        } catch (e: Exception) {
            Logging.w(TAG, "GL_RENDERER probe failed, falling back to device identifiers: ${e.message}")
            null
        } finally {
            if (display != EGL14.EGL_NO_DISPLAY) {
                runCatching {
                    EGL14.eglMakeCurrent(
                        display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                    )
                }
                if (surface != EGL14.EGL_NO_SURFACE) runCatching { EGL14.eglDestroySurface(display, surface) }
                if (eglContext != EGL14.EGL_NO_CONTEXT) runCatching { EGL14.eglDestroyContext(display, eglContext) }
                if (initialized) runCatching { EGL14.eglTerminate(display) }
            }
        }
    }

}
