package com.endiq.turtlelauncher.feature.discord

/** JVM-only regression checks; no credentials, Discord connection or Android runtime required. */
fun main() {
    val state = DiscordPresenceState<String>()
    check(!state.active && state.version == null)
    state.update("launcher", null)
    check(state.active && state.version == null)
    check(state.owner == "launcher")
    state.update("game", "1.21.1")
    check(state.version == "1.21.1")
    check(state.owner == "game")
    state.update("launcher", null) // opening settings must not replace the running game
    check(state.version == "1.21.1")
    check(state.owner == "game")
    state.remove("launcher") // Quit Launcher must not stop Minecraft's presence
    check(state.active && state.version == "1.21.1")
    state.update("launcher", null)
    state.remove("game") // normal exit / killed game process restores browsing
    check(state.active && state.version == null)
    state.remove("game") // duplicate binder-death / detach is harmless
    state.remove("launcher")
    check(!state.active)
    state.update("game", "")
    check(state.active && state.version == null)
    println("Discord presence ownership/state checks passed")
}
