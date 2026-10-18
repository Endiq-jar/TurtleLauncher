package com.endiq.turtlelauncher.feature.turtle

import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

/** Opt-in TCP connection latency, not Minecraft protocol latency. Owned by the visible HUD. */
object PingTracker {
    private const val TIMEOUT_MS = 1500
    private const val INTERVAL_MS = 5000L
    private val lock = Object()
    private val lastPingMs = AtomicInteger(-1)
    @Volatile private var currentTarget: ServerEndpoint? = null
    @Volatile private var worker: Thread? = null
    @Volatile private var activeSocket: Socket? = null

    @JvmStatic
    fun setTarget(address: String?): Boolean {
        val target = if (address.isNullOrBlank()) null else ServerEndpoint.parse(address) ?: return false
        synchronized(lock) {
            currentTarget = target
            lastPingMs.set(-1)
            runCatching { activeSocket?.close() }
            lock.notifyAll()
        }
        return true
    }

    @JvmStatic
    fun getTargetAddress(): String = currentTarget?.address.orEmpty()

    @JvmStatic
    fun start() {
        synchronized(lock) {
            if (worker != null) return
            val thread = Thread({
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                val self = Thread.currentThread()
                try {
                    while (worker === self) {
                        val target = synchronized(lock) {
                            while (currentTarget == null && worker === self) lock.wait()
                            currentTarget
                        } ?: continue
                        if (worker !== self) break
                        val measured = measure(target, self)
                        if (worker === self && currentTarget == target) lastPingMs.set(measured)
                        synchronized(lock) {
                            if (worker === self && currentTarget == target) lock.wait(INTERVAL_MS)
                        }
                    }
                } catch (_: InterruptedException) {
                    // HUD detached or Activity paused.
                }
            }, "TurtlePingTracker").apply { isDaemon = true }
            worker = thread
            thread.start()
        }
    }

    @JvmStatic
    fun stop() {
        synchronized(lock) {
            val previous = worker
            worker = null
            previous?.interrupt()
            runCatching { activeSocket?.close() }
            activeSocket = null
            lock.notifyAll()
        }
    }

    @JvmStatic
    fun getPingMs(): Int = lastPingMs.get()

    private fun measure(target: ServerEndpoint, owner: Thread): Int {
        val socket = Socket()
        return try {
            synchronized(lock) {
                if (worker !== owner || currentTarget != target) return -1
                activeSocket = socket
            }
            // DNS lookup is deliberately outside the UI thread.
            val endpoint = InetSocketAddress(target.host, target.port)
            val start = System.nanoTime()
            socket.connect(endpoint, TIMEOUT_MS)
            ((System.nanoTime() - start) / 1_000_000L).toInt()
        } catch (_: Exception) {
            -1
        } finally {
            runCatching { socket.close() }
            synchronized(lock) { if (activeSocket === socket) activeSocket = null }
        }
    }
}
