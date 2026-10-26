package com.endiq.turtlelauncher.launch

import com.endiq.turtlelauncher.renderer.RendererCatalog
import com.endiq.turtlelauncher.renderer.RendererInterface
import com.endiq.turtlelauncher.renderer.renderers.FreedrenoRenderer
import com.endiq.turtlelauncher.renderer.renderers.MobileGluesRenderer
import com.endiq.turtlelauncher.renderer.renderers.ZinkRenderer

/**
 * Pure decision logic for the automatic graphics picker.
 *
 * This used to live inline in [AutoSettingsOptimizer], where it could only be
 * exercised on a device: the rules were untestable, they treated the renderer and
 * the driver as one all-or-nothing decision, and they never checked a
 * recommendation against the renderer's own Minecraft version range. Keeping the
 * maths here means `GraphicsTuningTest` can cover every branch on a plain JVM,
 * and `GraphicsBenchmark` can feed a measured score into the same rules.
 *
 * No Android imports: everything is passed in (see [DeviceProfile]).
 */
object GraphicsTuning {

    /** GPU families this launcher has a tailored renderer for. */
    enum class GpuFamily { ADRENO, MALI, POWERVR, UNKNOWN }

    /** The measured/known facts the picker is allowed to look at. */
    data class DeviceProfile(
        /** `GL_VENDOR GL_RENDERER`, or a weaker `ro.hardware.egl` fallback. */
        val gpuName: String,
        val totalRamMb: Long,
        val screenPixels: Long,
        val refreshRateHz: Int,
        val hasVulkan: Boolean,
        /** Minecraft version id, e.g. "1.21.5"; blank = unknown/legacy. */
        val minecraftVersion: String,
        /** Normalised benchmark score from [com.endiq.turtlelauncher.feature.benchmark.GraphicsBenchmark]; null = not run. */
        val benchmarkScore: Int? = null,
        val severeThermal: Boolean = false,
        val powerSaving: Boolean = false
    ) {
        val throttled: Boolean get() = severeThermal || powerSaving
    }

    /** What the picker wants to change. Null fields are left alone. */
    data class Recommendation(
        val rendererUniqueId: String? = null,
        val rendererId: String? = null,
        val driver: String? = null,
        /** Percent of the display resolution Minecraft renders at (100 = native). */
        val resolutionPercent: Int = 100,
        val reason: String = ""
    )

    private const val LOW_TIER_RAM_MB = 3072L
    private const val HIGH_TIER_RAM_MB = 6144L

    /**
     * Benchmark score (higher is faster) mapped to a tier. Calibrated against the
     * fill-rate test in `GraphicsBenchmark`: ~<150 points is a device that struggles
     * at native resolution, >600 comfortably runs a 1080p-ish framebuffer.
     */
    enum class BenchTier { UNKNOWN, LOW, MID, HIGH }

    fun benchTier(score: Int?): BenchTier = when {
        score == null -> BenchTier.UNKNOWN
        score < 150 -> BenchTier.LOW
        score < 600 -> BenchTier.MID
        else -> BenchTier.HIGH
    }

    fun ramTier(totalRamMb: Long): BenchTier = when {
        totalRamMb <= 0L -> BenchTier.UNKNOWN
        totalRamMb < LOW_TIER_RAM_MB -> BenchTier.LOW
        totalRamMb < HIGH_TIER_RAM_MB -> BenchTier.MID
        else -> BenchTier.HIGH
    }

    fun gpuFamily(gpuName: String): GpuFamily {
        val name = gpuName.lowercase()
        return when {
            // "adreno" covers Qualcomm's own string; "fd" / "freedreno" appear when
            // the probe already runs through Mesa.
            "adreno" in name || "freedreno" in name -> GpuFamily.ADRENO
            "mali" in name || "bifrost" in name || "valhall" in name -> GpuFamily.MALI
            "powervr" in name || "sgx" in name || "apple" in name || "img" in name -> GpuFamily.POWERVR
            else -> GpuFamily.UNKNOWN
        }
    }

