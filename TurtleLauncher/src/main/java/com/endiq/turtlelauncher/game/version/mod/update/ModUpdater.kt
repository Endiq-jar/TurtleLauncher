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

package com.endiq.turtlelauncher.game.version.mod.update

import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.coroutine.Task
import com.endiq.turtlelauncher.coroutine.TaskFlowExecutor
import com.endiq.turtlelauncher.coroutine.TitledTask
import com.endiq.turtlelauncher.coroutine.addTask
import com.endiq.turtlelauncher.coroutine.buildPhase
import com.endiq.turtlelauncher.game.addons.modloader.ModLoader
import com.endiq.turtlelauncher.game.download.assets.platform.getProjectByVersion
import com.endiq.turtlelauncher.game.version.mod.ModProject
import com.endiq.turtlelauncher.game.version.mod.RemoteMod
import com.endiq.turtlelauncher.path.PathManager
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "ModUpdater"

/**
 * Fully automatic mod update check: checks the given mod list, fetches their newest versions, and matches them against the current MC version and mod loader
 * @param mods                  the mod list to check and update
 * @param modsDir               the current mods dir
 * @param minecraft             the MC main version, used for version matching
 * @param modLoader             mod loader info, used for version matching
 * @param waitForUserConfirm    waits for the user to confirm the mod update info
 *                              return `true` if the user approves; `false` cancels the install
 */
