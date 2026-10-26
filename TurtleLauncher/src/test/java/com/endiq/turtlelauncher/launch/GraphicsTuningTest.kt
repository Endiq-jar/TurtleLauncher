package com.endiq.turtlelauncher.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the automatic graphics picker.
 *
 * These run in plain `testDebugUnitTest` (no device, no emulator), which is the
 * point of pulling the policy out of AutoSettingsOptimizer: every branch below used
 * to be reachable only on real hardware, with the rules copy-pasted into an
 * Android-only object.
 */
class GraphicsTuningTest {

    private fun profile(
        gpu: String = "Adreno (TM) 640",
        ramMb: Long = 6144,
        pixels: Long = 2_073_600,
        refresh: Int = 60,
        vulkan: Boolean = true,
        version: String = "1.21.5",
        score: Int? = null,
        severeThermal: Boolean = false,
        powerSaving: Boolean = false
    ) = GraphicsTuning.DeviceProfile(
        gpuName = gpu,
        totalRamMb = ramMb,
        screenPixels = pixels,
        refreshRateHz = refresh,
        hasVulkan = vulkan,
        minecraftVersion = version,
        benchmarkScore = score,
        severeThermal = severeThermal,
        powerSaving = powerSaving
    )

    // ── GPU family detection ─────────────────────────────────────────────────

    @Test
    fun `recognises the GPU families the launcher has renderers for`() {
        assertEquals(GraphicsTuning.GpuFamily.ADRENO, GraphicsTuning.gpuFamily("Qualcomm Adreno (TM) 730"))
        assertEquals(GraphicsTuning.GpuFamily.MALI, GraphicsTuning.gpuFamily("Mali-G78 MP14"))
        assertEquals(GraphicsTuning.GpuFamily.MALI, GraphicsTuning.gpuFamily("Valhall"))
        assertEquals(GraphicsTuning.GpuFamily.POWERVR, GraphicsTuning.gpuFamily("PowerVR Rogue GE8320"))
        assertEquals(GraphicsTuning.GpuFamily.UNKNOWN, GraphicsTuning.gpuFamily("Vivante GC7000"))
        // Mesa reports Freedreno as the renderer once it is in the path.
        assertEquals(GraphicsTuning.GpuFamily.ADRENO, GraphicsTuning.gpuFamily("freedreno"))
    }

    // ── Renderer recommendation ──────────────────────────────────────────────

    @Test
    fun `Adreno with Vulkan prefers Freedreno and Turnip`() {
        val pick = GraphicsTuning.recommendRenderer(profile(ramMb = 8192, vulkan = true))
        assertNotNull(pick)
        assertEquals("FREEDRENO", pick!!.first)
        assertEquals("Turnip", pick.second)
    }

    @Test
    fun `Adreno without Vulkan falls back to MobileGlues`() {
        val pick = GraphicsTuning.recommendRenderer(profile(vulkan = false))
        assertNotNull(pick)
        assertEquals("MOBILEGLUES", pick!!.first)
        assertEquals("default", pick.second)
    }

    @Test
    fun `Mali without Vulkan uses MobileGlues`() {
        val pick = GraphicsTuning.recommendRenderer(profile(gpu = "Mali-G52", vulkan = false))
        assertNotNull(pick)
        assertEquals("MOBILEGLUES", pick!!.first)
    }

    @Test
    fun `unknown GPU without Vulkan gets no recommendation at all`() {
        // Better to leave a working setting alone than to guess.
        assertNull(GraphicsTuning.recommendRenderer(profile(gpu = "Vivante GC7000", vulkan = false)))
    }

    @Test
    fun `unavailable renderers are skipped instead of being recommended blindly`() {
        val pick = GraphicsTuning.recommendRenderer(
            profile(vulkan = true),
            isAvailable = { id -> id != "FREEDRENO" && id != "ZINK" }
        )
        // Adreno + Vulkan: Freedreno is preferred but missing from this APK, so
        // MobileGlues (which is installed) must still be suggested.
        assertEquals("MOBILEGLUES", pick!!.first)
    }

