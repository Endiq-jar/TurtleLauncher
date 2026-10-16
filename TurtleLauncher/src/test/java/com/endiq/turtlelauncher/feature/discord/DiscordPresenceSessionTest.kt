package com.endiq.turtlelauncher.feature.discord

import org.junit.Assert.*
import org.junit.Test

class DiscordPresenceSessionTest {
    @Test fun refreshAndSdkReinitializationKeepTheSameGameClock() {
        val session = DiscordPresenceSession()
        session.start("1.21.1", 1000L)
        session.start("1.21.1", 2000L)
        assertEquals(1000L, session.startedAt)
        assertEquals("1.21.1", session.version)
    }

    @Test fun newActivitiesAndClosedLeasesGetNewClocks() {
        val session = DiscordPresenceSession()
        session.start("1.21.1", 1000L)
        session.start(null, 2000L)
        assertEquals(2000L, session.startedAt)
        session.start(null, 3000L)
        assertEquals(2000L, session.startedAt)
        session.reset()
        assertEquals(0L, session.startedAt)
        session.start(null, 4000L)
        assertEquals(4000L, session.startedAt)
    }
}
