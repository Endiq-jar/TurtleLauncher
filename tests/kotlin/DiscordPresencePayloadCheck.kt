package com.endiq.turtlelauncher.feature.discord

fun main() {
    check(DiscordPresencePayload.text(" x ") == "") // SDK requires at least two characters
    check(DiscordPresencePayload.text("  Playing Minecraft  ") == "Playing Minecraft")
    val emoji = "\uD83D\uDC22".repeat(200)
    val bounded = DiscordPresencePayload.text(emoji)
    check(bounded.codePointCount(0, bounded.length) == 128)
    check(bounded.length == 256 && bounded.last().isLowSurrogate())
    check(DiscordPresencePayload.buttonUrl("https://github.com/Endiq-jar/TurtleLauncher") != null)
    check(DiscordPresencePayload.buttonUrl("javascript:alert(1)") == null)
    check(DiscordPresencePayload.buttonUrl("https://") == null)
    check(DiscordPresencePayload.buttonUrl("https://example.com/" + "x".repeat(512)) == null)
    println("Discord SDK text limits, Unicode boundaries and button URL checks passed")
}
