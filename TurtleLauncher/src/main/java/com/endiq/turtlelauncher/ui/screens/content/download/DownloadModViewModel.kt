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

package com.endiq.turtlelauncher.ui.screens.content.download

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.endiq.turtlelauncher.game.download.assets.platform.Platform
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformVersion
import com.endiq.turtlelauncher.game.version.installed.Version
import com.endiq.turtlelauncher.game.version.installed.VersionFolders
import com.endiq.turtlelauncher.game.version.mod.InstalledMod
import com.endiq.turtlelauncher.game.version.mod.ModFingerprints
import com.endiq.turtlelauncher.game.version.mod.matchInstalledMods
import com.endiq.turtlelauncher.game.version.mod.scanModFingerprints
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "DownloadModViewModel"

/** Local mod install state for the mod download screen */
class DownloadModViewModel : ViewModel() {
    /** The current match target platform */
    var currentPlatform: Platform = AllSettings.searchModPlatform.getValue()
        private set

    /** Whether a local scan or platform match is running */
    var matching by mutableStateOf(false)
        private set

    /** Locally installed mod projects for the current platform, keyed by platform project ID */
    var installedByProject by mutableStateOf<Map<String, InstalledMod>>(emptyMap())
        private set

    /** Locally installed mod versions for the current platform, keyed by platform version ID */
    private var installedByVersion by mutableStateOf<Map<String, InstalledMod>>(emptyMap())

    private var scanJob: Job? = null

    /** The last scan's version name, detecting target changes */
    private var scannedVersionName: String? = null

    /** All local mod file fingerprints from the last scan */
    private var scannedFingerprints: List<ModFingerprints> = emptyList()

    /** Finished platform matches of this lifecycle, avoiding recomputation on platform switches */
    private val matchedResults =
        mutableMapOf<Platform, Pair<Map<String, InstalledMod>, Map<String, InstalledMod>>>()

    /**
     * Checks whether a platform project is installed locally
     */
    fun checkProject(platform: Platform, projectId: String): InstalledMod? {
        return installedByProject[projectId]
            ?.takeIf { it.platform == platform && !it.notFound }
    }

    /**
     * Checks whether a platform version is installed locally
     */
    fun checkVersion(version: PlatformVersion): InstalledMod? {
        return installedByVersion[version.platformId()]
            ?.takeIf { it.platform == version.platform() && !it.notFound }
    }

    /**
     * Scans a game version's mods directory and matches locally installed mods per the current platform
     *
     * Changing the scanned version drops old matches at once;
     * rescanning the same version keeps old results until new ones arrive, avoiding flicker
     */
    fun scan(version: Version?) {
        val versionName = version?.getVersionName()
        if (scanJob?.isActive == true && versionName == scannedVersionName) return

        scanJob?.cancel()

        if (versionName != scannedVersionName) {
            installedByProject = emptyMap()
            installedByVersion = emptyMap()
        }
        //A rescan may change fingerprints; finished platform matches all go stale
        matchedResults.clear()

        scanJob = viewModelScope.launch {
            matching = true
            try {
                scannedVersionName = versionName
                scannedFingerprints = version?.let { ver ->
                    try {
                        scanModFingerprints(VersionFolders.MOD.getDir(ver.getGameDir()))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Logger.warning(TAG, "Failed to scan local mod fingerprints", e)
                        emptyList()
                    }
                } ?: emptyList()

                applyMatches(currentPlatform)
            } finally {
                matching = false
            }
        }
    }

    /**
     * On platform change, the scanned fingerprints re-match against the new platform
     */
    fun onPlatformChanged(platform: Platform) {
        if (currentPlatform == platform) return
        currentPlatform = platform

        //If the scan is still running, its tail auto-matches with the newest platform
        if (scanJob?.isActive == true) return

        scanJob = viewModelScope.launch {
            matching = true
            applyMatches(platform)
            matching = false
        }
    }

    private suspend fun applyMatches(platform: Platform) {
        matchedResults[platform]?.let { (byProject, byVersion) ->
            installedByProject = byProject
            installedByVersion = byVersion
            return
        }

        val fingerprints = scannedFingerprints
        if (fingerprints.isEmpty()) {
            installedByProject = emptyMap()
            installedByVersion = emptyMap()
            return
        }

        val byProject = mutableMapOf<String, InstalledMod>()
        val byVersion = mutableMapOf<String, InstalledMod>()

        fun collect(installed: InstalledMod) {
            if (installed.notFound) return
            byProject[installed.projectId] = installed
            byVersion[installed.versionId] = installed
        }

        //Results sync to the UI as they match
        val matched = matchInstalledMods(fingerprints, platform) { incremental ->
            incremental.byProject.values.forEach(::collect)
            installedByProject = byProject.toMap()
            installedByVersion = byVersion.toMap()
        }

        //Failed chunks skip the session cache and retry on the next platform switch (successful chunks already persist)
        if (matched.complete) matchedResults[platform] = matched.byProject to matched.byVersion
        installedByProject = matched.byProject
        installedByVersion = matched.byVersion
    }

    override fun onCleared() {
        scanJob?.cancel()
    }
}
