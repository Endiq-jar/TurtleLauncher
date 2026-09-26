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

package com.endiq.turtlelauncher.game.download.modpack.platform

import com.endiq.turtlelauncher.utils.GSON
import com.endiq.turtlelauncher.utils.file.locateRealRoot
import com.endiq.turtlelauncher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "SimplePackParser"

/**
 * Simple modpack parser, fitting structurally simple packs characterized by an index/manifest file,
 * letting implementations share this parsing logic
 * @param extraProcess runs after recognition for stricter checking; `true` confirms the format
 */
abstract class SimplePackParser<E: PackManifest>(
    val indexFilePath: String,
    protected val manifestClass: Class<E>,
    private val extraProcess: (suspend (rootFolder: File) -> Boolean)? = null,
    private val buildPack: (rootFolder: File, manifest: E) -> AbstractPack
) : PackParser {

    override suspend fun parse(packFolder: File): AbstractPack? {
        val root = locateRealRoot(packFolder)

        //Modpack index file
        val indexFile = File(root, indexFilePath)
        return withContext(Dispatchers.IO) {
            if (!indexFile.exists()) {
                Logger.debug(TAG, "${getIdentifier()} parser -> manifest file does not exist $indexFile")
                return@withContext null
            }

            //Try reading and recognizing; success means the pack has this format
            val rawString = indexFile.readText()
            val manifest = GSON.fromJson(rawString, manifestClass)

            //Recognized: run the extra logic processing
            if (extraProcess?.invoke(root) == false) {
                //Check failed: rule out this format
                return@withContext null
            }

            buildPack(root, manifest)
        }
    }
}