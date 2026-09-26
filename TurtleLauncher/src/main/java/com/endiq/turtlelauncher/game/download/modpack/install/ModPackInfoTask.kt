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

package com.endiq.turtlelauncher.game.download.modpack.install

import android.content.Context
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.coroutine.Task
import com.endiq.turtlelauncher.coroutine.TaskFlowExecutor
import com.endiq.turtlelauncher.coroutine.TaskLogOutput
import com.endiq.turtlelauncher.coroutine.TitledTask
import com.endiq.turtlelauncher.coroutine.addTask
import com.endiq.turtlelauncher.coroutine.buildPhase
import com.endiq.turtlelauncher.game.download.game.GameDownloadInfo
import com.endiq.turtlelauncher.game.download.game.GameInstaller
import com.endiq.turtlelauncher.game.download.modpack.platform.AbstractPack
import com.endiq.turtlelauncher.game.download.modpack.platform.PackPlatform
import com.endiq.turtlelauncher.game.version.installed.VersionConfig
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.utils.file.copyDirectoryContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Task-flow creation interface for modpack import, implemented on [ModPackInfo]
 * @param root modpack root directory
 */
abstract class ModPackInfoTask(
    protected val root: File,
    platform: PackPlatform
) : AbstractPack(platform = platform) {
    /**
     * Info parsed from the modpack file for internal use
     */
    protected lateinit var modpackInfo: ModPackInfo

    /**
     * User-specified target version name
     */
    protected lateinit var targetVersionName: String

    /**
     * Info of the game version about to be downloaded
     */
    protected lateinit var gameDownloadInfo: GameDownloadInfo

    /**
     * Reads the modpack install info
     */
    protected abstract suspend fun readInfo(
        task: Task,
        versionFolder: File,
        root: File
    ): ModPackInfo

    override fun getFinalClientName(): String {
        return targetVersionName
    }

    override fun buildTaskPhases(
        context: Context,
        scope: CoroutineScope,
        versionFolder: File,
        waitForVersionName: suspend (name: String) -> String,
        addPhases: (List<TaskFlowExecutor.TaskPhase>) -> Unit,
        onClearTemp: suspend () -> Unit,
        logOutputHolder: MutableStateFlow<TaskLogOutput?>
    ): List<TaskFlowExecutor.TaskPhase> {
        return listOf(
            buildPhase {
                //Extract and schedule download tasks
                addTask(
                    id = "ImportModpack.ExtractFiles",
                    title = androidText(R.string.import_modpack_task_extract_files_and_schedule_download),
                    icon = R.drawable.ic_build_outlined
                ) { task ->
                    modpackInfo = readInfo(task, versionFolder, root)
                }

                //Wait for the user to enter the target version name
                addTask(
                    id = "ImportModpack.WaitUserForVersionName",
                    title = androidText(R.string.download_install_input_version_name),
                    icon = R.drawable.ic_edit_outlined
                ) { task ->
                    task.updateProgress(-1f)
                    targetVersionName = waitForVersionName(modpackInfo.name)
                }

                //Download the modpack's mod files
                addTask(
                    id = "ImportModpack.DownloadMods",
                    dispatcher = Dispatchers.IO,
                    title = androidText(R.string.download_modpack_download)
                ) { task ->
                    val downloadTask = ModDownloader(modpackInfo.files)
                    downloadTask.startDownload(task)
                }

                //Analyze and match mod loader info, building the game install info
                addTask(
                    id = "ImportModpack.RetrieveLoader",
                    title = androidText(R.string.download_modpack_get_loaders),
                    icon = R.drawable.ic_build_outlined
                ) { _ ->
                    //Build the game install info
                    gameDownloadInfo = modpackInfo.retrieveLoaderTask(
                        targetVersionName = targetVersionName
                    )

                    //Start installing the game! On to the next phase!
                    val gameInstaller = GameInstaller(
                        context = context,
                        info = gameDownloadInfo,
                        scope = scope,
                        logOutputHolder = logOutputHolder
                    )
                    addPhases(
                        gameInstaller.getTaskPhase(
                            createIsolation = false,
                            onInstalled = { targetClientDir ->
                                //Game install finished; start the final task
                                //Modpack temp file install task
                                val finalTask = TitledTask(
                                    title = androidText(R.string.download_modpack_final_move),
                                    runningIcon = R.drawable.ic_build_outlined,
                                    task = createFinalInstallTask(
                                        targetClientDir = targetClientDir,
                                        tempVersionsDir = versionFolder,
                                        onClearTemp = onClearTemp
                                    )
                                )
                                //Switch to the install phase
                                addPhases(
                                    listOf(
                                        buildPhase { add(finalTask) }
                                    )
                                )
                            }
                        )
                    )
                }
            }
        )
    }

    /**
     * Creates the final install task
     */
    private fun createFinalInstallTask(
        targetClientDir: File,
        tempVersionsDir: File,
        onClearTemp: suspend () -> Unit
    ) = Task.runTask(
        id = "ImportModpack.FinalInstall",
        dispatcher = Dispatchers.IO,
        task = { task ->
            task.updateProgress(-1f)
            //Copy files
            copyDirectoryContents(
                tempVersionsDir,
                targetClientDir
            ) { percentage ->
                task.updateProgress(percentage = percentage)
            }

            //Create version info
            VersionConfig.createIsolation(targetClientDir).apply {
                this.versionSummary = modpackInfo.summary ?: "" //modpack description
                this.ramAllocation = modpackInfo.ram ?: -1
            }.save()

            //Clean up the temp modpack directory
            task.updateProgress(-1f)
            task.updateMessage(androidText(R.string.download_install_clear_temp))
            onClearTemp()
        }
    )
}