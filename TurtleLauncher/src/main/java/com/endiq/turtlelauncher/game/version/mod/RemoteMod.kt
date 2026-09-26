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

package com.endiq.turtlelauncher.game.version.mod

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.endiq.turtlelauncher.game.download.assets.platform.Platform
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformClasses
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformVersion
import com.endiq.turtlelauncher.game.download.assets.platform.curseforge.models.CurseForgeFile
import com.endiq.turtlelauncher.game.download.assets.platform.curseforge.models.CurseForgeModLoader
import com.endiq.turtlelauncher.game.download.assets.platform.getProjectByVersion
import com.endiq.turtlelauncher.game.download.assets.platform.getVersionByLocalFile
import com.endiq.turtlelauncher.game.download.assets.platform.modrinth.models.ModrinthModLoaderCategory
import com.endiq.turtlelauncher.game.download.assets.platform.modrinth.models.ModrinthVersion
import com.endiq.turtlelauncher.game.download.assets.utils.ModTranslations
import com.endiq.turtlelauncher.game.download.assets.utils.getMcMod
import com.endiq.turtlelauncher.game.download.assets.utils.getTranslations
import com.endiq.turtlelauncher.utils.file.calculateFileSha1
import com.endiq.turtlelauncher.utils.logging.Logger
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private const val TAG = "RemoteMod"

class RemoteMod(
    val localMod: LocalMod
) {
    /**
     * Whether project info is loading
     */
    var isLoading by mutableStateOf(false)
        private set

    /**
     * Files on the platform
     */
    var remoteFile: ModFile? by mutableStateOf(null)
        private set

    /**
     * Project info
     */
    var projectInfo: ModProject? by mutableStateOf(null)
        private set

    /**
     * Project translation info
     */
    var mcMod: ModTranslations.McMod? by mutableStateOf(null)
        private set

    /**
     * Whether it has been loaded before
     */
    var isLoaded: Boolean = false
        private set

    /**
     * @param loadFromCache whether to load from cache
     */
    suspend fun load(loadFromCache: Boolean) {
        if (loadFromCache && isLoaded) return

        if (!localMod.checkRemote) {
            isLoaded = true
            return
        }

        if (!loadFromCache) {
            remoteFile = null
            projectInfo = null
            mcMod = null
        }

        isLoaded = false
        isLoading = true

        try {
            withContext(Dispatchers.IO) {
                val file = localMod.file
                val modProjectCache = modProjectCache()
                val modFileCache = modFileCache()

                runCatching {
                    //Get the file sha1 as the cache key
                    val sha1 = calculateFileSha1(file)

                    //Load project info from cache
                    val cachedProject = if (loadFromCache) {
                        modProjectCache.decodeParcelable(sha1, ModProject::class.java)
                    } else null

                    //Load file info from cache
                    val cachedFile = if (loadFromCache) {
                        modFileCache.decodeParcelable(sha1, ModFile::class.java)
                    } else null

                    if (loadFromCache && cachedFile != null) {
                        remoteFile = cachedFile
                    } else {
                        loadRemoteFile(sha1)?.let { modFile ->
                            remoteFile = modFile
                            modFileCache.encode(sha1, modFile, MMKV.ExpireInDay)
                        }
                    }

                    if (loadFromCache && cachedProject != null) {
                        projectInfo = cachedProject
                        mcMod = PlatformClasses.MOD.getTranslations().getModBySlugId(cachedProject.slug)
                    } else {
                        ensureActive()
                        remoteFile?.let { modFile ->
                            val project = getProjectByVersion(
                                projectId = modFile.projectId,
                                platform = modFile.platform,
                                printLog = false
                            )
                            val newProjectInfo = ModProject(
                                id = project.platformId(),
                                platform = project.platform(),
                                iconUrl = project.platformIconUrl(),
                                title = project.platformTitle(),
                                slug = project.platformSlug()
                            )

                            projectInfo = newProjectInfo
                            mcMod = project.getMcMod(PlatformClasses.MOD)

                            modProjectCache.encode(sha1, newProjectInfo, MMKV.ExpireInDay)
                        }
                    }

                    isLoaded = true
                }.onFailure { e ->
                    if (e is CancellationException) return@onFailure
                    Logger.warning(TAG, "Failed to load project info for mod: ${file.name}", e)
                }
            }
        } finally {
            isLoading = false
        }
    }

    suspend fun loadRemoteFile(
        sha1: String? = null
    ): ModFile? {
        val file = localMod.file
        val sha10 = sha1 ?: calculateFileSha1(file)
        val version = getVersionByLocalFile(file, sha10)
        return version?.toModFile()
    }

    private fun PlatformVersion.toModFile(): ModFile {
        return when (this) {
            is ModrinthVersion -> {
                ModFile(
                    id = id,
                    projectId = projectId,
                    platform = Platform.MODRINTH,
                    loaders = loaders.mapNotNull { loaderName ->
                        ModrinthModLoaderCategory.entries.find { it.facetValue() == loaderName }
                    }.toTypedArray(),
                    datePublished = datePublished
                )
            }
            is CurseForgeFile -> {
                ModFile(
                    id = id.toString(),
                    projectId = modId.toString(),
                    platform = Platform.CURSEFORGE,
                    loaders = gameVersions.mapNotNull { loaderName ->
                        CurseForgeModLoader.entries.find {
                            it.getDisplayName().equals(loaderName, true)
                        }
                    }.toTypedArray(),
                    datePublished = fileDate
                )
            }
            else -> error("Unknown version type: $this")
        }
    }
}