    @Test
    fun `renderers outside their Minecraft version range are skipped`() {
        val pick = GraphicsTuning.recommendRenderer(
            profile(minecraftVersion = "1.21.5", vulkan = true),
            inVersionRange = { id, _ -> id != "FREEDRENO" }
        )
        assertEquals("MOBILEGLUES", pick!!.first)
    }

    // ── Manual choices are respected per field ───────────────────────────────

    @Test
    fun `a pinned renderer still allows an automatic driver`() {
        val recommendation = GraphicsTuning.recommend(
            profile = profile(),
            currentRenderer = "hand-picked",
            currentDriver = "default",
            mayChangeRenderer = false,
            mayChangeDriver = true
        )
        assertNull(recommendation.rendererUniqueId)
        assertEquals("Turnip", recommendation.driver)
    }

    @Test
    fun `a pinned driver still allows an automatic renderer`() {
        val recommendation = GraphicsTuning.recommend(
            profile = profile(),
            currentRenderer = "default",
            currentDriver = "Turnip",
            mayChangeRenderer = true,
            mayChangeDriver = false
        )
        assertEquals("FREEDRENO", recommendation.rendererId)
        assertNull(recommendation.driver)
    }

    @Test
    fun `restoring defaults re-enables the automatic pick`() {
        // Everything untouched: nothing to recommend against.
        val recommendation = GraphicsTuning.recommend(
            profile = profile(),
            currentRenderer = "",
            currentDriver = "",
            mayChangeRenderer = true,
            mayChangeDriver = true
        )
        assertEquals("FREEDRENO", recommendation.rendererId)
    }

    // ── Resolution policy ────────────────────────────────────────────────────

    @Test
    fun `benchmark tiers split at the documented scores`() {
        assertEquals(GraphicsTuning.BenchTier.UNKNOWN, GraphicsTuning.benchTier(null))
        assertEquals(GraphicsTuning.BenchTier.LOW, GraphicsTuning.benchTier(149))
        assertEquals(GraphicsTuning.BenchTier.MID, GraphicsTuning.benchTier(150))
        assertEquals(GraphicsTuning.BenchTier.MID, GraphicsTuning.benchTier(599))
        assertEquals(GraphicsTuning.BenchTier.HIGH, GraphicsTuning.benchTier(600))
    }

    @Test
    fun `resolution follows the measured score, not RAM alone`() {
        val weak = GraphicsTuning.resolutionPercent(profile(score = 90, ramMb = 8192))
        val strong = GraphicsTuning.resolutionPercent(profile(score = 900, ramMb = 3072))
        assertTrue("a low benchmark score must reduce resolution", weak < strong)
        assertEquals(70, weak)
        assertEquals(100, strong)
    }

    @Test
    fun `resolution falls back to the RAM tier before the benchmark is run`() {
        assertEquals(70, GraphicsTuning.resolutionPercent(profile(score = null, ramMb = 2048)))
        assertEquals(100, GraphicsTuning.resolutionPercent(profile(score = null, ramMb = 8192)))
    }

    @Test
    fun `large or fast displays cost resolution`() {
        val base = GraphicsTuning.resolutionPercent(profile(score = 150, pixels = 1_000_000, refresh = 60))
        val big = GraphicsTuning.resolutionPercent(profile(score = 150, pixels = 4_000_000, refresh = 60))
        val fast = GraphicsTuning.resolutionPercent(profile(score = 150, pixels = 1_000_000, refresh = 120))
        assertTrue(big < base)
        assertTrue(fast < base)
    }

    @Test
    fun `thermal throttling and battery saver cap resolution`() {
        val hot = GraphicsTuning.resolutionPercent(profile(score = 900, severeThermal = true))
        val saving = GraphicsTuning.resolutionPercent(profile(score = 900, powerSaving = true))
        assertEquals(70, hot)
        assertEquals(60, saving)
    }

