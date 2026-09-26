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

package com.endiq.turtlelauncher.game.download.modpack.platform.modrinth

import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.coroutine.Task
import com.endiq.turtlelauncher.game.addons.modloader.ModLoader
import com.endiq.turtlelauncher.game.download.assets.platform.mcim.mapMCIMMirrorUrls
import com.endiq.turtlelauncher.game.download.modpack.install.ModFile
import com.endiq.turtlelauncher.game.download.modpack.install.ModPackInfo
import com.endiq.turtlelauncher.game.download.modpack.install.ModPackInfoTask
import com.endiq.turtlelauncher.game.download.modpack.platform.PackPlatform
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.utils.file.copyDirectoryContents
import java.io.File

/**
 * Modrinth modpack install info
 * @param manifest the Modrinth modpack manifest
 */
class ModrinthPack(
    root: File,
    private val manifest: ModrinthManifest
) : ModPackInfoTask(
    root = root,
    platform = PackPlatform.Modrinth
) {
    /**
     * Reads the Modrinth manifest into a [ModPackInfo] object
     */
    suspend fun readModrinth(
        task: Task,
        targetFolder: File,
        extractFiles: suspend (internalPath: String, outputDir: File) -> Unit
    ): ModPackInfo {
        //Collect all mod files to download
        val files = manifest.files.mapNotNull { manifestFile ->
            //Unsupported by the client
            if (manifestFile.env?.client == "unsupported") return@mapNotNull null
            ModFile(
                outputFile = File(targetFolder, manifestFile.path),
                downloadUrls = manifestFile.downloads.mapMCIMMirrorUrls(),
                sha1 = manifestFile.hashes.sha1
            )
        }

        //Collect loader info
        val loaders = manifest.dependencies.entries.mapNotNull { (id, version) ->
            when (id) {
                "forge" -> ModLoader.FORGE to version
                "neoforge" -> ModLoader.NEOFORGE to version
                "fabric-loader" -> ModLoader.FABRIC to version
                "quilt-loader" -> ModLoader.QUILT to version
                else -> null
            }
        }

        //Extract the override files into the target directory
        task.updateProgress(-1f)
        task.updateMessage(androidText(R.string.download_modpack_install_overrides))
        extractFiles("overrides", targetFolder)
        extractFiles("client-overrides", targetFolder)

        return ModPackInfo(
            name = manifest.name,
            summary = manifest.summary,
            files = files,
            loaders = loaders,
            gameVersion = manifest.getGameVersion()
        )
    }

    override suspend fun readInfo(
        task: Task,
        versionFolder: File,
        root: File
    ): ModPackInfo {
        return readModrinth(
            task = task,
            targetFolder = versionFolder,
            extractFiles = { internalPath, outputDir ->
                val sourceDir = internalPath.takeIf { it.isNotBlank() }
                    ?.let { File(root, it) }
                    ?: root
                if (sourceDir.exists()) {
                    //Extract files
                    copyDirectoryContents(
                        from = sourceDir,
                        to = outputDir,
                        onProgress = { progress ->
                            task.updateProgress(progress)
                        }
                    )
                }
            }
        )
    }
}