    /**
     * Renderer to recommend for this device, or null to leave the user's choice alone.
     *
     * [isAvailable] answers "does this renderer's native library exist in this APK
     * for this ABI" (Renderers.getCompatibleRenderers), and [inVersionRange] checks
     * [RendererCatalog]'s per-renderer Minecraft bounds. Both are injected so this
     * function stays pure and testable - a recommended renderer that cannot load is
     * worse than no recommendation at all, which is what the old code risked.
     */
    fun recommendRenderer(
        profile: DeviceProfile,
        isAvailable: (rendererId: String) -> Boolean = { true },
        inVersionRange: (rendererId: String, minecraftVersion: String) -> Boolean = { _, _ -> true }
    ): Pair<String, String>? {
        val family = gpuFamily(profile.gpuName)
        // Ranked preferences per family; the first one that both ships in this build
        // and covers the running Minecraft version wins.
        val preferences: List<String> = when (family) {
            GpuFamily.ADRENO -> listOf(
                // Freedreno is written for Adreno specifically, but it is only useful
                // with Turnip (Vulkan); without Vulkan the Mesa path cannot start.
                if (profile.hasVulkan) FreedrenoRenderer.ID else "",
                MobileGluesRenderer.ID,
                ZinkRenderer.ID
            )
            GpuFamily.MALI -> listOf(
                if (profile.hasVulkan) ZinkRenderer.ID else "",
                MobileGluesRenderer.ID
            )
            GpuFamily.POWERVR -> listOf(MobileGluesRenderer.ID, ZinkRenderer.ID)
            GpuFamily.UNKNOWN -> if (profile.hasVulkan) {
                listOf(ZinkRenderer.ID, MobileGluesRenderer.ID)
            } else emptyList()
        }
        for (id in preferences.filter { it.isNotEmpty() }) {
            if (!isAvailable(id)) continue
            if (!inVersionRange(id, profile.minecraftVersion)) continue
            val driver = if (id == FreedrenoRenderer.ID && profile.hasVulkan) "Turnip" else "default"
            return id to driver
        }
        return null
    }

    /**
     * Render resolution as a percentage of the display.
     *
     * The old rule keyed off RAM alone, which says nothing about fill rate - a 4GB
     * phone with a weak GPU rendered at 100% while a 6GB one with the same GPU did
     * too. This weighs the benchmark (when it has been run), the pixel count, the
     * refresh rate and thermals, and never goes above 100% or below 60%.
     */
    fun resolutionPercent(profile: DeviceProfile): Int {
        if (profile.powerSaving) return 60
        val tier = if (benchTier(profile.benchmarkScore) != BenchTier.UNKNOWN) {
            benchTier(profile.benchmarkScore)
        } else {
            // No benchmark yet: RAM is a weak but not useless proxy, so keep the old
            // conservative behaviour instead of guessing something more aggressive.
            ramTier(profile.totalRamMb)
        }
        val base = when (tier) {
            BenchTier.LOW -> 70
            BenchTier.MID -> 85
            BenchTier.HIGH -> 100
            BenchTier.UNKNOWN -> 100
        }
        // Large frames and high refresh rates cost fill rate we may not have.
        val pixelPenalty = when {
            profile.screenPixels > 3_700_000L -> 10   // >1440p
            profile.screenPixels > 2_100_000L -> 5    // >1080p
            else -> 0
        }
        val refreshPenalty = if (profile.refreshRateHz >= 120 && tier != BenchTier.HIGH) 5 else 0
        val thermalPercent = if (profile.severeThermal) 70 else 100
        val computed = base - pixelPenalty - refreshPenalty
        return (minOf(computed, thermalPercent)).coerceIn(60, 100)
    }

