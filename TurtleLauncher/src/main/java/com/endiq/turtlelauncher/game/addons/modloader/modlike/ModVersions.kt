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

package com.endiq.turtlelauncher.game.addons.modloader.modlike

import com.endiq.turtlelauncher.game.download.assets.platform.mirroredModrinthSource
import com.endiq.turtlelauncher.game.download.assets.platform.mirroredPlatformSearcher
import com.endiq.turtlelauncher.game.download.assets.platform.modrinth.models.ModrinthVersion
import com.endiq.turtlelauncher.game.download.assets.platform.modrinth.models.getPrimary
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.initAllGeneric
import com.endiq.turtlelauncher.utils.logging.Logger
import kotlinx.coroutines.CancellationException

private const val TAG = "ModVersions"

/**
 * Mod version management class
 * @param modrinthID the mod's ID on the Modrinth platform
 */
abstract class ModVersions(
    private val modrinthID: String
) {
    private var cacheVersions: List<ModrinthVersion>? = null

    /**
     * Fetches the mod list for a specific version
     */
    suspend fun fetchVersionList(
        mcVersion: String,
        force: Boolean = false
    ): List<ModVersion>? {
        try {
            val versions = run {
                if (!force && cacheVersions != null) return@run cacheVersions!!
                mirroredPlatformSearcher(
                    searchers = mirroredModrinthSource()
                ) { searcher ->
                    searcher.getVersions(
                        projectID = modrinthID
                    ).initAllGeneric(
                        currentProjectId = modrinthID
                    )
                }.also {
                    cacheVersions = it
                }
            }

            return versions.mapNotNull { version ->
                //Keep only mod versions with a matching version number
                if (!version.gameVersions.contains(mcVersion)) return@mapNotNull null
                //Keep only the main file
                val file = version.files.getPrimary() ?: run {
                    Logger.warning(TAG, "No file list available, skipping -> ${version.name}")
                    return@mapNotNull null
                }
                ModVersion(
                    inherit = mcVersion,
                    displayName = version.versionNumber,
                    version = version,
                    file = file
                )
            }
        } catch (_: CancellationException) {
            Logger.debug(TAG, "Client cancelled.")
            return null
        } catch (e: Exception) {
            Logger.debug(TAG, "Failed to fetch mod list! {mod id = $modrinthID}", e)
            throw e
        }
    }
}