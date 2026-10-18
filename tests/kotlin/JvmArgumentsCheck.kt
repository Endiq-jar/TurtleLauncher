import com.endiq.turtlelauncher.launch.JvmArguments
import com.endiq.turtlelauncher.launch.FrameBudget

fun checkJvmArguments() {
    check(JvmArguments.parse("-Xmx2G-XX:+UseG1GC-Dfoo=bar") == listOf("-Xmx2G", "-XX:+UseG1GC", "-Dfoo=bar"))
    val quoted = "-Dname=\"hello world\" -javaagent:\"/games/my agent.jar\" -XX:OnOutOfMemoryError=\"echo -Xmx128m\""
    val expected = listOf("-Dname=hello world", "-javaagent:/games/my agent.jar", "-XX:OnOutOfMemoryError=echo -Xmx128m")
    check(JvmArguments.parse(quoted) == expected)
    check(JvmArguments.parse(JvmArguments.merge(quoted, "")) == expected)
    check(JvmArguments.parse("-Durl=https://host/-Xfile -Dsetting=a=b") == listOf("-Durl=https://host/-Xfile", "-Dsetting=a=b"))
    check(JvmArguments.parse("-Dpath='C:\\Games\\My Pack' ").single() == "-Dpath=C:\\Games\\My Pack")
    check(runCatching { JvmArguments.parse("-Dname=\"unfinished") }.isFailure)
    check(JvmArguments.usesG1(""))
    check(!JvmArguments.usesG1("-XX:+UseSerialGC"))
    check(!JvmArguments.usesG1("-XX:-UseG1GC"))
    check(!JvmArguments.usesG1("-XX:+UseG1GC -XX:-UseG1GC"))
    check(JvmArguments.usesG1("-XX:-UseG1GC -XX:+UseG1GC"))
    val serial = JvmArguments.parse(JvmArguments.merge("-XX:+UseSerialGC -XX:MaxGCPauseMillis=80", "-XX:+UseG1GC -XX:MaxGCPauseMillis=50"))
    check("-XX:+UseSerialGC" in serial && "-XX:+UseG1GC" !in serial)
    check("-XX:MaxGCPauseMillis=80" in serial && "-XX:MaxGCPauseMillis=50" !in serial)
    val disable = JvmArguments.parse(JvmArguments.merge("-XX:-UseStringDeduplication", "-XX:+UseStringDeduplication"))
    check(disable == listOf("-XX:-UseStringDeduplication"))
    val java = java.io.File(System.getProperty("java.home"), "bin/java").absolutePath
    val vm = ProcessBuilder(listOf(java) + serial + "-version").redirectErrorStream(true).start()
    val output = vm.inputStream.bufferedReader().readText()
    check(vm.waitFor() == 0) { output }
    check(FrameBudget.remainingNanos(0, 2_000_000, 60) in 14_000_000L..15_000_000L)
    check(FrameBudget.remainingNanos(0, 50_000_000, 60) == 0L)
    check(runCatching { FrameBudget.remainingNanos(0, 0, 0) }.isFailure)
    println("JVM quoting, explicit GC precedence, real JVM startup and frame-budget checks passed")
}
