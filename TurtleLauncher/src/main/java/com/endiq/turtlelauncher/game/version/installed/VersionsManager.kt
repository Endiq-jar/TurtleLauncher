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

package com.endiq.turtlelauncher.game.version.installed

import com.endiq.turtlelauncher.game.path.getGameHome
import com.endiq.turtlelauncher.game.path.getVersionsHome
import com.endiq.turtlelauncher.game.version.installed.utils.parseJsonToVersionInfo
import com.endiq.turtlelauncher.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.apache.commons.io.FileUtils
import java.io.File

private const val TAG = "VersionsManager"

object VersionsManager {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()
    private val listeners: MutableList<suspend () -> Unit> = mutableListOf()

    /**
     * Registers a version list refresh listener
     */
    fun registerListener(listener: suspend () -> Unit) {
        listeners.add(listener)
    }

    /**
     * Removes a version list refresh listener
     */
    fun unregisterListener(listener: suspend () -> Unit) {
        listeners.remove(listener)
    }

    private val _versions = MutableStateFlow<List<Version>>(emptyList())
    /** All current game versions */
    val versions = _versions.asStateFlow()

    /**
     * Current game info
     */
    var gameInfo: CurrentGameInfo? = null
        private set

    private val _currentVersion = MutableStateFlow<Version?>(null)
    val currentVersion = _currentVersion.asStateFlow()

    private var currentJob: Job? = null

    private val _isRefreshing = MutableStateFlow(false)
    /** Whether versions are being refreshed */
    val isRefreshing = _isRefreshing.asStateFlow()

    /**
     * Checks whether the version already exists
     */
    fun isVersionExists(versionName: String, checkJson: Boolean = false): Boolean {
        val folder = File(getVersionsHome(), versionName)
        //Ensure the version folder exists, and its version json file exists as well
        return if (checkJson) File(folder, "${folder.name}.json").exists()
        else folder.exists()
    }

    /**
     * Refreshes all versions
     * @param tag who initiated the refresh; logged for easier tracing
     * @param trySetVersion tries to set the current version after refreshing
     */
    fun refresh(tag: String, trySetVersion: String? = null) {
        currentJob?.cancel()
        currentJob = scope.launch {
            mutex.withLock {
                _isRefreshing.update { true }
                Logger.debug(TAG, "Initiated by $tag: starting to refresh the version list.")

                //The game directory this refresh binds to, preventing cross-directory data if the directory switches mid-refresh
                val gameHome = getGameHome()

                if (trySetVersion != null) {
                    saveCurrentVersion(trySetVersion, refresh = false)
                    Logger.debug(TAG, "Has attempted to save the current version: $trySetVersion")
                }

                _versions.update { emptyList() }

                val newVersions = mutableListOf<Version>()
                File(getVersionsHome(gameHome)).listFiles()?.forEach { versionFile ->
                    runCatching {
                        processVersionFile(gameHome, versionFile)
                    }.getOrNull()?.let {
                        newVersions.add(it)
                    }
                }

                _versions.update { newVersions.sortedWith(VersionComparator) }

                gameInfo = refreshCurrentInfo(gameHome)
                Logger.debug(TAG, "Version list refreshed, refreshing the current version now.")
                refreshCurrentVersion()

                listeners.forEach { it() }

                _isRefreshing.update { false }
            }
        }
    }

    /**
     * Runs tasks that may run once the version list refresh completes
     */
    suspend fun waitForRefresh() {
        mutex.withLock {}
    }

    private fun processVersionFile(gameHome: String, versionFile: File): Version? {
        val version = loadVersion(gameHome, versionFile.name) ?: return null
        Logger.info(TAG,
            "Identified and added version: ${version.getVersionName()}, " +
                    "Path: (${version.getVersionPath()}), " +
                    "Info: ${version.getVersionInfo()?.getInfoString()}"
        )
        return version
    }

    /**
     * Loads a single version under the given game directory
     * @return null when the version doesn't exist or isn't a valid version folder
     */
    fun loadVersion(gameHome: String, versionName: String): Version? {
        val versionFile = File(getVersionsHome(gameHome), versionName)
        if (!versionFile.exists() || !versionFile.isDirectory) return null

        var isVersion = false

        //Decide whether it's a version by checking for the version's .json file
        val jsonFile = File(versionFile, "${versionFile.name}.json")
        val versionInfo = if (jsonFile.exists() && jsonFile.isFile) {
            parseJsonToVersionInfo(jsonFile)?.also {
                //If parsing failed, it may not be a standard version
                //To be safe, only successfully parsed versions count as valid
                isVersion = true
            }
        } else {
            null
        }

        val versionConfig = VersionConfig.parseConfig(versionFile)

        return Version(
            versionFile.name,
            gameHome,
            versionConfig,
            versionInfo,
            isVersion,
            versionInfo.getVersionType()
        )
    }

