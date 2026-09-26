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

package com.endiq.turtlelauncher.game.version.installed.cleanup

import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.coroutine.Task
import com.endiq.turtlelauncher.coroutine.TaskFlowExecutor
import com.endiq.turtlelauncher.coroutine.TitledTask
import com.endiq.turtlelauncher.coroutine.addTask
import com.endiq.turtlelauncher.coroutine.buildPhase
import com.endiq.turtlelauncher.game.path.getAssetsHome
import com.endiq.turtlelauncher.game.version.download.BaseMinecraftDownloader
import com.endiq.turtlelauncher.game.version.installed.VersionInfoParser
import com.endiq.turtlelauncher.game.version.installed.VersionsManager
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.utils.file.collectFiles
import com.endiq.turtlelauncher.utils.file.findRedundantFiles
import com.endiq.turtlelauncher.utils.file.formatFileSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File

class GameAssetCleaner(
    scope: CoroutineScope
) {
    private val taskExecutor = TaskFlowExecutor(scope)
    val tasksFlow: StateFlow<List<TitledTask>> = taskExecutor.tasksFlow

    /**
     * Base downloader
     */
    private val downloader = BaseMinecraftDownloader()

    /**
     * All installed files
     */
    private val allFiles = mutableListOf<File>()

    /**
     * All files the game needs
     */
    private val allGameFiles = mutableListOf<File>()

    /**
     * All redundant files
     */
    private lateinit var allRedundantFiles: List<File>

    /**
     * Number of cleaned files
     */
    private var cleanedFileCount = 0

    /**
     * Total size of cleaned files
     */
    private var cleanedSize: Long = 0L

    /**
     * Files that failed to clean
     */
    private val failedFiles = mutableListOf<File>()

    /**
     * Starts cleaning
     * @param isRunning called when cleaning is refused because one is running
     * @param onEnd when cleaning ends
     * @param onThrowable when cleaning hits an error
     */
    fun start(
        isRunning: () -> Unit = {},
        onEnd: (count: Int, size: String) -> Unit,
        onThrowable: (Throwable) -> Unit
    ) {
        if (taskExecutor.isRunning()) {
            //A cleanup is running; block this request
            isRunning()
            return
        }

        taskExecutor.executePhasesAsync(
            onStart = {
                val tasks = getTaskPhases()
                taskExecutor.addPhases(tasks)
            },
            onComplete = {
                onEnd(cleanedFileCount, formatFileSize(cleanedSize))
            },
            onError = onThrowable
        )
    }

    private suspend fun getTaskPhases() = withContext(Dispatchers.IO) {
        //Libraries are no longer cleaned: they're small and cleaning could cause other issues: #617
//        val libraryFolder = File(getLibrariesHome())
        val assetsFolder = File(getAssetsHome())

        allFiles.clear()
        allGameFiles.clear()
        failedFiles.clear()
        cleanedFileCount = 0
        cleanedSize = 0L

        listOf(
            buildPhase {
                //Collect all files
                addTask(
                    id = "GameAssetCleaner.CollectFiles",
                    title = androidText(R.string.versions_manage_cleanup_collect_files),
                    icon = R.drawable.ic_article_outlined,
                ) { task ->
                    task.updateProgress(-1f)

//                    collectFiles(libraryFolder) { allFiles.add(it.alsoProgress(task)) }
                    collectFiles(assetsFolder) { allFiles.add(it.alsoProgress(task)) }
                }

                //Collect the game files needed by all versions
                addTask(
                    id = "GameAssetCleaner.CollectGameFiles",
                    title = androidText(R.string.versions_manage_cleanup_collect_game_files),
                    icon = R.drawable.ic_article_outlined
                ) { task ->
                    task.updateProgress(-1f)

                    val allVersions = VersionsManager.versions.value
                    allVersions.forEach { version ->
                        ensureActive()

                        task.updateMessage(androidText(
                            R.string.versions_manage_cleanup_progress_next_version, version.getVersionName()
                        ))

                        //Judge by the dependencies needed at game-launch time
                        val gameManifest = VersionInfoParser(version)
                            .setInheriting(skipIfNotExists = true)
                            .build()

                        val index = downloader.createAssetIndex(downloader.assetIndexTarget, gameManifest)

                        fun addGameFile(file: File) {
                            if (allGameFiles.addIfNotContains(file)) {
                                file.alsoProgress(task)
                            } else {
                                task.updateMessage(androidText(R.string.versions_manage_cleanup_progress_collected))
                            }
                        }

//                        downloader.loadLibraryDownloads(gameManifest) { _, _, targetFile, _, _ ->
//                            addGameFile(targetFile)
//                        }
                        downloader.loadAssetsDownload(index) { _, _, targetFile, _ ->
                            addGameFile(targetFile)
                        }
                    }
                }

                //Diff out the useless files
                addTask(
                    id = "GameAssetCleaner.CompareFiles",
                    title = androidText(R.string.versions_manage_cleanup_compare_files),
                    icon = R.drawable.ic_build_outlined
                ) { task ->
                    task.updateProgress(-1f)

                    allRedundantFiles = findRedundantFiles(
                        sourceFiles = allFiles,
                        targetFiles = allGameFiles,
                    ).filter { it.exists() }
                }

                //Clean the files
                addTask(
                    id = "GameAssetsCleaner.Cleanup",
                    title = androidText(R.string.versions_manage_cleanup_cleanup),
                    icon = R.drawable.ic_auto_delete_outlined,
                    dispatcher = Dispatchers.IO
                ) { task ->
                    task.updateProgress(-1f)

                    val totalSize = allRedundantFiles.size
                    allRedundantFiles.forEachIndexed { index, file ->
                        ensureActive()
                        val size = FileUtils.sizeOf(file)
                        if (!FileUtils.deleteQuietly(file)) {
                            failedFiles.add(file)
                        } else {
                            cleanedFileCount++
                            cleanedSize += size
                        }
                        task.updateProgress(
                            index.toFloat() / totalSize.toFloat()
                        )
                        task.updateMessage(androidText(
                            R.string.versions_manage_cleanup_progress, file.name
                        ))
                    }

                    task.updateProgress(-1f)

                    if (failedFiles.isNotEmpty()) {
                        throw CleanFailedException(failedFiles)
                    }
                }
            }
        )
    }

    fun cancel() {
        taskExecutor.cancel()
    }

    private fun File.alsoProgress(task: Task) = this.also {
        task.updateProgress(-1f)
        task.updateMessage(androidText(
            R.string.versions_manage_cleanup_progress, it.name
        ))
    }

    /**
     * Adds the file when the set holds no file with that path
     * @return whether it was added
     */
    private fun MutableList<File>.addIfNotContains(file: File): Boolean {
        return if (!any { it.absolutePath == file.absolutePath }) {
            add(file)
            true
        } else {
            false
        }
    }
}