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

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Read-only progress snapshot of a download batch, consumed by the UI layer */
data class BatchProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val downloadedFiles: Int,
    val totalFiles: Int,
    val speedBytesPerSec: Long
)

/**
 * Cross-thread byte/file counter with embedded per-second block speed sampling:
 * each reported value is the real number of bytes written to disk during the last complete second, updated once per second,
 * with no cross-window smoothing or extrapolation; reported numbers always correspond to actual traffic.
 * The hot path is a single atomic addition.
 * All file/byte counters are atomic, so no counts are lost even when as many threads as concurrent connections finish at once.
 */
class DownloadStats {
    private val downloaded = AtomicLong(0L)
    private val totalBytesCounter = AtomicLong(-1L)
    private val totalFilesCounter = AtomicInteger(0)

    private val downloadedFilesCounter = AtomicInteger(0)
    val expectedTotalBytes: Long get() = totalBytesCounter.get()

    val totalFiles: Int get() = totalFilesCounter.get()
    val downloadedFiles: Int get() = downloadedFilesCounter.get()

    fun addBytes(count: Long) {
        downloaded.addAndGet(count)
    }

    fun registerFile(expectedSize: Long) {
        totalFilesCounter.incrementAndGet()
        if (expectedSize > 0) {
            totalBytesCounter.accumulateAndGet(expectedSize) { previous, add ->
                if (previous < 0) add else previous + add
            }
        }
    }

    fun markFileFinished() {
        downloadedFilesCounter.incrementAndGet()
    }

    /**
     * Fold the bytes of locally reused files into the downloaded amount
     */
    fun resetSpeedBaseline() {
        synchronized(this) {
            blockStartNanos = System.nanoTime()
            blockStartBytes = downloaded.get()
        }
    }

    val downloadedBytes: Long get() = downloaded.get()

    /**
     * Returns the real average throughput of the last complete sampling second
     */
    fun refreshSpeed(): Long {
        val now = System.nanoTime()
        synchronized(this) {
            val elapsed = now - blockStartNanos
            if (elapsed < SAMPLE_INTERVAL_NANOS) return currentSpeed

            val bytes = downloaded.get()
            currentSpeed = ((bytes - blockStartBytes) * NANOS_PER_SEC / elapsed)
                .coerceAtLeast(0L)
            blockStartNanos = now
            blockStartBytes = bytes
            return currentSpeed
        }
    }

    /** Refreshes speed sampling before producing a snapshot, so callers need no separate sampling trigger */
    fun snapshotProgress(): BatchProgress = BatchProgress(
        downloadedBytes = downloaded.get(),
        totalBytes = expectedTotalBytes,
        downloadedFiles = downloadedFiles,
        totalFiles = totalFiles,
        speedBytesPerSec = refreshSpeed()
    )

    private var blockStartNanos = System.nanoTime()
    private var blockStartBytes = 0L

    private var currentSpeed: Long = 0L

    companion object {
        /** Speed sampling period */
        private const val SAMPLE_INTERVAL_NANOS = 1_000_000_000L
        private const val NANOS_PER_SEC = 1_000_000_000L
    }
}
