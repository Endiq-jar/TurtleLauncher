package com.endiq.turtlelauncher.feature.discord

import org.junit.Assert.*
import org.junit.Test

class DiscordPresenceStateTest {
    @Test fun gameTakesPriorityAndSurvivesLauncherExit() {
        val state = DiscordPresenceState<String>()
        assertFalse(state.active)
        state.update("launcher", null)
        state.update("game", "1.21.1")
        state.update("launcher", null)
        assertEquals("1.21.1", state.version)
        assertEquals("game", state.owner)
        state.remove("launcher")
        assertTrue(state.active)
        assertEquals("1.21.1", state.version)
        assertEquals("game", state.owner)
    }

    @Test fun gameDeathReturnsToBrowsingAndLastClientClearsPresence() {
        val state = DiscordPresenceState<String>()
        state.update("launcher", null)
        state.update("game", "1.21.1")
        state.remove("game")
        state.remove("game") // normal detach followed by a death callback is harmless
        assertTrue(state.active)
        assertNull(state.version)
        assertEquals("launcher", state.owner)
        state.remove("launcher")
        assertFalse(state.active)
    }
}
