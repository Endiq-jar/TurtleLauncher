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
import android.net.Uri
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.context.copyLocalFile
import com.endiq.turtlelauncher.coroutine.TaskFlowExecutor
import com.endiq.turtlelauncher.coroutine.TaskLogOutput
import com.endiq.turtlelauncher.coroutine.TitledTask
import com.endiq.turtlelauncher.coroutine.addTask
import com.endiq.turtlelauncher.coroutine.buildPhase
import com.endiq.turtlelauncher.game.download.modpack.platform.ALL_PACK_PARSER
import com.endiq.turtlelauncher.game.download.modpack.platform.AbstractPack
import com.endiq.turtlelauncher.game.path.GamePathManager
import com.endiq.turtlelauncher.game.version.installed.VersionFolders
import com.endiq.turtlelauncher.game.version.installed.VersionsManager
import com.endiq.turtlelauncher.path.PathManager
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.utils.file.extractFromZip
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.network.isUsingMobileData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File
import org.apache.commons.compress.archivers.zip.ZipFile as ApacheZipFile
import java.util.zip.ZipFile as JDKZipFile

private const val TAG = "ModpackImporter"

/**
 * Local modpack importer
 * @param uri locally picked file URI
 * @param scope the lifecycle-managed scope the install task runs in
 * @param waitForVersionName waits for the user to enter the target version name
 * @param waitForConfirmMobileData waits for the user to confirm mobile-data use
 */
class ModpackImporter(
    private val context: Context,
    private val uri: Uri,
    private val scope: CoroutineScope,
    private val waitForVersionName: suspend (String) -> String,
    private val waitForConfirmMobileData: suspend () -> Boolean,
) {
    private val taskExecutor = TaskFlowExecutor(scope)
    val taskFlow: StateFlow<List<TitledTask>> = taskExecutor.tasksFlow

    private val _logOutput = MutableStateFlow<TaskLogOutput?>(null)
    /** Live log output of the install JVM */
    val logOutput: StateFlow<TaskLogOutput?> = _logOutput.asStateFlow()

    /**
     * Task builder of the modpack currently being imported
     */
    private lateinit var modpack: AbstractPack

    /**
     * Starts importing a modpack
     * @param isRunning called when an import is refused because one is running
     * @param onFinished when the import finishes
     * @param onCancelled on internal cancellation
     * @param onError when the import throws
     */
    fun startImport(
        isRunning: () -> Unit = {},
        onFinished: (version: String) -> Unit,
        onCancelled: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (taskExecutor.isRunning()) {
            isRunning()
            return //already running; refuse the import
        }

        taskExecutor.executePhasesAsync(
            onStart = {
                val tasks = getTaskPhases()
                taskExecutor.addPhases(tasks)
            },
            onComplete = {
                onFinished(modpack.getFinalClientName())
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

    private suspend fun getTaskPhases() = withContext(Dispatchers.IO) {
        //Temporary game environment directory
        val tempModPackDir = PathManager.DIR_CACHE_MODPACK_DOWNLOADER
        val tempVersionsDir = File(tempModPackDir, "fkVersion")
        //Modpack installer package file
        val installerDir = File(tempModPackDir, "installer")
        val installerFile = File(installerDir, ".temp_installer.zip")
        //Unpacked modpack cache directory
        val packDir = File(installerDir, ".temp_pack")

        listOf(
            buildPhase {
                //Clear the previous install's cache (leftovers could skew this install's result)
                addTask(
                    id = "ImportModpack.Cleanup",
                    title = androidText(R.string.download_install_clear_temp),
                    icon = R.drawable.ic_auto_delete_outlined
                ) { _ ->
                    GamePathManager.waitForRefresh()
                    VersionsManager.waitForRefresh()
                    clearTempModPackDir()
                    //After cleaning the cache directory, create a fresh one
                    tempModPackDir.createDirAndLog()
                    tempVersionsDir.createDirAndLog()
                    installerDir.createDirAndLog()
                    packDir.createDirAndLog()
                    VersionFolders.MOD.getDir(tempVersionsDir).createDirAndLog() //create the temp mods directory
                }

                //Import the files first
                addTask(
                    id = "ImportModpack.ImportFile",
                    title = androidText(R.string.import_modpack_task_unpack),
                    dispatcher = Dispatchers.IO,
                    icon = R.drawable.ic_unarchive_outlined
                ) { task ->
                    task.updateProgress(-1f)
                    context.copyLocalFile(uri, installerFile)
                    //Try to unpack the archive
                    try {
                        JDKZipFile(installerFile).use { zip ->
                            zip.extractFromZip("", packDir)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Logger.warning(TAG, "JDK ZipFile failed to unpack, fallback to Apache ZipFile.", e)
                        try {
                            ApacheZipFile.builder().setFile(installerFile).get().use { zip ->
                                zip.extractFromZip("", packDir)
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            //If the fallback unpacking also fails, this probably isn't an archive
                            //or the archive is corrupted; throw the unsupported exception and end the flow
                            Logger.error(TAG, "Unable to extract the installer file. Is it really a compressed archive?", e)
                            throw PackNotSupportedException(
                                reason = UnsupportedPackReason.CorruptedArchive
                            )
                        }
                    }

                    //From this phase on, check whether mobile data may be used
                    if (isUsingMobileData(context)) {
                        val use = waitForConfirmMobileData()
                        if (!use) {
                            //User declined installing over mobile data; cancel the import
                            throw UsingMobileDataException()
                        }
                    }
                }

                //Parse the modpack
                addTask(
                    id = "ImportModpack.ParsePack",
                    title = androidText(R.string.import_modpack_task_parse),
                    icon = R.drawable.ic_build_outlined
                ) { task ->
                    task.updateProgress(-1f)

                    modpack = run {
                        //Try parsing with every modpack format
                        for (parser in ALL_PACK_PARSER) {
                            ensureActive()

                            val result = runCatching {
                                parser.parse(packFolder = packDir)
                            }.onFailure { th ->
                                Logger.debug(TAG, "${parser.getIdentifier()} parser does not recognize this format", th)
                            }.getOrNull()

                            if (result != null) {
                                //The modpack format was recognized
                                Logger.info(TAG, "Successfully detected the modpack format: ${result.platform.identifier}")
                                return@run result
                            } else {
                                Logger.debug(TAG, "Skipped the ${parser.getIdentifier()} parser")
                            }
                        }
                        //The modpack is unsupported, or its format matched nothing
                        null
                    } ?: throw PackNotSupportedException(UnsupportedPackReason.UnsupportedFormat)

                    //Add the task flow for the next import phase
                    taskExecutor.addPhases(
                        modpack.buildTaskPhases(
                            context = context,
                            scope = scope,
                            versionFolder = tempVersionsDir,
                            waitForVersionName = waitForVersionName,
                            addPhases = { phases ->
                                taskExecutor.addPhases(phases)
                            },
                            onClearTemp = {
                                clearTempModPackDir()
                            },
                            logOutputHolder = _logOutput
                        )
                    )
                }
            }
        )
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
     * Cancels the modpack import
     */
    fun cancel() {
        taskExecutor.cancel()
    }

    private fun File.createDirAndLog(): File {
        this.mkdirs()
        Logger.debug(TAG, "Created directory: $this")
        return this
    }
}