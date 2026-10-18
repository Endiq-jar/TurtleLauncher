import com.endiq.turtlelauncher.feature.turtle.PingTracker
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

fun checkPingTracker() {
    val accepted = AtomicInteger()
    val server = ServerSocket(0)
    val acceptor = thread(isDaemon = true, name = "test-ping-server") {
        try {
            while (!server.isClosed) server.accept().use { accepted.incrementAndGet() }
        } catch (_: Exception) { /* fixture closed */ }
    }
    try {
        check(PingTracker.setTarget("127.0.0.1:${server.localPort}"))
        check(!PingTracker.setTarget("https://invalid"))
        check(PingTracker.getTargetAddress() == "127.0.0.1:${server.localPort}")
        repeat(20) { PingTracker.start(); PingTracker.stop() }
        PingTracker.start()
        PingTracker.start() // idempotent, not a second probe worker
        val deadline = System.nanoTime() + 3_000_000_000L
        while (PingTracker.getPingMs() < 0 && System.nanoTime() < deadline) Thread.sleep(10)
        check(PingTracker.getPingMs() >= 0) { "Local TCP probe did not finish" }
        check(accepted.get() > 0)
        PingTracker.stop()
        check(PingTracker.setTarget(""))
        check(PingTracker.getPingMs() == -1)
        PingTracker.start()
        Thread.sleep(100)
        val dormant = Thread.getAllStackTraces().keys.filter { it.name == "TurtlePingTracker" && it.isAlive }
        check(dormant.size == 1) { "Duplicate ping workers: ${dormant.size}" }
        check(dormant.single().state == Thread.State.WAITING) { "No-target worker should sleep without polling" }
        PingTracker.stop()
        dormant.single().join(2000)
        check(!dormant.single().isAlive) { "Probe worker survived stop" }
        println("Ping worker start/stop, live TCP probe and idle-sleep checks passed")
    } finally {
        PingTracker.stop()
        server.close()
        acceptor.join(2000)
    }
}
