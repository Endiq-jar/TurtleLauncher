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

import com.endiq.turtlelauncher.game.addons.modloader.ModLoader
import com.endiq.turtlelauncher.game.addons.modloader.fabriclike.fabric.FabricVersions
import com.endiq.turtlelauncher.game.addons.modloader.fabriclike.quilt.QuiltVersions
import com.endiq.turtlelauncher.game.addons.modloader.forgelike.forge.ForgeVersions
import com.endiq.turtlelauncher.game.addons.modloader.forgelike.neoforge.NeoForgeVersions
import com.endiq.turtlelauncher.game.download.game.GameDownloadInfo

/**
 * Modpack info
 * @param name modpack name
 * @param summary modpack summary (usable as the version description)
 * @param ram memory allocation recommended by the modpack
 * @param files all mods the modpack needs downloaded
 * @param loaders mod loaders the modpack needs installed
 * @param gameVersion game version the modpack needs
 */
data class ModPackInfo(
    val name: String,
    val summary: String? = null,
    val ram: Int? = null,
    val files: List<ModFile>,
    val loaders: List<Pair<ModLoader, String>>,
    val gameVersion: String
)

/**
 * Mod loader resolution/matching task
 * @return the built game download/install info
 */
suspend fun ModPackInfo.retrieveLoaderTask(
    targetVersionName: String
): GameDownloadInfo {
    var gameInfo = GameDownloadInfo(
        gameVersion = gameVersion,
        customVersionName = targetVersionName
    )

    //Match the target loader version and fetch its details
    loaders.forEach { pair ->
        pair.retrieveLoader(
            gameVersion = gameVersion,
            gameInfo = gameInfo,
            pasteGameInfo = { newInfo ->
                gameInfo = newInfo
            }
        )
    }

    return gameInfo
}

/**
 * Resolves and matches mod loaders, pasting them into the game download info
 * @param gameVersion the current game version
 * @param pasteGameInfo pastes the matched mod loader version back into the info class
 */
suspend fun Pair<ModLoader, String>.retrieveLoader(
    gameVersion: String,
    gameInfo: GameDownloadInfo,
    pasteGameInfo: (GameDownloadInfo) -> Unit
) {
    val (loader, version) = this
    when (loader) {
        ModLoader.FORGE -> {
            ForgeVersions.fetchForgeList(gameVersion)?.find {
                it.versionName == version
            }?.let { forgeVersion ->
                pasteGameInfo(gameInfo.copy(forge = forgeVersion))
            }
        }
        ModLoader.NEOFORGE -> {
            NeoForgeVersions.fetchNeoForgeList(gameVersion = gameVersion)?.find {
                it.versionName == version
            }?.let { neoforgeVersion ->
                pasteGameInfo(gameInfo.copy(neoforge = neoforgeVersion))
            }
        }
        ModLoader.FABRIC -> {
            FabricVersions.fetchFabricLoaderList(gameVersion)?.find {
                it.version == version
            }?.let { fabricVersion ->
                pasteGameInfo(gameInfo.copy(fabric = fabricVersion))
            }
        }
        ModLoader.QUILT -> {
            QuiltVersions.fetchQuiltLoaderList(gameVersion)?.find {
                it.version == version
            }?.let { quiltVersion ->
                pasteGameInfo(gameInfo.copy(quilt = quiltVersion))
            }
        }
        else -> {
            //Unsupported
        }
    }
}