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

package com.endiq.turtlelauncher.game.versioninfo

import com.google.gson.reflect.TypeToken
import com.endiq.turtlelauncher.game.addons.mirror.mapBMCLMirrorUrls
import com.endiq.turtlelauncher.game.versioninfo.models.VersionManifest
import com.endiq.turtlelauncher.game.versioninfo.models.mapVersion
import com.endiq.turtlelauncher.path.PathManager
import com.endiq.turtlelauncher.path.URL_MINECRAFT_VERSION_REPOS
import com.endiq.turtlelauncher.utils.GSON
import com.endiq.turtlelauncher.utils.file.readString
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.network.fetchStringFromUrls
import com.endiq.turtlelauncher.utils.network.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.util.concurrent.TimeUnit

private const val TAG = "MinecraftVersions"

object MinecraftVersions {
    private var manifest: VersionManifest? = null

    private val _allVersions = MutableStateFlow<List<MinecraftVersion>>(emptyList())
    val allVersions = _allVersions.asStateFlow()

    /**
     * Refreshes the Minecraft version list
     * @param force forces downloading a fresh version list
     */
    @Throws(IllegalStateException::class)
    suspend fun refreshVersions(force: Boolean = false) {
        if (!force && _allVersions.value.isNotEmpty()) return

        val vm = if (force) {
            getVersionManifest(force = true)
        } else {
            manifest ?: getVersionManifest(force = false)
        }
        val versions = vm.versions.mapVersion()
        _allVersions.update { versions }
    }

    /**
     * Returns the Minecraft version info list
     * @param force forces downloading a fresh version list
     */
    @Throws(IllegalStateException::class)
    suspend fun getVersionManifest(force: Boolean = false): VersionManifest {
        manifest?.takeIf { !force }?.let { return it }

        return withContext(Dispatchers.IO) {
            val localManifestFile = PathManager.FILE_MINECRAFT_VERSIONS
            val isOutdated = !localManifestFile.exists() || !localManifestFile.isFile ||
                    //Refresh the version info list once a day
                    localManifestFile.lastModified() + TimeUnit.DAYS.toMillis(1) < System.currentTimeMillis()

            val newManifest = if (force || isOutdated) {
                downloadVersionManifest()
            } else {
                try {
                    GSON.fromJson(localManifestFile.readText(), VersionManifest::class.java)
                } catch (e: Exception) {
                    Logger.warning(TAG, "Failed to parse version manifest, will redownload", e)
                    //On read failure, delete the current version info file
                    FileUtils.deleteQuietly(localManifestFile)
                    downloadVersionManifest()
                }
            }

            val newManifest0 = newManifest ?: throw IllegalStateException("Version manifest is null after all attempts")
            mergeUnlistVersions(newManifest0) ?: newManifest0
        }.also { newManifest ->
            manifest = newManifest
        }
    }

    /**
     * Fetches version info from the official version repository
     */
    private suspend fun downloadVersionManifest(): VersionManifest {
        return withContext(Dispatchers.IO) {
            withRetry("MinecraftVersions", maxRetries = 2) {
                val rawJson = fetchStringFromUrls(URL_MINECRAFT_VERSION_REPOS.mapBMCLMirrorUrls())
                val versionManifest = GSON.fromJson(rawJson, VersionManifest::class.java)
                PathManager.FILE_MINECRAFT_VERSIONS.writeText(rawJson)
                versionManifest
            }
        }
    }

    /**
     * Tries to merge locally the versions the officials have hidden
     */
    private suspend fun mergeUnlistVersions(
        currentManifest: VersionManifest
    ): VersionManifest? {
        return withContext(Dispatchers.IO) {
            MinecraftVersions::class.java.getResourceAsStream("/assets/game/unlist_versions.json")?.use { input ->
                input.readString()
            }?.let { unlistVersionJson ->
                GSON.fromJson<List<VersionManifest.Version>>(
                    unlistVersionJson,
                    object : TypeToken<List<VersionManifest.Version>>() {}.type
                )
            }?.let { unlistVersions ->
                val versions = currentManifest.versions.toMutableList()
                versions.addAll(unlistVersions)
                versions.sortWith { version, other ->
                    other.releaseTime.compareTo(version.releaseTime)
                }

                currentManifest.copy(
                    versions = versions.toList()
                )
            }
        }
    }
}