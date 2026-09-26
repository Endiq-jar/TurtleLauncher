/*
 * Turtle Launcher
 * Copyright (C) 2025 Endiq and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.endiq.turtlelauncher.game.download.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Count conservation regression: markFileFinished/registerFile used to do non-atomic increments on volatile fields,
 * so 64 concurrent completions lost updates (2000 successes counted as 1992, triggering the "finished-count conservation" alert).
 */
class DownloadStatsTest {

    @Test
    fun `concurrent counters conserve every increment`() = runBlocking<Unit> {
        val stats = DownloadStats()
        val workers = 64
        val perWorker = 40

        coroutineScope {
            repeat(workers) {
                launch(Dispatchers.Default) {
                    repeat(perWorker) {
                        stats.markFileFinished()
                        stats.registerFile(10L)
                    }
                }
            }
        }

        assertEquals(workers * perWorker, stats.downloadedFiles)
        assertEquals(workers * perWorker, stats.totalFiles)
        assertEquals(workers.toLong() * perWorker * 10L, stats.expectedTotalBytes)
    }

    @Test
    fun `seeded bytes stay out of the speed meter after baseline reset`() = runBlocking<Unit> {
        val stats = DownloadStats()
        stats.registerFile(1_000L)
        stats.markFileFinished()

        //Reused-file bytes are folded into progress at once, then the speed baseline is reset
        stats.addBytes(1_000L)
        stats.resetSpeedBaseline()

        delay(1_100)
        assertEquals(0L, stats.refreshSpeed())
        assertEquals(1_000L, stats.downloadedBytes)
        assertEquals(1_000L, stats.expectedTotalBytes)
    }
}
