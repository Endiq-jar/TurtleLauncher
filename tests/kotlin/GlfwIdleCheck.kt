import org.lwjgl.glfw.GLFW
import java.lang.management.ManagementFactory
import kotlin.concurrent.thread

fun main() {
    GLFW.glfwPostEmptyEvent() // null waiter is legal
    check(GLFW.untouchedMethod("test") == "compat:test")
    check(GLFW.compatibilitySentinel == "preserved")
    GLFW.glfwWaitEventsTimeout(0.0)
    GLFW.glfwWaitEventsTimeout(-1.0)
    GLFW.glfwWaitEventsTimeout(Double.NaN)
    val before = GLFW.polls
    val start = System.nanoTime()
    GLFW.glfwWaitEventsTimeout(.004)
    check(System.nanoTime() - start >= 2_000_000) { "Timeout wait returned immediately" }
    check(GLFW.polls == before + 1) { "Wait must dispatch events after sleeping" }
    val bean = ManagementFactory.getThreadMXBean()
    if (bean.isThreadCpuTimeSupported) bean.isThreadCpuTimeEnabled = true
    val cpuStart = if (bean.isThreadCpuTimeSupported) bean.currentThreadCpuTime else -1
    val wallStart = System.nanoTime()
    repeat(30) { GLFW.glfwWaitEvents() }
    val elapsed = System.nanoTime() - wallStart
    val cpuElapsed = if (cpuStart >= 0) bean.currentThreadCpuTime - cpuStart else -1
    check(elapsed >= 100_000_000) { "Idle wait regressed to a spin loop" }
    if (cpuElapsed >= 0) check(cpuElapsed < elapsed / 2) { "Wait used CPU instead of parking" }
    // Empty events from another thread can release a park without changing other GLFW methods.
    val worker = thread { repeat(10) { GLFW.glfwWaitEvents() } }
    repeat(20) { GLFW.glfwPostEmptyEvent(); Thread.sleep(1) }
    worker.join(2000)
    check(!worker.isAlive)
    println("GLFW patched-bytecode verification passed; idle wall=${elapsed / 1_000_000}ms CPU=${cpuElapsed / 1_000_000}ms")
}
