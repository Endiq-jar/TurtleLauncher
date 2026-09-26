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
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformVersion
import com.endiq.turtlelauncher.game.download.assets.platform.mcim.mapMCIMMirrorUrls
import com.endiq.turtlelauncher.game.download.game.GameDownloadInfo
import com.endiq.turtlelauncher.game.download.game.GameInstaller
import com.endiq.turtlelauncher.game.version.installed.VersionConfig
import com.endiq.turtlelauncher.game.version.installed.VersionFolders
import com.endiq.turtlelauncher.game.version.installed.getVersionIconFile
import com.endiq.turtlelauncher.path.PathManager
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.utils.file.copyDirectoryContents
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.network.downloadFile
import com.endiq.turtlelauncher.utils.network.downloadFileFromSources
import com.endiq.turtlelauncher.utils.network.isUsingMobileData
import com.endiq.turtlelauncher.utils.network.withSpeedReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File

private const val TAG = "ModPackInstaller"

/**
 * Online modpack installer; supports only CurseForge and Modrinth
 * @param version version info of the selected modpack
 * @param iconUrl the modpack's icon URL
 * @param scope the lifecycle-managed scope the install task runs in
 * @param waitForVersionName waits for the user to enter the version name
 * @param waitForConfirmMobileData waits for the user to confirm mobile-data use
 */
