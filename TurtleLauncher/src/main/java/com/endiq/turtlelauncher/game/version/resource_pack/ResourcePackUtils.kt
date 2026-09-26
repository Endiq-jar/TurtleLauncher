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

package com.endiq.turtlelauncher.game.version.resource_pack

import com.endiq.turtlelauncher.game.version.mod.meta.PackMcMeta
import com.endiq.turtlelauncher.utils.GSON
import com.endiq.turtlelauncher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.zip.ZipFile

private const val TAG = "ResourcePackUtils"

/**
 * Parses a resource pack; the game only loads folder packs or zip packs
 * @param file the resource pack file
 */
suspend fun parseResourcePack(file: File): ResourcePackInfo? = withContext(Dispatchers.IO) {
    runCatching {
        var isValid = false
        var metaContent: String? = null
        var iconBytes: ByteArray? = null
        var fileSize: Long? = null

        if (file.isDirectory) { //Resource pack in folder form
            //Resource pack metadata
            File(file, "pack.mcmeta").takeIf { it.exists() }?.let { metaFile ->
                metaContent = metaFile.readText()
            }
            //Try reading the resource pack's icon
            File(file, "pack.png").takeIf { it.exists() }?.let { iconFile ->
                iconBytes = iconFile.readBytes()
            }
        } else if (file.extension == "zip") { //Resource pack in archive form
            //For performance, only archive resource packs get a computed file size
            fileSize = FileUtils.sizeOf(file)

            ZipFile(file).use { zip ->
                //Resource pack metadata
                zip.getEntry("pack.mcmeta")?.let { metaEntry ->
                    metaContent = zip.getInputStream(metaEntry).bufferedReader().readText()
                }
                //Try reading the resource pack's icon
                zip.getEntry("pack.png")?.let { iconEntry ->
                    iconBytes = zip.getInputStream(iconEntry).readBytes()
                }
            }
        }

        val meta = metaContent?.let { content ->
            runCatching {
                GSON.fromJson(content, PackMcMeta::class.java)
            }.onFailure {
                Logger.warning(TAG, "Failed to parse the resource package metadata: ${file.absolutePath}", it)
            }.getOrNull()
        }?.also {
            //Successful parsing means a valid format
            isValid = true
        }

        ResourcePackInfo(
            file = file,
            fileSize = fileSize,
            isValid = isValid,
            description = meta?.pack?.description?.toPlainText(),
            packFormat = meta?.pack?.packFormat,
            icon = iconBytes
        )
    }.onFailure {
        Logger.warning(TAG, "Failed to parse the resource package: ${file.absolutePath}", it)
    }.getOrNull()
}