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

package com.endiq.turtlelauncher.game.download.game

import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.coroutine.Task
import com.endiq.turtlelauncher.game.version.download.BaseMinecraftDownloader
import com.endiq.turtlelauncher.game.version.download.DEFAULT_DOWNLOAD_THREADS
import com.endiq.turtlelauncher.game.version.download.DownloadTask
import com.endiq.turtlelauncher.game.version.download.parseTo
import com.endiq.turtlelauncher.game.version.download.runBatchDownloads
import com.endiq.turtlelauncher.game.versioninfo.models.GameManifest
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.utils.file.formatFileSize
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Game library downloader
 * Assembles library tasks from the version JSON and hands them to the batch download engine
 */
class GameLibDownloader(
    private val downloader: BaseMinecraftDownloader,
    private val gameJson: String,
    private val maxDownloadThreads: Int = DEFAULT_DOWNLOAD_THREADS
) {
    private val allDownloadTasks = ConcurrentLinkedQueue<DownloadTask>()

    //Check whether downloading has begun
    private var isDownloadStarted: Boolean = false

    /**
     * Schedules downloading all libraries
     */
    suspend fun schedule(
        task: Task,
        targetDir: File = downloader.librariesTarget,
        updateProgress: Boolean = true
    ) {
        val gameManifest = gameJson.parseTo(GameManifest::class.java)

        if (updateProgress) {
            task.updateProgress(-1f)
            task.updateMessage(androidText(R.string.minecraft_download_stat_download_task))
        }

        //Only load and process the libraries
        downloader.loadLibraryDownloads(gameManifest, targetDir) { urls, hash, targetFile, size, isDownloadable ->
            scheduleDownload(urls, hash, targetFile, size, isDownloadable)
        }
    }

    /**
     * Hands all library tasks to the download engine
     */
    suspend fun download(task: Task) {
        isDownloadStarted = true
        val tasks = allDownloadTasks.toList()
        if (tasks.isNotEmpty()) {
            task.runBatchDownloads(
                tasks = tasks,
                maxConnections = maxDownloadThreads,
                retryRounds = 1,
                onSnapshot = { snapshot ->
                    task.updateSpeed(snapshot.speedBytesPerSec)
                    task.updateMessage(androidText(
                        R.string.minecraft_download_downloading_game_files,
                        snapshot.downloadedFiles, snapshot.totalFiles,
                        formatFileSize(snapshot.downloadedBytes), formatFileSize(snapshot.totalBytes)
                    ))
                }
            )
        }

        //Clear task info
        task.updateProgress(1f)
        task.updateMessage(null)
    }

    /**
     * Submits the scheduled download
     */
    fun scheduleDownload(urls: List<String>, sha1: String?, targetFile: File, size: Long, isDownloadable: Boolean = true) {
        if (isDownloadStarted) throw IllegalStateException("The download has already started; adding more download tasks is no longer meaningful.")

        if (allDownloadTasks.any { it.targetFile.absolutePath == targetFile.absolutePath }) return

        allDownloadTasks.add(
            DownloadTask(
                urls = urls,
                verifyIntegrity = true,
                targetFile = targetFile,
                sha1 = sha1,
                size = size,
                isDownloadable = isDownloadable
            )
        )
    }

    /**
     * Removes one download task
     * Usable before downloading
     */
    fun removeDownload(predicate: (DownloadTask) -> Boolean) {
        if (isDownloadStarted) throw IllegalStateException("The download has already started; removing download tasks is no longer meaningful.")
        allDownloadTasks.removeIf(predicate)
    }
}