class ModUpdater(
    private val mods: List<RemoteMod>,
    private val modsDir: File,
    private val minecraft: String,
    private val modLoader: ModLoader,
    scope: CoroutineScope,
    private val waitForUserConfirm: suspend (List<ModManifest>) -> List<SelectableModManifest>
) {
    private val taskExecutor = TaskFlowExecutor(scope)
    val tasksFlow: StateFlow<List<TitledTask>> = taskExecutor.tasksFlow

    /**
     * Mods that need new-version checks
     */
    val dataList: MutableList<ModData> = mutableListOf()

    /**
     * Mods that need an update
     */
    val allModsUpdate: MutableList<ModManifest> = mutableListOf()

    /**
     * The final mod list to update
     */
    val finalModsUpdate: MutableList<ModManifest> = mutableListOf()

    /**
     * Starts updating all selected mods
     * @param isRunning called when this request is refused because an update is running
     * @param onUpdated when all mods have been updated successfully
     * @param onNoModUpdates when no mods need updating (all selected mods are up to date)
     * @param onCancelled when the update task is cancelled
     * @param onError when an error occurs while updating mods
     */
    fun updateAll(
        isRunning: () -> Unit = {},
        onUpdated: () -> Unit,
        onNoModUpdates: () -> Unit,
        onCancelled: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (taskExecutor.isRunning()) {
            //An update is running; block this request
            isRunning()
            return
        }

        taskExecutor.executePhasesAsync(
            onStart = {
                val tasks = getTaskPhases()
                taskExecutor.addPhases(tasks)
            },
            onComplete = onUpdated,
            onError = { th ->
                if (th is ModUpdateCancelledException) {
                    //The user cancelled this update
                    onCancelled()
                    return@executePhasesAsync
                }
                if (th is NoModUpdatesAvailableException) {
                    //All mods are up to date; nothing needs an update
                    onNoModUpdates()
                    return@executePhasesAsync
                }
                onError(th)
            }
        )
    }

    private suspend fun getTaskPhases() = withContext(Dispatchers.IO) {
        dataList.clear()
        allModsUpdate.clear()
        finalModsUpdate.clear()
        val tempModUpdaterDir = PathManager.DIR_CACHE_MOD_UPDATER

        listOf(
            buildPhase {
                //Clear the cache
                addTask(
                    id = "ModUpdater.ClearTemp",
                    title = androidText(R.string.download_install_clear_temp),
                    icon = R.drawable.ic_auto_delete_outlined
                ) {
                    clearTempModUpdaterDir()
                    //After cleanup, recreate the cache directory
                    tempModUpdaterDir.createDirAndLog()
                }

                //Filter the mod data
                addTask(
                    id = "ModUpdater.Filter",
                    title = androidText(R.string.mods_update_task_filter),
                    icon = R.drawable.ic_filter_alt_outlined
                ) { task ->
                    val totalSize = mods.size
                    val needLoad = mutableListOf<RemoteMod>()
                    val readyData = mutableListOf<ModData>()

                    mods.forEachIndexed { index, mod ->
                        val file = mod.localMod.file

                        task.updateProgress((index + 1f) / totalSize)
                        task.updateMessage(androidText(file.nameWithoutExtension))

                        // Filter out mods that can't be checked remotely
                        if (!mod.localMod.checkRemote) return@forEachIndexed

                        val modFile = mod.remoteFile
                        val project = mod.projectInfo

                        if (modFile != null && project != null) {
                            readyData += ModData(
                                file = file,
                                modFile = modFile,
                                project = project,
                                mcMod = mod.mcMod
                            )
                        } else {
                            needLoad += mod
                        }
                    }

                    val loadedData = if (needLoad.isNotEmpty()) {
                        needLoad.map { mod ->
                            async(Dispatchers.IO) {
                                loadModData(task, mod)
                            }
                        }.awaitAll()
                            .filterNotNull()
                            .also {
                                task.updateMessage(null)
                            }
                    } else {
                        emptyList()
                    }

                    dataList.addAll(readyData)
                    dataList.addAll(loadedData)
                }

                // Check for updates
                addTask(
                    id = "ModUpdater.CheckUpdate",
                    title = androidText(R.string.mods_update_task_check_update),
                    icon = R.drawable.ic_list_alt_check_outlined
                ) { task ->
                    // Concurrency capped at 5
                    val semaphore = Semaphore(5)
                    val completedCount = AtomicInteger(0)
                    val totalSize = dataList.size

                    val updateResults = dataList.map { data ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                // Check for updates
                                val version = data.checkUpdate(minecraft, modLoader)

                                // Update the progress bar thread-safely: progress is computed from finished count
                                val currentCompleted = completedCount.incrementAndGet()
                                task.updateProgress(currentCompleted.toFloat() / totalSize)
                                task.updateMessage(androidText(data.project.title))

                                // Return a pair when a new version exists; otherwise null
                                if (version != null) data to version else null
                            }
                        }
                    }.awaitAll().filterNotNull() // Wait for all tasks and filter out the null results needing no update

                    updateResults.forEach { (data, version) ->
                        allModsUpdate.add(ModManifest(data, version))
                    }

                    if (allModsUpdate.isEmpty()) {
                        //All mods are up to date; no update needed
                        throw NoModUpdatesAvailableException()
                    }
                }

                //Wait for the user to confirm the mod updates
                addTask(
                    id = "ModUpdater.WaitForUser",
                    title = androidText(R.string.mods_update_task_wait_for_user),
                    icon = R.drawable.ic_schedule_outlined
                ) {
                    val finalList = waitForUserConfirm(allModsUpdate).toFinalList()
                    if (finalList.isEmpty()) {
                        // The user cancelled, or no mods were selected for update
                        // Throw a cancellation exception here to end all tasks
                        throw ModUpdateCancelledException()
                    }
                    allModsUpdate.clear()
                    finalModsUpdate.addAll(finalList)
                }

                //Download the new mod versions
                addTask(
                    id = "ModUpdater.UpdateMod",
                    title = androidText(R.string.mods_update_task_download)
                ) { task ->
                    val updater = ModVersionUpdater(
                        mods = finalModsUpdate.allNews(),
                        targetDir = tempModUpdaterDir
                    )
                    updater.startDownload(task)
                }

                //Replace the mod files
                addTask(
                    id = " ModUpdater.ReplaceMod",
                    title = androidText(R.string.mods_update_task_replace),
                    icon = R.drawable.ic_build_outlined
                ) { task ->
                    val totalCount = finalModsUpdate.size
                    finalModsUpdate.forEachIndexed { index, entry ->
                        val oldMod = entry.data
                        val newVersion = entry.new

                        val oldFile = oldMod.file
                        val newFileName = newVersion.platformFileName()
                        val cacheFile = File(tempModUpdaterDir, newFileName)

                        task.updateProgress((index + 1).toFloat() / totalCount)
                        task.updateMessage(androidText(oldFile.name))

                        //Ensure all files are valid
                        if (modsDir.exists() && oldFile.exists() && cacheFile.exists()) {
                            FileUtils.deleteQuietly(oldFile)
                            val newFile = File(modsDir, newFileName)
                            cacheFile.copyTo(target = newFile, overwrite = true)
                        }
                    }
                }

                //Clear the cache
                addTask(
                    id = "ModUpdater.ClearTempEnds",
                    title = androidText(R.string.download_install_clear_temp),
                    icon = R.drawable.ic_auto_delete_outlined
                ) {
                    clearTempModUpdaterDir()
                }
            }
        )
    }

    private suspend fun loadModData(
        task: Task,
        mod: RemoteMod
    ): ModData? {
        val file = mod.localMod.file

        val modFile = mod.remoteFile ?: runCatching {
            task.updateMessage(androidText(
                R.string.mods_update_task_loading, file.name
            ))
            mod.loadRemoteFile()
        }.onFailure {
            Logger.warning(TAG, "Failed to load remote mod version", it)
        }.getOrNull() ?: return null

        val project = mod.projectInfo ?: runCatching {
            task.updateMessage(androidText(
                R.string.mods_update_task_loading, file.name
            ))

            val project = getProjectByVersion(modFile.projectId, modFile.platform)
            ModProject(
                id = project.platformId(),
                platform = project.platform(),
                iconUrl = project.platformIconUrl(),
                title = project.platformTitle(),
                slug = project.platformSlug()
            )
        }.onFailure {
            Logger.warning(TAG, "Failed to load remote project", it)
        }.getOrNull() ?: return null

        return ModData(
            file = file,
            modFile = modFile,
            project = project,
            mcMod = mod.mcMod
        )
    }

    fun cancel() {
        taskExecutor.cancel()
    }

    /**
     * Cleans the temporary mod update cache directory
     */
    private suspend fun clearTempModUpdaterDir() = withContext(Dispatchers.IO) {
        PathManager.DIR_CACHE_MOD_UPDATER.takeIf { it.exists() }?.let { folder ->
            FileUtils.deleteQuietly(folder)
            Logger.info(TAG, "Temporary mod updater directory cleared.")
        }
    }

    private fun File.createDirAndLog(): File {
        this.mkdirs()
        Logger.debug(TAG, "Created directory: $this")
        return this
    }
}