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

package com.endiq.turtlelauncher.game.download.modpack.platform.multimc

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
import com.endiq.turtlelauncher.game.download.modpack.install.retrieveLoader
import com.endiq.turtlelauncher.game.download.modpack.platform.AbstractPack
import com.endiq.turtlelauncher.game.download.modpack.platform.PackPlatform
import com.endiq.turtlelauncher.game.version.installed.VersionConfig
import com.endiq.turtlelauncher.game.version.installed.getVersionIconFile
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.utils.file.copyDirectoryContents
import com.endiq.turtlelauncher.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

private const val TAG = "MultiMCPack"

open class MultiMCPack(
    private val root: File,
    private val manifest: MultiMCManifest
): AbstractPack(platform = PackPlatform.MultiMC) {

    /**
     * MultiMC instance configuration used by internal parsing
     */
    private var configuration: MultiMCConfiguration? = null

    /**
     * User-specified target version name
     */
    private lateinit var targetVersionName: String

    /**
     * Info of the game version about to be downloaded
     */
    private lateinit var gameDownloadInfo: GameDownloadInfo

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
                //Parse the MultiMC instance configuration
                addTask(
                    id = "ImportModpack.ParseMMCCfg",
                    title = androidText(R.string.import_modpack_task_parse),
                    icon = R.drawable.ic_build_outlined
                ) { task ->
                    task.updateProgress(-1f)
                    //MMC instance config file
                    configuration = loadMMCConfigFromPack(root)?.also { configuration ->
                        Logger.debug(TAG, "Successfully read the MultiMC instance configuration: $configuration")
                    }

                    //Once recognized, start extracting the modpack's game files
                    val minecraftDir = File(root, ".minecraft")
                    if (minecraftDir.exists() && minecraftDir.isDirectory) {
                        task.updateMessage(androidText(R.string.import_modpack_task_extract_files))
                        copyDirectoryContents(
                            from = minecraftDir,
                            to = versionFolder,
                            onProgress = { progress ->
                                task.updateProgress(progress)
                            }
                        )

                        //Migrate the icon (if any)
                        task.updateProgress(-1f)
                        val iconKey = configuration?.iconKey ?: "icon"
                        val iconFile = File(minecraftDir, "$iconKey.png").takeIf { file ->
                            file.exists() && file.isFile
                        } ?: File(minecraftDir, "icon.png")
                        
                        if (iconFile.exists() && iconFile.isFile) {
                            iconFile.copyTo(getVersionIconFile(versionFolder))
                            //After a successful copy, the pre-existing icon should be removed
                            iconFile.delete()
                        }
                    }
                }

                //Wait for the user to enter the target version name
                addTask(
                    id = "ImportModpack.WaitUserForVersionName",
                    title = androidText(R.string.download_install_input_version_name),
                    icon = R.drawable.ic_edit_outlined
                ) { task ->
                    task.updateProgress(-1f)
                    targetVersionName = waitForVersionName(configuration?.name ?: "")
                }

                //Analyze and match mod loader info, building the game install info
                addTask(
                    id = "ImportModpack.RetrieveLoader",
                    title = androidText(R.string.download_modpack_get_loaders),
                    icon = R.drawable.ic_build_outlined
                ) {
                    val gameVersion = manifest.getMinecraftVersion()!!

                    //Build the game install info
                    gameDownloadInfo = GameDownloadInfo(
                        gameVersion = gameVersion,
                        customVersionName = targetVersionName
                    )

                    //Build mod loader install info
                    manifest.components.forEach { component ->
                        with(manifest) { component.retrieveLoader() }?.let { pair ->
                            pair.retrieveLoader(
                                gameVersion = gameVersion,
                                gameInfo = gameDownloadInfo,
                                pasteGameInfo = { info ->
                                    gameDownloadInfo = info
                                }
                            )
                        }
                    }

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
                //JVM launch arguments
                configuration?.jvmArgs?.let { this.jvmArgs = it }
                //Auto-join a server on launch
                configuration?.joinServerOnLaunch?.let { this.serverIp = it }
            }.save()

            //Clean up the temp modpack directory
            task.updateProgress(-1f)
            task.updateMessage(androidText(R.string.download_install_clear_temp))
            onClearTemp()
        }
    )
}