    /**
     * Full recommendation for one launch. Only fields whose current value still
     * matches the last automatic choice are offered (see
     * [PerformancePolicy.mayAutoTune]) - manual choices are never overwritten, which
     * the caller enforces by passing [mayChangeRenderer] / [mayChangeDriver].
     */
    fun recommend(
        profile: DeviceProfile,
        currentRenderer: String,
        currentDriver: String,
        mayChangeRenderer: Boolean,
        mayChangeDriver: Boolean,
        isAvailable: (String) -> Boolean = { true },
        inVersionRange: (String, String) -> Boolean = { _, _ -> true }
    ): Recommendation {
        val resolution = resolutionPercent(profile)
        val pick = if (mayChangeRenderer || mayChangeDriver) {
            recommendRenderer(profile, isAvailable, inVersionRange)
        } else null

        if (pick == null) {
            return Recommendation(
                resolutionPercent = resolution,
                reason = when {
                    !mayChangeRenderer && !mayChangeDriver -> "renderer and driver are user-chosen"
                    else -> "no compatible renderer for ${gpuFamily(profile.gpuName)}"
                }
            )
        }
        val (rendererId, driver) = pick
        return Recommendation(
            rendererUniqueId = if (mayChangeRenderer) uniqueIdOf(rendererId) else null,
            rendererId = if (mayChangeRenderer) rendererId else null,
            driver = if (mayChangeDriver) driver else null,
            resolutionPercent = resolution,
            reason = "auto: ${gpuFamily(profile.gpuName)} + " +
                (if (profile.hasVulkan) "Vulkan" else "no Vulkan") +
                ", bench=${profile.benchmarkScore ?: "n/a"}"
        )
    }

    /**
     * Built-in renderers the picker can recommend, keyed by [RendererInterface.getRendererId].
     * Instantiating them is cheap and side-effect free (their environment maps are lazy);
     * reading the unique identifier from the renderer itself means this map can never
     * drift from the renderer classes the way a copied UUID literal would.
     */
    private val BUILT_IN_RENDERERS: Map<String, RendererInterface> = listOf(
        FreedrenoRenderer(), MobileGluesRenderer(), ZinkRenderer()
    ).associateBy { it.getRendererId() }

    /** The setting stores a unique identifier; the catalog and picker use short ids. */
    fun uniqueIdOf(rendererId: String): String? =
        BUILT_IN_RENDERERS[rendererId]?.getUniqueIdentifier()

    /** Inverse of [uniqueIdOf], for code that only has the stored setting value. */
    fun rendererIdOf(uniqueId: String): String? =
        BUILT_IN_RENDERERS.values.firstOrNull { it.getUniqueIdentifier() == uniqueId }?.getRendererId()

    /**
     * Whether [rendererId] fits [minecraftVersion] according to [RendererCatalog]'s
     * inclusive bounds. Unknown renderers and unknown versions are allowed (the
     * catalog's absence of data is not evidence of incompatibility).
     */
    fun withinCatalogRange(rendererId: String, minecraftVersion: String): Boolean {
        if (minecraftVersion.isBlank()) return true
        val entry = RendererCatalog.get(rendererId) ?: return true
        val version = parseVersion(minecraftVersion) ?: return true
        entry.minMinecraftVersion?.let { min ->
            parseVersion(min)?.let { if (compare(version, it) < 0) return false }
        }
        entry.maxMinecraftVersion?.let { max ->
            parseVersion(max)?.let { if (compare(version, it) > 0) return false }
        }
        return true
    }

    /** "1.21.5" / "26.1.2" -> list of ints; snapshots ("24w09a") -> null. */
    fun parseVersion(raw: String): List<Int>? {
        val cleaned = raw.trim()
        if (cleaned.isEmpty() || !cleaned[0].isDigit() || 'w' in cleaned.lowercase()) return null
        return cleaned.split('.', '-').mapNotNull { it.toIntOrNull() }.takeIf { it.isNotEmpty() }
    }

    /** Lexicographic integer comparison; missing trailing parts count as 0. */
    fun compare(a: List<Int>, b: List<Int>): Int {
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }
}
