import com.endiq.turtlelauncher.feature.turtle.HudRefreshPolicy
import com.endiq.turtlelauncher.feature.turtle.ServerEndpoint
import com.endiq.turtlelauncher.launch.GameFpsOptions
import com.endiq.turtlelauncher.launch.PerformancePolicy
import java.nio.file.Files
import java.io.File

fun main() {
    checkPingTracker()
    checkJvmArguments()
    check(HudRefreshPolicy.intervalMs(0, true) == 50L)
    check(HudRefreshPolicy.intervalMs(-10, false) == 1000L)
    check(HudRefreshPolicy.intervalMs(100, true) == 100L)
    check(HudRefreshPolicy.intervalMs(5000, true) == 1000L)
    check(HudRefreshPolicy.intervalMs(5000, false) == 5000L)
    check(ServerEndpoint.parse("example.org") == ServerEndpoint("example.org", 25565))
    check(ServerEndpoint.parse("example.org:25566")?.port == 25566)
    check(ServerEndpoint.parse("[2001:db8::1]:25565")?.host == "2001:db8::1")
    check(ServerEndpoint.parse("[::1]")?.address == "[::1]:25565")
    listOf("", "https://example.org", "a:0", "a:65536", "a:", "a:-1", "a:x", "::1", "[::1]oops", "[xyz]:12", "host name").forEach {
        check(ServerEndpoint.parse(it) == null) { "Accepted invalid endpoint: $it" }
    }
    check(PerformancePolicy.mayAutoTune("100", null, "100"))
    check(!PerformancePolicy.mayAutoTune("90", "80", "100"))
    check(PerformancePolicy.mayAutoTune("80", "80", "100"))
    check(PerformancePolicy.resolutionPercent(2048, false) == 80)
    check(PerformancePolicy.resolutionPercent(8192, true) == 70)
    check(PerformancePolicy.resolutionPercent(8192, false) == 100)
    check(PerformancePolicy.resolutionPercent(0, false) == 100)
    check(PerformancePolicy.gcPauseTargetMs(true) == 50)
    check(PerformancePolicy.gcPauseTargetMs(false) == 100)
    check(!PerformancePolicy.allowUncapped(true, 2, false))
    check(!PerformancePolicy.allowUncapped(true, 0, true))
    check(!PerformancePolicy.allowUncapped(false, 0, false))
    check(PerformancePolicy.allowUncapped(true, 1, false))

    val dir = Files.createTempDirectory("turtle-fps-test").toFile()
    try {
        val options = File(dir, "options.txt")
        val original = "maxFps:120\nenableVsync:true\nrenderDistance:8\nresourcePacks:[\"file/test:pack.zip\"]\n"
        options.writeText(original)
        GameFpsOptions.apply(dir, true)
        check("maxFps:260" in options.readText())
        check("enableVsync:false" in options.readText())
        GameFpsOptions.apply(dir, true) // don't back up our own override on repeated launches
        GameFpsOptions.apply(dir, false)
        check(options.readText() == original)
        check(File(dir, ".turtle-fps-options.properties").isFile) // no backup deletion
        GameFpsOptions.apply(dir, false) // idempotent
        check(options.readText() == original)
        GameFpsOptions.apply(dir, true)
        options.writeText(options.readText().replace("maxFps:260", "maxFps:144"))
        GameFpsOptions.apply(dir, false)
        check("maxFps:144" in options.readText()) // preserve subsequent player changes
        check("enableVsync:true" in options.readText())
        options.writeText("renderDistance:12\n")
        GameFpsOptions.apply(dir, true)
        GameFpsOptions.apply(dir, false)
        check(options.readText() == "renderDistance:12\n") // originally missing options
        // Never guess rollback data for FPS options edited by older launcher builds.
        File(dir, ".turtle-fps-options.properties").writeText("")
        options.writeText("maxFps:260\nenableVsync:false\n")
        GameFpsOptions.apply(dir, false)
        check(options.readText() == "maxFps:260\nenableVsync:false\n")
    } finally {
        dir.deleteRecursively() // isolated test fixture only
    }
    println("Launcher performance, endpoint parsing and reversible FPS options checks passed")
}
