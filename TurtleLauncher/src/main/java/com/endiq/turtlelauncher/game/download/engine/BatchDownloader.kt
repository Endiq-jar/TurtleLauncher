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

import com.endiq.turtlelauncher.game.download.engine.BatchDownloader.Companion.PROGRESS_INTERVAL_MS
import com.endiq.turtlelauncher.game.download.engine.BatchDownloader.Companion.SYSTEMIC_FAILURE_LIMIT
import com.endiq.turtlelauncher.utils.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

/** Files still unsuccessful after the batch finishes (and not accepted by [BatchDownloader.onFailureFilter]) */
class BatchDownloadException(summary: String, cause: Throwable? = null) : IOException(summary, cause)

/**
 * Batch download orchestrator: file-level concurrency is semaphore-controlled, with at most [maxConnections] files transferring at once;
 * per-file retries and source switching are absorbed by the engine; files exhausting all candidates join the next whole-batch retry round.
 * Systemic-failure circuit breaker: with zero batch successes, [SYSTEMIC_FAILURE_LIMIT] consecutive permanent file failures cancel the rest and fail immediately,
 * avoiding tens of thousands of hopeless retries on deterministic faults (e.g. engine or source misconfiguration).
 * File-level stats are registered before run() (callers may pre-register locally reused files),
 * so [run] may be called at most once per instance.
 */
class BatchDownloader(
    private val requests: List<DownloadRequest>,
    private val maxConnections: Int = DEFAULT_MAX_CONNECTIONS,
    private val retryRounds: Int = 1
) {
    val stats = DownloadStats()

    /** Receives one progress snapshot every [PROGRESS_INTERVAL_MS]; the callback runs on the scheduler thread and should only forward lightly */
    var onUpdate: (suspend (BatchProgress) -> Unit)? = null

    var onFileSuccess: (suspend (DownloadRequest) -> Unit)? = null

    /**
     * Verdict for files still failing after all retry rounds: true accepts the state and continues
     * (e.g. optional extra content); false adds them to the final failure set.
     */
    var onFailureFilter: ((DownloadRequest, Throwable) -> Boolean)? = null

    private val files = Semaphore(maxConnections)

    /** Consecutive permanent-failure counter, reset by any file success; together with the zero-success condition it detects systemic failure */
    private val systemicFailureCount = AtomicInteger(0)

    /** Circuit-breaker reason for systemic failure; null means not triggered */
    private val systemicAbortCause = AtomicReference<Throwable?>(null)

    /** In-flight file jobs of the batch, cancelled one by one on breaker trip (walking the child-task list races against fast completion-detach) */
    private val activeFileJobs = AtomicReference<List<Job>?>(null)

    /** Failure list of the latest run (target file path, exception), for caller diagnostics */
    var lastRunFailures: Map<String, Throwable> = emptyMap()
        private set

    suspend fun run() {
        // No resetting here
        // the caller may have registered locally reused files into stats before run()
        requests.forEach { stats.registerFile(it.expectedSize) }
        systemicFailureCount.set(0)
        systemicAbortCause.set(null)

        val failures = ConcurrentHashMap<String, Throwable>()
        coroutineScope {
            val reporter = launch(Dispatchers.Default) {
                while (isActive) {
                    delay(PROGRESS_INTERVAL_MS.milliseconds)
                    onUpdate?.invoke(stats.snapshotProgress())
                }
            }

            try {
                val fileJobs = requests.map { request ->
                    launch(Dispatchers.IO) {
                        //Take a file permit before opening the temp file:
                        //otherwise all jobs queue up holding open handles, and the flood of handles overwhelms the storage layer
                        files.withPermit {
                            runOne(request, failures)
                        }
                    }
                }
                activeFileJobs.set(fileJobs)
                fileJobs.joinAll()
            } finally {
                activeFileJobs.set(null)
                reporter.cancelAndJoin()
            }
        }

        systemicAbortCause.get()?.let { cause ->
            lastRunFailures = failures.toMap()
            throw BatchDownloadException(
                "Batch aborted after $SYSTEMIC_FAILURE_LIMIT consecutive permanent failures " +
                        "with no successful download; the failure looks systemic rather than per-file",
                cause
            )
        }

        val finished = stats.downloadedFiles
        if (finished != requests.size) {
            val failedCount = failures.size
            val outcome = if (failedCount == 0) "all" else "$failedCount failed"
            Logger.warning(TAG, "Completed-count mismatch: requests=" + requests.size
                    + " finished=" + finished + " failures=" + outcome)
        }

        if (failures.isNotEmpty()) {
            lastRunFailures = failures.toMap()
            val detail = failures.entries.joinToString(separator = "\n") { (path, error) ->
                "$path: ${error.message ?: error::class.simpleName}"
            }
            //With thousands of files failing, details would flood the log; keep only the first few — the full list stays in lastRunFailures
            val summaryLines = detail.lines()
            val summary = if (summaryLines.size > MAX_FAILURE_DETAIL_LINES) {
                summaryLines.take(MAX_FAILURE_DETAIL_LINES).joinToString("\n") +
                        "\n... and ${summaryLines.size - MAX_FAILURE_DETAIL_LINES} more lines"
            } else {
                detail
            }
            throw BatchDownloadException(summary)
        }
    }

    private suspend fun runOne(
        request: DownloadRequest,
        failures: MutableMap<String, Throwable>
    ) {
        var lastError: Throwable? = null
        repeat(retryRounds + 1) {
            try {
                FileDownloader(request, stats).download()
                onFileSuccess?.invoke(request)
                stats.markFileFinished()
                systemicFailureCount.set(0)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        lastError?.let { error ->
            Logger.error(TAG, "Download failed permanently: ${request.targetFile.absolutePath}", error)
            if (onFailureFilter?.invoke(request, error) == true) {
                stats.markFileFinished()
                systemicFailureCount.set(0)
                return
            }
            failures[request.targetFile.absolutePath] = error

            //Zero batch successes plus consecutive permanent failures: judged a systemic failure,
            //grinding through the rest only spawns futile retries; cancel the whole batch at once
            if (stats.downloadedFiles == 0) {
                val count = systemicFailureCount.incrementAndGet()
                if (count >= SYSTEMIC_FAILURE_LIMIT && systemicAbortCause.compareAndSet(null, error)) {
                    Logger.warning(TAG, "Aborting batch: $count consecutive permanent failures with zero successful downloads", error)
                    activeFileJobs.get()?.forEach { it.cancel() }
                }
            }
        }
    }

    companion object {
        private const val TAG = "BatchDownloader"
        const val DEFAULT_MAX_CONNECTIONS = 64
        const val PROGRESS_INTERVAL_MS = 100L

        /** Max failures listed in the exception detail */
        private const val MAX_FAILURE_DETAIL_LINES = 20

        /** With zero batch successes, this many consecutive permanent failures means systemic failure; abort the batch */
        const val SYSTEMIC_FAILURE_LIMIT = 5
    }
}