    @Test
    fun `resolution always stays inside the documented 60 to 100 percent range`() {
        val samples = listOf(
            profile(score = 0, pixels = 8_000_000, refresh = 144, severeThermal = true),
            profile(score = 5000, pixels = 1000, refresh = 30),
            profile(score = null, ramMb = 0)
        )
        samples.forEach { sample ->
            val percent = GraphicsTuning.resolutionPercent(sample)
            assertTrue("$percent out of range", percent in 60..100)
        }
    }

    // ── Version range handling ───────────────────────────────────────────────

    @Test
    fun `renderer catalog ranges are enforced`() {
        // Holy GL4ES is capped at 1.21.4 in RendererCatalog.
        assertFalse(GraphicsTuning.withinCatalogRange("GL4ES", "1.21.5"))
        assertTrue(GraphicsTuning.withinCatalogRange("GL4ES", "1.20.1"))
        // Unknown renderers and unknown/snapshot versions are allowed.
        assertTrue(GraphicsTuning.withinCatalogRange("NOT_A_RENDERER", "1.21.5"))
        assertTrue(GraphicsTuning.withinCatalogRange("GL4ES", ""))
        assertTrue(GraphicsTuning.withinCatalogRange("GL4ES", "24w09a"))
    }

    @Test
    fun `version parsing handles both naming schemes and rejects snapshots`() {
        assertEquals(listOf(1, 21, 5), GraphicsTuning.parseVersion("1.21.5"))
        assertEquals(listOf(26, 1, 2), GraphicsTuning.parseVersion("26.1.2"))
        assertEquals(listOf(1, 20), GraphicsTuning.parseVersion("1.20"))
        assertNull(GraphicsTuning.parseVersion("24w09a"))
        assertNull(GraphicsTuning.parseVersion(""))
    }

    @Test
    fun `version comparison is numeric, not lexicographic`() {
        assertTrue(GraphicsTuning.compare(listOf(1, 21, 5), listOf(1, 9, 0)) > 0)
        assertTrue(GraphicsTuning.compare(listOf(26, 1), listOf(26, 1, 0)) == 0)
        assertTrue(GraphicsTuning.compare(listOf(1, 20), listOf(1, 21)) < 0)
    }

    // ── Settings round-trip ──────────────────────────────────────────────────

    @Test
    fun `stored setting values map back to renderer ids`() {
        assertEquals("ZINK", GraphicsTuning.rendererIdOf("18d93f17-ff53-a319-fa61-58709a77bf87"))
        assertEquals("MOBILEGLUES", GraphicsTuning.rendererIdOf("4b4b8e4b-083d-429c-97e1-5e8239b6dc17"))
        assertNull(GraphicsTuning.rendererIdOf("not-a-real-identifier"))
        assertEquals(
            "18d93f17-ff53-a319-fa61-58709a77bf87",
            GraphicsTuning.uniqueIdOf("ZINK")
        )
    }

    // ── Throughput of the decision itself ────────────────────────────────────

    /**
     * The picker runs on every launch, on the main thread path that starts the game.
     * 20k full recommendations must finish well inside a frame budget; if someone
     * later adds I/O or reflection to the policy, this fails in CI instead of
     * making launches feel slow.
     */
    @Test
    fun `recommendation is cheap enough to run at every launch`() {
        val sample = profile(score = 320)
        val started = System.nanoTime()
        repeat(20_000) { index ->
            val recommendation = GraphicsTuning.recommend(
                profile = if (index % 2 == 0) sample else profile(gpu = "Mali-G52", vulkan = false),
                currentRenderer = "default",
                currentDriver = "default",
                mayChangeRenderer = true,
                mayChangeDriver = true
            )
            assertNotNull(recommendation.rendererId)
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("20k recommendations took ${elapsedMs}ms", elapsedMs < 2_000)
    }
}
