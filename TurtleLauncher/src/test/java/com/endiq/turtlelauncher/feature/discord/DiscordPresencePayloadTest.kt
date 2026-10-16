package com.endiq.turtlelauncher.feature.discord

import org.junit.Assert.*
import org.junit.Test

class DiscordPresencePayloadTest {
    @Test fun enforcesSdkTextBoundsWithoutSplittingEmoji() {
        assertEquals("", DiscordPresencePayload.text("x"))
        val bounded = DiscordPresencePayload.text("\uD83D\uDC22".repeat(200))
        assertEquals(128, bounded.codePointCount(0, bounded.length))
        assertTrue(bounded.last().isLowSurrogate())
    }
    @Test fun invalidButtonsCannotInvalidateTheWholePresence() {
        assertNull(DiscordPresencePayload.buttonUrl("javascript:alert(1)"))
        assertNull(DiscordPresencePayload.buttonUrl("https://"))
        assertNull(DiscordPresencePayload.buttonUrl("https://example.com/" + "x".repeat(512)))
        assertNotNull(DiscordPresencePayload.buttonUrl("https://github.com/Endiq-jar/TurtleLauncher"))
    }
}
