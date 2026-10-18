package com.endiq.turtlelauncher.feature.turtle

/** Fast input feedback is independent from expensive, once-a-second telemetry. */
object HudRefreshPolicy {
    fun intervalMs(requestedMs: Long, hasInputWidgets: Boolean): Long =
        if (hasInputWidgets) requestedMs.coerceIn(50L, 1000L)
        else requestedMs.coerceAtLeast(1000L)
}
