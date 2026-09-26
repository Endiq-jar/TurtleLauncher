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
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

/**
 * The engine's single-file convenience entry, suited to small ad-hoc jobs; use [BatchDownloader] for large batches.
 */
object DownloadEngine {

    /**
     * @param sizeCallback incremental callback of bytes written; retries and source switches are absorbed by the engine, so callers only see positive deltas
     */
    suspend fun download(
        request: DownloadRequest,
        stats: DownloadStats = DownloadStats(),
        sizeCallback: (Long) -> Unit = {}
    ) {
        if (request.expectedSize > 0) stats.registerFile(request.expectedSize)

        if (sizeCallback === defaultCallback) {
            FileDownloader(request, stats).download()
            stats.markFileFinished()
            return
        }

        val reported = AtomicLong(0L)
        coroutineScope {
            val reporter = launch(Dispatchers.Default) {
                while (isActive) {
                    delay(PROGRESS_SAMPLE_MS.milliseconds)
                    drain(reported, stats, sizeCallback)
                }
            }
            try {
                FileDownloader(request, stats).download()
                stats.markFileFinished()
                drain(reported, stats, sizeCallback)
            } finally {
                reporter.cancelAndJoin()
            }
        }
    }

    private fun drain(reported: AtomicLong, stats: DownloadStats, callback: (Long) -> Unit) {
        val total = stats.downloadedBytes
        val delta = total - reported.get()
        if (delta > 0) {
            reported.addAndGet(delta)
            callback(delta)
        }
    }

    private val defaultCallback: (Long) -> Unit = {}

    private const val PROGRESS_SAMPLE_MS = 100L
}