    private fun refreshCurrentVersion() {
        val version = run {
            val currentList = _versions.value
            if (currentList.isEmpty()) return@run null

            fun getVersionByFirst(): Version? {
                return currentList.find { it.isValid() }?.apply {
                    //Ensure the version is valid
                    saveCurrentVersion(getVersionName(), refresh = false)
                }
            }

            runCatching {
                val versionString = gameInfo!!.version
                currentList.getVersion(versionString) ?: run {
                    Logger.debug(TAG, "Stored version $versionString not found, using the first available version instead.")
                    getVersionByFirst()
                }
            }.onFailure { e ->
                Logger.warning(TAG, "The current version information has not been initialized yet.", e)
            }.getOrElse {
                getVersionByFirst()
            }
        }.also { version ->
            Logger.debug(TAG, "The current version is: ${version?.getVersionName()}")
        }

        _currentVersion.update { version }
    }

    private fun List<Version>.getVersion(name: String?): Version? {
        name?.let { versionName ->
            return find { it.getVersionName() == versionName }?.takeIf { it.isValid() }
        }
        return null
    }

    /**
     * Saves the currently selected version
     * @return whether the save was performed
     */
    fun saveVersion(version: Version, refresh: Boolean = true): Boolean {
        if (!version.isValid()) return false
        saveCurrentVersion(version.getVersionName(), refresh)
        return true
    }

    /**
     * Saves the currently selected version
     */
    fun saveCurrentVersion(versionName: String, refresh: Boolean = true) {
        runCatching {
            gameInfo!!.apply {
                version = versionName
                saveCurrentInfo(getGameHome())
            }
            if (refresh) {
                Logger.debug(TAG, "Current game info file saved, refreshing the current version now.")
                refreshCurrentVersion()
            }
        }.onFailure { e ->
            Logger.error(TAG, "An exception occurred while saving the currently selected version information.", e)
        }
    }

    /**
     * Renames the current version; the new name's legality is not validated here
     */
    fun renameVersion(version: Version, name: String) {
        val currentVersionName = _currentVersion.value?.getVersionName()
        //If the current version is the one being renamed, set the new name as the current version
        val saveToCurrent = version.getVersionName() == currentVersionName

        val versionFolder = version.getVersionPath()
        val renameFolder = File(version.getVersionsFolder(), name)

        //Whatever the renamed folder is, it must be deleted if it already exists
        //otherwise problems will arise
        FileUtils.deleteQuietly(renameFolder)

        val originalName = versionFolder.name

        versionFolder.renameTo(renameFolder)

        val versionJsonFile = File(renameFolder, "$originalName.json")
        val versionJarFile = File(renameFolder, "$originalName.jar")
        val renameJsonFile = File(renameFolder, "$name.json")
        val renameJarFile = File(renameFolder, "$name.jar")

        versionJsonFile.renameTo(renameJsonFile)
        versionJarFile.renameTo(renameJarFile)

        FileUtils.deleteQuietly(versionFolder)

        if (saveToCurrent) {
            //Set and refresh the current version
            saveCurrentVersion(name, refresh = false)
        }

        refresh("VersionsManager.renameVersion")
    }

    /**
     * Copies the selected version into a new version
     * @param version the selected version
     * @param name the new version's name
     * @param copyAllFile whether to copy all files
     */
    fun copyVersion(version: Version, name: String, copyAllFile: Boolean) {
        val versionsFolder = version.getVersionsFolder()
        val newVersion = File(versionsFolder, name)

        val originalName = version.getVersionName()

        //The new version's json and jar files
        val newJsonFile = File(newVersion, "$name.json")
        val newJarFile = File(newVersion, "$name.jar")

        val originalVersionFolder = version.getVersionPath()
        if (copyAllFile) {
            //With copy-all enabled, copy the original folder wholesale into the new version
            FileUtils.copyDirectory(originalVersionFolder, newVersion)
            //Rename the json/jar files
            val jsonFile = File(newVersion, "$originalName.json")
            val jarFile = File(newVersion, "$originalName.jar")
            if (jsonFile.exists()) jsonFile.renameTo(newJsonFile)
            if (jarFile.exists()) jarFile.renameTo(newJarFile)
        } else {
            //Without copying everything: only copy and rename the json/jar files
            val originalJsonFile = File(originalVersionFolder, "$originalName.json")
            val originalJarFile = File(originalVersionFolder, "$originalName.jar")
            newVersion.mkdirs()
            // versions/1.21.3/1.21.3.json -> versions/name/name.json
            if (originalJsonFile.exists()) originalJsonFile.copyTo(newJsonFile)
            // versions/1.21.3/1.21.3.jar -> versions/name/name.jar
            if (originalJarFile.exists()) originalJarFile.copyTo(newJarFile)
        }

        //Save the version config file
        version.getVersionConfig().copy().let { config ->
            config.setVersionPath(newVersion)
            config.isolationType = SettingState.ENABLE
            config.saveWithThrowable()
        }

        refresh("VersionsManager.copyVersion")
    }

    /**
     * Deletes a version
     */
    fun deleteVersion(version: Version) {
        FileUtils.deleteQuietly(version.getVersionPath())
        refresh("VersionsManager.deleteVersion")
    }
}