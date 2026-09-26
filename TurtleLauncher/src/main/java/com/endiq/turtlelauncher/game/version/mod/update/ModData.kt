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

import com.endiq.turtlelauncher.game.addons.modloader.ModLoader
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformVersion
import com.endiq.turtlelauncher.game.download.assets.platform.getVersions
import com.endiq.turtlelauncher.game.download.assets.utils.ModTranslations
import com.endiq.turtlelauncher.game.version.mod.ModFile
import com.endiq.turtlelauncher.game.version.mod.ModProject
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.initAll
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.string.parseInstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "ModData"

/**
 * Data class of a mod that needs an update, recording the mod file and its project
 * @param modFile the mod's file on the platform
 * @param project the mod's project on the platform
 * @param mcMod the mod translation info
 */
data class ModData(
    val file: File,
    val modFile: ModFile,
    val project: ModProject,
    val mcMod: ModTranslations.McMod?
) {
    /**
     * The current mod's version number, for old-vs-new comparison
     */
    var currentVersion: String? = null
        private set

    /**
     * Checks for mod updates
     * @param minecraftVer the MC version, used to filter versions
     * @param modLoader mod loader info, used to filter versions
     */
    suspend fun checkUpdate(
        minecraftVer: String,
        modLoader: ModLoader
    ): PlatformVersion? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val datePublished = parseInstant(modFile.datePublished)
                val projectId = project.id
                val currentLoaderName = modLoader.displayName.lowercase()
                val currentFileLoaders = modFile.loaders
                    .map { it.getDisplayName().lowercase() }
                    .toSet()
                val targetLoaders = when {
                    currentLoaderName in currentFileLoaders -> {
                        // The current mod file supports the current game loader: check updates only on the current loader channel
                        setOf(currentLoaderName)
                    }

                    currentFileLoaders.isNotEmpty() -> {
                        // The current mod file doesn't support the current game loader (e.g. the Sinytra Connector scenario):
                        // Prefer the loader channel the mod file itself supports when checking updates
                        currentFileLoaders
                    }

                    else -> {
                        // When the file's loader can't be identified, fall back to the current game loader
                        setOf(currentLoaderName)
                    }
                }

                // Fetch all versions and initialize
                val versions = getVersions(
                    projectId,
                    project.platform
                ).initAll(projectId)
                    .filter { version ->
                        if (version.platformId() == modFile.id) {
                            // Current version: set the version number
                            currentVersion = version.platformVersion()
                        }
                        val loaderNames = version.platformLoaders()
                            .map { it.getDisplayName().lowercase() }
                            .toSet()
                        // Whether it supports the current MC version
                        minecraftVer in version.platformGameVersion() &&
                        // Whether it matches the target loader (the current loader, or the file's own loader)
                        loaderNames.any { it in targetLoaders } &&
                        // Whether it's newer than the current one
                        version.platformDatePublished() > datePublished
                    }

                // Take the newest version
                versions.firstOrNull()?.also { version ->
                    Logger.info(TAG, "Detected update for mod ${file.name}: $currentVersion -> ${version.platformVersion()}")
                }
            }.onFailure { th ->
                Logger.warning(TAG, "An error occurred while fetching all versions of the mod.", th)
            }.getOrNull()
        }
    }
}
