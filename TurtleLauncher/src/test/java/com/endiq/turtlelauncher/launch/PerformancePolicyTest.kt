package com.endiq.turtlelauncher.launch

import com.endiq.turtlelauncher.feature.turtle.HudRefreshPolicy
import com.endiq.turtlelauncher.feature.turtle.ServerEndpoint
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class PerformancePolicyTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun autoTuningPreservesManualChanges() {
        assertTrue(PerformancePolicy.mayAutoTune("100", null, "100"))
        assertTrue(PerformancePolicy.mayAutoTune("80", "80", "100"))
        assertFalse(PerformancePolicy.mayAutoTune("90", "80", "100"))
        assertEquals(70, PerformancePolicy.resolutionPercent(8192, true))
        assertFalse(PerformancePolicy.allowUncapped(true, 2, false))
        assertFalse(PerformancePolicy.allowUncapped(true, 0, true))
        assertTrue(PerformancePolicy.allowUncapped(true, 0, false))
    }

    @Test fun pollingIsBoundedAndEndpointsAreValidated() {
        assertEquals(50L, HudRefreshPolicy.intervalMs(0, true))
        assertEquals(1000L, HudRefreshPolicy.intervalMs(10, false))
        assertEquals(ServerEndpoint("::1", 25565), ServerEndpoint.parse("[::1]:25565"))
        assertNull(ServerEndpoint.parse("https://invalid"))
        assertNull(ServerEndpoint.parse("host:65536"))
    }

    @Test fun explicitGcAndQuotedArgumentsSurviveLaunch() {
        val merged = JvmArguments.merge(
            "-XX:+UseSerialGC -XX:MaxGCPauseMillis=80 -Dname=\"my pack\"",
            "-XX:+UseG1GC -XX:MaxGCPauseMillis=50"
        )
        val args = JvmArguments.parse(merged)
        assertTrue("-XX:+UseSerialGC" in args)
        assertFalse("-XX:+UseG1GC" in args)
        assertTrue("-XX:MaxGCPauseMillis=80" in args)
        assertTrue("-Dname=my pack" in args)
        assertEquals(0L, FrameBudget.remainingNanos(0, 50_000_000, 60))
    }

    @Test fun fpsRollbackPreservesPlayerEdits() {
        val game = directory.newFolder()
        val options = java.io.File(game, "options.txt")
        val original = "maxFps:120\nenableVsync:true\nrenderDistance:8\n"
        options.writeText(original)
        GameFpsOptions.apply(game, true)
        GameFpsOptions.apply(game, true)
        GameFpsOptions.apply(game, false)
        assertEquals(original, options.readText())
        GameFpsOptions.apply(game, true)
        options.writeText(options.readText().replace("maxFps:260", "maxFps:144"))
        GameFpsOptions.apply(game, false)
        assertTrue(options.readText().contains("maxFps:144"))
        assertTrue(options.readText().contains("enableVsync:true"))
        assertTrue(java.io.File(game, ".turtle-fps-options.properties").isFile)
    }
}
