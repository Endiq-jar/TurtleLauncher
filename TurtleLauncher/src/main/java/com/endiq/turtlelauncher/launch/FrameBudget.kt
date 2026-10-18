package com.endiq.turtlelauncher.launch

/** No busy-spin or catch-up bursts: slow frames get no additional delay. */
object FrameBudget {
    @JvmStatic
    fun remainingNanos(started: Long, now: Long, fps: Int): Long {
        require(fps > 0)
        return (1_000_000_000L / fps - (now - started).coerceAtLeast(0L)).coerceAtLeast(0L)
    }
}
