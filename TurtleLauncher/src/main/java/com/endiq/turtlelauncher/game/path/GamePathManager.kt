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

package com.endiq.turtlelauncher.game.path

import android.content.Context
import com.endiq.turtlelauncher.database.AppDatabase
import com.endiq.turtlelauncher.game.version.installed.VersionsManager
import com.endiq.turtlelauncher.path.PathManager
import com.endiq.turtlelauncher.setting.AllSettings.currentGamePathId
import com.endiq.turtlelauncher.utils.canHandlePermission
import com.endiq.turtlelauncher.utils.hasStoragePermission
import com.endiq.turtlelauncher.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import kotlin.io.path.exists

private const val TAG = "GamePathManager"

/**
 * Game directory management, supporting saving game files to different paths
 */
object GamePathManager {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()
    private val defaultGamePath = File(PathManager.DIR_FILES_EXTERNAL, ".minecraft").absolutePath
    /**
     * Default game directory ID
     */
    const val DEFAULT_ID = "default"

    private val _gamePathData = MutableStateFlow<List<GamePath>>(listOf())
    val gamePathData = _gamePathData.asStateFlow()

    private val _currentPath = MutableStateFlow(defaultGamePath)
    /** Currently selected path */
    val currentPath = _currentPath.asStateFlow()

    /**
     * Launcher default game directory
     */
    fun getDefaultPath(): String = defaultGamePath

    /**
     * Current user path
     */
    fun getUserPath(): String = File(_currentPath.value).parentFile!!.absolutePath

    private lateinit var database: AppDatabase
    private lateinit var gamePathDao: GamePathDao

    fun initialize(context: Context) {
        database = AppDatabase.getInstance(context)
        gamePathDao = database.gamePathDao()
    }

    fun reloadPath() {
        scope.launch {
            mutex.withLock {
                _gamePathData.update { emptyList() }

                val newValue = mutableListOf<GamePath>()
                //Add the default game directory
                newValue.add(0, GamePath(DEFAULT_ID, "", defaultGamePath))

                run parseConfig@{
                    //Load game directories from the database
                    val paths = gamePathDao.getAllPaths()
                    newValue.addAll(paths.sortedBy { it.title })
                }

                _gamePathData.update { newValue }

                if (canHandlePermission &&  !hasStoragePermission) {
                    _currentPath.update { defaultGamePath }
                    saveDefaultPath(false)
                } else {
                    refreshCurrentPath(false)
                }

                VersionsManager.refresh("GamePathManager.reloadPath")
                Logger.info(TAG, "Loaded ${_gamePathData.value.size} game paths")
            }
        }
    }

    /**
     * Runs tasks gated on the path list finishing its refresh
     */
    suspend fun waitForRefresh() {
        mutex.withLock {}
    }

    private fun String.createNoMediaFile() {
        runCatching {
            val parent = Paths.get(this)
            Files.createDirectories(parent)
            val noMediaFile = parent.resolve(".nomedia")
            if (!noMediaFile.exists()) Files.createFile(noMediaFile)
        }.onFailure { e ->
            Logger.error(TAG, "Failed to create .nomedia file in $this", e)
        }
    }

    /**
     * Checks whether an entry with the given ID exists
     */
    fun containsId(id: String): Boolean = _gamePathData.value.any { it.id == id }

    /**
     * Checks whether an entry with the given path exists
     */
    fun containsPath(path: String): Boolean = _gamePathData.value.any { it.path == path }

    /**
     * Changes and saves the title of the given directory
     * @throws IllegalArgumentException when no matching entry is found
     */
    fun modifyTitle(path: GamePath, modifiedTitle: String) {
        if (!containsId(path.id)) throw IllegalArgumentException("Item with ID ${path.id} not found, unable to rename.")
        path.title = modifiedTitle
        savePath(path)
    }

    /**
     * Adds a new path and saves it
     * @throws IllegalArgumentException when the added path conflicts with existing entries
     */
    fun addNewPath(title: String, path: String) {
        if (containsPath(path)) throw IllegalArgumentException("The path conflicts with an existing item!")
        savePath(
            GamePath(id = generateUUID(), title = title, path = path)
        )
    }

    /**
     * Deletes a path and saves
     */
    fun removePath(path: GamePath) {
        if (!containsId(path.id)) return
        deletePath(path)
    }

    /**
     * Saves it as the default game directory
     */
    fun saveDefaultPath(reloadVersions: Boolean = true) {
        saveCurrentPathUncheck(DEFAULT_ID, reloadVersions)
    }

    /**
     * Saves the currently selected path
     * @throws IllegalStateException when storage / all-files permission isn't granted
     * @throws IllegalArgumentException when no matching entry is found
     */
    fun saveCurrentPath(id: String, reloadVersions: Boolean = true) {
        if (canHandlePermission && !hasStoragePermission) throw IllegalStateException("Storage permissions are not granted")
        if (!containsId(id)) throw IllegalArgumentException("No match found!")
        saveCurrentPathUncheck(id, reloadVersions)
    }

    private fun saveCurrentPathUncheck(id: String, reloadVersions: Boolean) {
        if (currentGamePathId.getValue() == id) return
        currentGamePathId.save(id)
        refreshCurrentPath(reloadVersions)
    }

    private fun refreshCurrentPath(reloadVersions: Boolean) {
        val id = currentGamePathId.getValue()
        _gamePathData.value.find { it.id == id }?.let { item ->
            if (_currentPath.value == item.path) return //avoid duplicate refreshes
            val path = item.path
            _currentPath.update { path }
            path.createNoMediaFile()
            if (reloadVersions) {
                VersionsManager.refresh("GamePathManager.refreshCurrentPath")
            }
        } ?: saveCurrentPath(DEFAULT_ID, reloadVersions)
    }

    private fun generateUUID(): String {
        val uuid = UUID.randomUUID().toString()
        return if (containsId(uuid)) generateUUID()
        else uuid
    }

    private fun savePath(path: GamePath) {
        scope.launch {
            runCatching {
                gamePathDao.savePath(path)
                Logger.info(TAG, "Saved game path: ${path.path}")
            }.onFailure { e ->
                Logger.error(TAG, "Failed to save game path config!", e)
            }
            reloadPath()
        }
    }

    private fun deletePath(path: GamePath) {
        scope.launch {
            gamePathDao.deletePath(path)
            reloadPath()
        }
    }
}