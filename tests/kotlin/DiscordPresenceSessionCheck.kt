package com.endiq.turtlelauncher.feature.discord

fun main() {
    val session = DiscordPresenceSession()
    session.start("1.21.1", 1000L)
    session.start("1.21.1", 2000L)
    check(session.startedAt == 1000L)
    session.start(null, 3000L)
    check(session.startedAt == 3000L && session.version == null)
    session.start(null, 4000L)
    check(session.startedAt == 3000L)
    session.reset()
    check(session.startedAt == 0L)
    session.start(null, 5000L)
    check(session.startedAt == 5000L)
    println("Discord logical session refresh, transition and reset checks passed")
}
