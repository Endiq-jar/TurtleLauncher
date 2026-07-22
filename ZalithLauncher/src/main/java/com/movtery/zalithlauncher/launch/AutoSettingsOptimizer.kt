package com.movtery.zalithlauncher.launch

import android.content.Context
import android.opengl.EGL14
import android.os.Build
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.renderer.renderers.FreedrenoRenderer
import com.movtery.zalithlauncher.renderer.renderers.HolyGL4ESRenderer
import com.movtery.zalithlauncher.renderer.renderers.KryptonWrapperRenderer
import com.movtery.zalithlauncher.renderer.renderers.ZinkRenderer
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.utils.platform.BatterySaverManager
import com.movtery.zalithlauncher.utils.platform.ThermalManager
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.prefs.LauncherPreferences

/**
 * AutoSettingsOptimizer (replaces the old AutoGraphicsOptimizer)
 *
 * Tunes more than just renderer/driver: also sets a sensible RAM
 * allocation, live resolution scale, and the TurtleLauncher FPS Boost flags
 * for the device's tier, all before launch. Enabled by default — anything
 * it sets can still be overridden by hand in Video/Experimental settings
 * (it just re-applies its own picks on the next launch unless turned off).
 *
 * Renderer/driver selection maps detected GPU vendor to one of the six FCL-sourced
 * renderers (see renderer/renderers/ and RendererCatalog):
 *   Adreno GPU     → Freedreno, driver = Turnip (if Vulkan available)
 *   Mali/ARM GPU   → Zink if Vulkan available, else Krypton Wrapper
 *   PowerVR/other  → Holy GL4ES, driver = default
 *   Unknown        → renderer left as-is
 *
 * Special case (MC 26.1+): MC 26.x's CubeMapTexture panorama background
 * calls glTexImage2D with GL_TEXTURE_CUBE_MAP + GL_RGB8, which OpenGL ES
 * 3.2 doesn't support as a renderable cubemap format — causing a crash
 * (GL_INVALID_ENUM, error 1280) on PowerVR/Apple/unknown GPUs. For MC 26.1+
 * on those GPUs we switch to Zink if Vulkan is available, which
 * avoids the ES cubemap limitation entirely.
 *
 * Device tiers (by total RAM) drive everything else:
 *   LOW  (< 3GB)   → RAM alloc from findBestRAMAllocation, 80% resolution scale,
 *                    frame skipping on (prioritize responsiveness over smoothness),
 *                    other FPS boost flags left off.
 *   MID  (3–6GB)   → full resolution, adaptive frame timing on, FPS boost flags
 *                    otherwise left off (no need, no obvious win on mid-tier silicon).
 *   HIGH (> 6GB)   → full resolution, all FPS boost flags on (unlimited FPS,
 *                    low-latency rendering, frame pacing, adaptive frame timing).
 *
 * A device already thermally throttled (API 29+, see [ThermalManager]) or with
 * Android's own Battery Saver on (see [BatterySaverManager]) at launch time gets
 * capped one profile down from what its RAM tier alone would pick; a severely
 * throttled device gets forced onto a conservative profile outright. RAM
 * allocation itself is untouched by this - only the FPS-boost/resolution flags
 * that actually drive extra heat and power draw.
 */
object AutoSettingsOptimizer {
    private const val TAG = "AutoSettingsOptimizer"

    private const val LOW_TIER_RAM_MB = 3072
    private const val HIGH_TIER_RAM_MB = 6144

    /**
     * @param context       Activity context
     * @param mcVersionId   Minecraft version string, e.g. "26.1.2" (empty = legacy path)
     */
    fun apply(context: Context, mcVersionId: String = "") {
        // Fast Boot: if we already computed/applied picks for this exact version, skip
        // re-running GPU detection + tier math (the result wouldn't change anyway).
        if (AllSettings.fastBoot.getValue() && mcVersionId.isNotEmpty() &&
            AllSettings.lastOptimizedVersion.getValue() == mcVersionId) {
            Logging.i(TAG, "Fast Boot: skipping Auto Settings Optimizer (already applied for $mcVersionId)")
            return
        }
        applyGraphics(context, mcVersionId)
        applyPerformanceTier(context)
        if (mcVersionId.isNotEmpty()) {
            AllSettings.lastOptimizedVersion.put(mcVersionId).save()
        }
    }