class ModPackInstaller(
    private val context: Context,
    private val version: PlatformVersion,
    private val iconUrl: String?,
    private val scope: CoroutineScope,
    private val waitForVersionName: suspend (ModPackInfo) -> String,
    private val waitForConfirmMobileData: suspend () -> Boolean,
) {
    private val taskExecutor = TaskFlowExecutor(scope)
    val tasksFlow: StateFlow<List<TitledTask>> = taskExecutor.tasksFlow

    private val _logOutput = MutableStateFlow<TaskLogOutput?>(null)
    /** Live log output of the install JVM */
    val logOutput: StateFlow<TaskLogOutput?> = _logOutput.asStateFlow()

    /**
     * Info parsed from the modpack file
     */
    private lateinit var modpackInfo: ModPackInfo

    /**
     * User-specified target version name
     */
    private lateinit var targetVersionName: String

    /**
     * Info of the game version about to be downloaded
     */
    private lateinit var gameDownloadInfo: GameDownloadInfo

    /**
     * Starts installing the modpack
     * @param isRunning called when the install is refused because one is running
     * @param onInstalled when the install completes
     * @param onCancelled on internal cancellation
     * @param onError when the install throws
     */
    fun installModPack(
        isRunning: () -> Unit = {},
        onInstalled: (version: String) -> Unit,
        onCancelled: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (taskExecutor.isRunning()) {
            //An install is already running; block this request
            isRunning()
            return
        }

        taskExecutor.executePhasesAsync(
            onStart = {
                val tasks = getTaskPhase()
                taskExecutor.addPhases(tasks)
            },
            onComplete = {
                onInstalled(targetVersionName)
            },
            onError = { e ->
                if (e is UsingMobileDataException) {
                    //User declined mobile data
                    onCancelled()
                    return@executePhasesAsync
                }
                onError(e)
            }
        )
    }

    private suspend fun getTaskPhase() = withContext(Dispatchers.IO) {
        //Temporary game environment directory
        val tempModPackDir = PathManager.DIR_CACHE_MODPACK_DOWNLOADER
        val tempVersionsDir = File(tempModPackDir, "fkVersion")
        //Modpack installer package file
        val installerFile = File(tempModPackDir, "installer.zip")
        //Icon temp file
        val tempIconFile = File(tempModPackDir, "icon.png")

        listOf(
            buildPhase {
                //Clear the previous install's cache (leftovers could skew this install's result)
                addTask(
                    id = "Download.ModPack.ClearTemp",
                    title = androidText(R.string.download_install_clear_temp),
                    icon = R.drawable.ic_auto_delete_outlined
                ) { _ ->
                    clearTempModPackDir()
                    //After cleaning the cache directory, create a fresh one
                    tempModPackDir.createDirAndLog()
                    tempVersionsDir.createDirAndLog()
                    VersionFolders.MOD.getDir(tempVersionsDir).createDirAndLog() //create the temp mods directory

                    //From this phase on, check whether mobile data may be used
                    if (isUsingMobileData(context)) {
                        val use = waitForConfirmMobileData()
                        if (!use) {
                            //User declined installing over mobile data; cancel the import
                            throw UsingMobileDataException()
                        }
                    }
                }

                //Download the modpack installer package
                addTask(
                    id = "Download.ModPack.Installer",
                    title = androidText(R.string.download_game_install_base_download_file2, version.platformDisplayName())
                ) { task ->
                    val totalFileSize = version.platformFileSize().toDouble()
                    var downloadedSize = 0L
                    fun updateProgress() {
                        task.updateProgress((downloadedSize.toDouble() / totalFileSize).toFloat())
                    }
                    withSpeedReport(
                        onSpeedReport = { bytes ->
                            task.updateSpeed(bytes)
                        },
                        onClear = {
                            task.clearSpeed()
                        }
                    ) { report ->
                        downloadFileFromSources(
                            urls = version
                                .platformDownloadUrl()
                                .mapMCIMMirrorUrls(),
                            sha1 = version.platformSha1(),
                            outputFile = installerFile,
                            sizeCallback = { size ->
                                downloadedSize += size
                                updateProgress()
                                report(size)
                            }
                        )
                    }
                    //Download the icon image
                    task.updateProgress(-1f)
                    task.updateMessage(null)
                    iconUrl?.let { iconUrl ->
                        downloadFile(
                            url = iconUrl,
                            outputFile = tempIconFile
                        )
                    }
                }

                //Parse the modpack and unpack it
                addTask(
                    id = "Parse.ModPack",
                    title = androidText(R.string.download_modpack_install_parse),
                    icon = R.drawable.ic_build_outlined
                ) { task ->
                    modpackInfo = parserModPack(
                        file = installerFile,
                        platform = version.platform(),
                        targetFolder = tempVersionsDir,
                        task = task
                    )
                }

                //Wait for the user to enter the target version name
                addTask(
                    id = "Download.ModPack.WaitUserForVersionName",
                    title = androidText(R.string.download_install_input_version_name),
                    icon = R.drawable.ic_edit_outlined
                ) { task ->
                    task.updateProgress(-1f)
                    targetVersionName = waitForVersionName(modpackInfo)
                }

                //Download the modpack's mod files
                addTask(
                    id = "Download.ModPack.Mods",
                    dispatcher = Dispatchers.IO,
                    title = androidText(R.string.download_modpack_download)
                ) { task ->
                    val downloadTask = ModDownloader(modpackInfo.files)
                    downloadTask.startDownload(task)
                }

                //Analyze and match mod loader info, building the game install info
                addTask(
                    id = "ModPack.Retrieve.Loader",
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
                        logOutputHolder = _logOutput
                    )
                    taskExecutor.addPhases(
                        phases = gameInstaller.getTaskPhase(
                            createIsolation = false,
                            onInstalled = { targetClientDir ->
                                //Game install finished; start the final task
                                //Modpack temp file install task
                                val finalTask = TitledTask(
                                    title = androidText(R.string.download_modpack_final_move),
                                    runningIcon = R.drawable.ic_build_outlined,
                                    task = createFinalInstallTask(
                                        targetClientDir = targetClientDir,
                                        tempVersionsDir = tempVersionsDir,
                                        tempIconFile = tempIconFile
                                    )
                                )
                                //Switch to the install phase
                                taskExecutor.addPhase(
                                    buildPhase { add(finalTask) }
                                )
                            }
                        )
                    )
                }
            }
        )
    }

    /**
     * Cancels the install
     */
    fun cancelInstall() {
        taskExecutor.cancel()
    }

    /**
     * Cleans up the temporary modpack version directory
     */
    private suspend fun clearTempModPackDir() = withContext(Dispatchers.IO) {
        PathManager.DIR_CACHE_MODPACK_DOWNLOADER.takeIf { it.exists() }?.let { folder ->
            FileUtils.deleteQuietly(folder)
            Logger.info(TAG, "Temporary modpack directory cleared.")
        }
    }

    /**
     * Creates the final install task
     */
    private fun createFinalInstallTask(
        targetClientDir: File,
        tempVersionsDir: File,
        tempIconFile: File
    ) = Task.runTask(
        id = "ModPack.Final.Install",
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

            //Copy the modpack icon
            if (tempIconFile.exists() && tempIconFile.isFile) {
                val iconFile = getVersionIconFile(targetClientDir)
                if (iconFile.exists()) FileUtils.deleteQuietly(iconFile)
                tempIconFile.copyTo(iconFile)
            }

            //Create version info
            VersionConfig.createIsolation(targetClientDir).apply {
                this.versionSummary = modpackInfo.summary ?: "" //modpack description
                this.ramAllocation = modpackInfo.ram ?: -1
            }.save()

            //Clean up the temp modpack directory
            task.updateProgress(-1f)
            task.updateMessage(androidText(R.string.download_install_clear_temp))
            clearTempModPackDir()
        }
    )

    private fun File.createDirAndLog(): File {
        this.mkdirs()
        Logger.debug(TAG, "Created directory: $this")
        return this
    }
}