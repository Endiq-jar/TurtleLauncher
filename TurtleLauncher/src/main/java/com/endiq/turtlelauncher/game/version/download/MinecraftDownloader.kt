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

import android.content.Context
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.coroutine.Task
import com.endiq.turtlelauncher.game.path.getGameHome
import com.endiq.turtlelauncher.game.versioninfo.models.GameManifest
import com.endiq.turtlelauncher.game.versioninfo.models.VersionManifest
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.utils.file.formatFileSize
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.string.getMessageOrToString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

private const val TAG = "MinecraftDownloader"

/** Max concurrent download connections of a single install flow */
const val DEFAULT_DOWNLOAD_THREADS = 64

/**
 * Minecraft installer: assembles download tasks for the version JSON, client jar, assets and libraries,
 * and hands them to the batch download engine
 * Chunked concurrency, automatic source switching, whole-round retries on failure
 */
class MinecraftDownloader(
    private val context: Context,
    private val version: String,
    private val customName: String = version,
    private val gameHome: String = getGameHome(),
    private val downloader: BaseMinecraftDownloader = BaseMinecraftDownloader(gameHome),
    private val mode: DownloadMode = DownloadMode.DOWNLOAD,
    private val onCompletion: suspend (Task) -> Unit = {},
    private val onError: (message: String) -> Unit = {},
    private val onThrowable: ((throwable: Throwable) -> Unit)? = null,
    private val maxDownloadThreads: Int = DEFAULT_DOWNLOAD_THREADS
) {
    private var allDownloadTasks = mutableListOf<DownloadTask>()

    private fun <R> getTaskMessage(
        download: R,
        verify: R
    ): R =
        when (mode) {
            DownloadMode.DOWNLOAD -> download
            DownloadMode.VERIFY_AND_REPAIR -> verify
        }

    /**
     * Custom client directory ->client<-/versions/..
     */
    fun getDownloadTask(
        clientName: String = this.customName,
        clientVersionsDir: File = downloader.versionsTarget
    ): Task {
        return Task.runTask(
            id = DOWNLOADER_TAG,
            dispatcher = Dispatchers.Default,
            task = { task ->
                task.updateProgress(-1f)
                task.updateMessage(
                    getTaskMessage(androidText(R.string.minecraft_download_stat_download_task), null)
                )
                if (mode == DownloadMode.DOWNLOAD) {
                    progressNewDownloadTasks(clientName, clientVersionsDir)
                } else {
                    val jsonFile = downloader.getVersionJsonPath(customName).takeIf { it.canRead() } ?: throw IOException("Version $customName JSON file is unreadable.")
                    val jsonText = jsonFile.readText()
                    val gameManifest = jsonText.parseTo(GameManifest::class.java)
                    progressDownloadTasks(gameManifest, clientName)
                }

                if (allDownloadTasks.isNotEmpty()) {
                    task.runBatchDownloads(
                        tasks = allDownloadTasks,
                        maxConnections = maxDownloadThreads,
                        retryRounds = 1,
                        onSnapshot = { snapshot ->
                            task.updateSpeed(snapshot.speedBytesPerSec)
                            task.updateMessage(androidText(
                                getTaskMessage(R.string.minecraft_download_downloading_game_files, R.string.minecraft_download_verifying_and_repairing_files),
                                snapshot.downloadedFiles, snapshot.totalFiles,
                                formatFileSize(snapshot.downloadedBytes), formatFileSize(snapshot.totalBytes)
                            ))
                        }
                    )
                }
                //Clear task info
                task.updateProgress(1f)
                task.updateMessage(null)

                onCompletion(task)
            },
            onError = { e ->
                Logger.error(TAG, "Failed to download Minecraft!", e)
                if (onThrowable != null) {
                    onThrowable(e)
                } else {
                    val message = when(e) {
                        is CancellationException -> return@runTask
                        is FileNotFoundException -> context.getString(R.string.minecraft_download_failed_notfound)
                        else -> e.getMessageOrToString()
                    }
                    onError(message)
                }
            }
        )
    }

    /**
     * Installs only Jar and Json files into the custom version directory
     */
    private suspend fun progressNewDownloadTasks(
        clientName: String,
        clientVersionsDir: File
    ) {
        val gameManifest = downloader.findVersion(this.version)?.let {
            downloader.createVersionJson(it, clientName, clientVersionsDir)
        } ?: throw IllegalArgumentException("Version not found: $version")

        commonScheduleDownloads(gameManifest, null, clientName, clientVersionsDir)
    }

    private suspend fun progressDownloadTasks(
        gameManifest: GameManifest,
        clientName: String,
        clientVersionsDir: File = downloader.versionsTarget
    ) {
        val inheritsFrom = downloader.takeIf {
            gameManifest.inheritsFrom != null
        }?.findVersion(gameManifest.inheritsFrom)

        //Try parsing as vanilla first
        inheritsFrom?.let {
            downloader.createVersionJson(it)
        }?.let { gameManifest1 ->
            progressDownloadTasks(gameManifest1, gameManifest.inheritsFrom)
        }

        commonScheduleDownloads(
            gameManifest = gameManifest,
            inheritsFrom = inheritsFrom,
            clientName = clientName,
            clientVersionsDir = clientVersionsDir
        )
    }

    private suspend fun commonScheduleDownloads(
        gameManifest: GameManifest,
        inheritsFrom: VersionManifest.Version? = null,
        clientName: String,
        clientVersionsDir: File
    ) {
        val assetsIndex = downloader.createAssetIndex(downloader.assetIndexTarget, gameManifest)

        downloader.loadClientJarDownload(
            gameManifest = gameManifest,
            clientName = clientName,
            mcFolder = clientVersionsDir,
            scheduleDownload = { urls, hash, targetFile, size ->
                scheduleDownload(urls, hash, targetFile, size)
            },
            scheduleCopy = { targetFile ->
                inheritsFrom?.let { inheritsFrom ->
                    val inheritsJar = downloader.getVersionJarPath(inheritsFrom.id)

                    allDownloadTasks.find {
                        it.targetFile.absolutePath == inheritsJar.absolutePath
                    }?.let { task ->
                        task.fileDownloadedTask = {
                            if (!targetFile.exists() && inheritsJar.exists()) {
                                inheritsJar.copyTo(targetFile, overwrite = true)
                                Logger.info(TAG, "Copied ${inheritsJar.absolutePath} to ${targetFile.absolutePath}")
                            }
                        }
                    }
                }
            }
        )
        downloader.loadAssetsDownload(assetsIndex) { urls, hash, targetFile, size ->
            scheduleDownload(urls, hash, targetFile, size)
        }
        downloader.loadLibraryDownloads(gameManifest) { urls, hash, targetFile, size, isDownloadable ->
            scheduleDownload(urls, hash, targetFile, size, isDownloadable)
        }
    }

    /**
     * Submits the scheduled download
     */
    private fun scheduleDownload(urls: List<String>, sha1: String?, targetFile: File, size: Long, isDownloadable: Boolean = true) {
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
}