    private fun applyGraphics(context: Context, mcVersionId: String) {
        val gpu = detectGpu()
        val isMC26Plus = LaunchArgs.isMinecraftVersionAtLeast(mcVersionId, 26, 1, 0)
        Logging.i(TAG, "Detected GPU: $gpu | Manufacturer: ${Build.MANUFACTURER} | Android ${Build.VERSION.SDK_INT} | MC26+: $isMC26Plus")

        val hasVulkan = Tools.checkVulkanSupport(context.packageManager)

        // TurtleLauncher: this used to store short strings like "gl4es114"/"opengles3" into
        // AllSettings.renderer, which Renderers.setCurrentRenderer looks up by UUID
        // (getUniqueIdentifier()) - those never matched anything, so this picker silently
        // always fell back to the first renderer in the list regardless of what was
        // "selected" here. Fixed to store the real UUID of one of the six FCL-sourced
        // renderers (see the renderer/renderers/ package).
        val (rendererUuid, driver) = when {
            gpu.contains("adreno", ignoreCase = true) -> {
                // Freedreno is explicitly "optimized primarily for Qualcomm Adreno GPUs"
                // (see FreedrenoRenderer's doc comment) - a better match than a generic
                // GL4ES build now that it's an actual available option.
                val driverChoice = if (hasVulkan) "Turnip" else "default"
                Pair(FreedrenoRenderer().getUniqueIdentifier(), driverChoice)
            }
            gpu.contains("mali", ignoreCase = true) ||
            gpu.contains("arm", ignoreCase = true) -> {
                if (hasVulkan) {
                    Pair(ZinkRenderer().getUniqueIdentifier(), "default")
                } else {
                    Pair(KryptonWrapperRenderer().getUniqueIdentifier(), "default")
                }
            }
            gpu.contains("powervr", ignoreCase = true) ||
            gpu.contains("sgx", ignoreCase = true) ||
            gpu.contains("apple", ignoreCase = true) -> {
                if (isMC26Plus && hasVulkan) {
                    Logging.i(TAG, "MC 26.1+ on PowerVR/Apple GPU: switching to Zink to avoid CubeMap GL_INVALID_ENUM crash")
                    Pair(ZinkRenderer().getUniqueIdentifier(), "default")
                } else {
                    Pair(HolyGL4ESRenderer().getUniqueIdentifier(), "default")
                }
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

        Logging.i(TAG, "Auto-selected renderer=$rendererUuid driver=$driver")
        AllSettings.renderer.put(rendererUuid).save()
        AllSettings.driver.put(driver).save()
    }

    private fun applyPerformanceTier(context: Context) {
        val totalRamMb = Tools.getTotalDeviceMemory(context)
        val bestRam = LauncherPreferences.findBestRAMAllocation(context)

        // Only overwrite RAM allocation if the user hasn't manually changed it since
        // we last set it (lastAutoRam == -1 means we've never applied a pick yet on
        // this install). Otherwise every launch silently reverted a manual override
        // back to our computed value.
        val currentRam = AllSettings.ramAllocation.value.getValue()
        val lastAutoRam = AllSettings.lastAutoRamAllocation.getValue()
        val appliedRam: Int
        if (lastAutoRam == -1 || currentRam == lastAutoRam) {
            AllSettings.ramAllocation.value.put(bestRam).save()
            AllSettings.lastAutoRamAllocation.put(bestRam).save()
            appliedRam = bestRam
        } else {
            Logging.i(TAG, "Skipping RAM auto-tune: user manually set ${currentRam}MB (last auto pick was ${lastAutoRam}MB)")
            appliedRam = currentRam
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

    private fun detectGpu(): String {
        return try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            EGL14.eglInitialize(display, null, 0, null, 0)
            val vendor = EGL14.eglQueryString(display, EGL14.EGL_VENDOR) ?: ""
            val renderer = System.getProperty("ro.hardware.egl") ?: ""
            val gpuFromBuild = Build.HARDWARE
            "$vendor $renderer $gpuFromBuild"
        } catch (e: Exception) {
            Build.HARDWARE
        }
    }
}
