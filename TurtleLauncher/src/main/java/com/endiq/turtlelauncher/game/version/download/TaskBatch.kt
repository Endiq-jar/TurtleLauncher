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

package com.endiq.turtlelauncher.game.version.download

import com.endiq.turtlelauncher.coroutine.Task
import com.endiq.turtlelauncher.game.download.engine.BatchDownloader
import com.endiq.turtlelauncher.game.download.engine.BatchProgress
import com.endiq.turtlelauncher.utils.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

private const val TAG = "TaskBatch"

/** Concurrency for verifying locally existing files */
private const val LOCAL_VERIFY_PARALLELISM = 4

/**
 * Unified entry point that hands a batch of [DownloadTask]s to the download engine:
 * files that can be reused locally are picked first (counted as completed immediately, with their follow-up tasks run),
 * the rest are downloaded concurrently by the engine in chunks; if any still fail, a [DownloadFailedException] is thrown
 * whose message contains the failed file path.
 *
 * @param onSnapshot receives an engine statistics snapshot every 100 ms
 */
suspend fun Task.runBatchDownloads(
    tasks: List<DownloadTask>,
    maxConnections: Int,
    retryRounds: Int = 1,
    onSnapshot: suspend (BatchProgress) -> Unit = {},
    acceptFailure: ((task: DownloadTask, error: Throwable) -> Boolean)? = null
) {
    val verifyStarted = System.currentTimeMillis()
    val (reusable, pending) = verifyExistingFilesConcurrently(tasks)
    Logger.info(TAG, "Local file check done: reusable=${reusable.size} pending=${pending.size}, took=${System.currentTimeMillis() - verifyStarted}ms")

    //If any file in the manifest has an undeclared size, bytes cannot form a reliable progress denominator
    val sizesFullyKnown = tasks.all { it.size > 0 }

    val batch = BatchDownloader(
        requests = pending.map { it.toRequest() },
        maxConnections = maxConnections,
        retryRounds = retryRounds
    )

    reusable.forEach {
        batch.stats.registerFile(it.size)
        batch.stats.markFileFinished()
        it.runFileDownloadedTask()
    }

    //Reused-file bytes are folded into the "downloaded" figure so the progress bar starts from the completed portion
    if (reusable.isNotEmpty()) {
        batch.stats.addBytes(reusable.sumOf { maxOf(it.size, 0L) })
        //Then reset the speed baseline so the one-off folded bytes are not reported as instantaneous rate
        batch.stats.resetSpeedBaseline()
    }

    batch.onUpdate = { snapshot ->
        updateProgress(progressFor(snapshot, sizesFullyKnown, hasFileCount = snapshot.totalFiles > 0))
        onSnapshot(snapshot)
    }
    batch.onFileSuccess = { request ->
        (request.tag as? DownloadTask)?.runFileDownloadedTask()
    }
    acceptFailure?.let { judge ->
        batch.onFailureFilter = { request, error ->
            (request.tag as? DownloadTask)?.let { judge(it, error) } ?: false
        }
    }

    try {
        batch.run()
        updateProgress(1f)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val failedDetail = batch.lastRunFailures.keys.joinToString("\n") { path -> path }
        throw DownloadFailedException("Failed downloads:\n$failedDetail", e)
    } finally {
        clearSpeed()
    }
}

/**
 * Verifies in parallel whether locally existing files can be reused
 */
private suspend fun verifyExistingFilesConcurrently(
    tasks: List<DownloadTask>
): Pair<List<DownloadTask>, List<DownloadTask>> =
    withContext(Dispatchers.IO.limitedParallelism(LOCAL_VERIFY_PARALLELISM)) {
        coroutineScope {
            tasks.map { task ->
                async { task to task.existingFileValid() }
            }.awaitAll()
        }.partition { it.second }
            .let { (reusable, pending) -> reusable.map { it.first } to pending.map { it.first } }
    }

/**
 * Progress bar display strategy:
 * - when all files in the manifest declare sizes, byte-based progress is the smoothest;
 * - when sizes are unknown but the file count is known, fall back to counting finished files;
 * - when even the file count is unknown, show indeterminate progress.
 */
private fun progressFor(snapshot: BatchProgress, sizesFullyKnown: Boolean, hasFileCount: Boolean): Float = when {
    sizesFullyKnown && snapshot.totalBytes > 0 ->
        (snapshot.downloadedBytes.toFloat() / snapshot.totalBytes).coerceIn(0f, 1f)

    hasFileCount && snapshot.totalFiles > 0 ->
        snapshot.downloadedFiles.toFloat() / snapshot.totalFiles

    else -> -1f
}

