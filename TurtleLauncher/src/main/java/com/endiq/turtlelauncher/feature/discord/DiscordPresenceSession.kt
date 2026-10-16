package com.endiq.turtlelauncher.feature.discord

/** Logical activity timing is independent of native SDK/Activity recreation. Actor-thread only. */
internal class DiscordPresenceSession {
    var version: String? = null
        private set
    var startedAt: Long = 0L
        private set

    fun start(version: String?, now: Long) {
        if (this.version != version || startedAt == 0L) startedAt = now
        this.version = version
    }

    fun reset() {
        version = null
        startedAt = 0L
    }
}
