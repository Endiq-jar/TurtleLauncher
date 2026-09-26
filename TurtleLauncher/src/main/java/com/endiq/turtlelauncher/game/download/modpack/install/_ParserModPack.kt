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

import com.endiq.turtlelauncher.coroutine.Task
import com.endiq.turtlelauncher.game.download.assets.platform.Platform
import com.endiq.turtlelauncher.game.download.modpack.platform.AbstractPack
import com.endiq.turtlelauncher.game.download.modpack.platform.curseforge.CurseForgeManifest
import com.endiq.turtlelauncher.game.download.modpack.platform.curseforge.CurseForgePack
import com.endiq.turtlelauncher.game.download.modpack.platform.modrinth.ModrinthManifest
import com.endiq.turtlelauncher.game.download.modpack.platform.modrinth.ModrinthPack
import com.endiq.turtlelauncher.utils.GSON
import com.endiq.turtlelauncher.utils.file.extractFromZip
import com.endiq.turtlelauncher.utils.file.readText
import com.endiq.turtlelauncher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import org.apache.commons.compress.archivers.zip.ZipFile as ApacheZipFile
import java.util.zip.ZipFile as JdkZipFile

private const val TAG = "ParserModPack"

/**
 * Modpack parsing config unifying the parse flow
 * @param manifestPath the manifest file's location inside the archive
 * @param manifestType the manifest's type (class for deserialization)
 * @param createPack builds the generic modpack format from the manifest
 * @param readPack builds [ModPackInfo] from the created generic format
 */
private data class PackParserConfig<M, P : AbstractPack>(
    val manifestPath: String,
    val manifestType: Class<M>,
    val createPack: (manifest: M) -> P,
    val readPack: suspend P.(Task, File, suspend (String, File) -> Unit) -> ModPackInfo
)

/**
 * Only for built-in online downloads; parses different modpack types via [Platform]
 * @return the modpack's online-download mod info
 */
suspend fun parserModPack(
    file: File,
    platform: Platform,
    targetFolder: File,
    task: Task
): ModPackInfo = withContext(Dispatchers.IO) {
    //root isn't needed here, since we're only fetching the ModPackInfo object
    //an emptyFile placeholder is enough; it's just about creating the object
    val emptyFile = File("")

    when (platform) {
        Platform.CURSEFORGE -> {
            val config = PackParserConfig(
                manifestPath = "manifest.json",
                manifestType = CurseForgeManifest::class.java,
                createPack = { CurseForgePack(root = emptyFile, it) },
                readPack = { task, target, extract ->
                    readCurseForge(task, target, extract)
                }
            )
            parseModPackGeneric(file, targetFolder, task, config)
        }
        Platform.MODRINTH -> {
            val config = PackParserConfig(
                manifestPath = "modrinth.index.json",
                manifestType = ModrinthManifest::class.java,
                createPack = { ModrinthPack(root = emptyFile, it) },
                readPack = { task, target, extract ->
                    readModrinth(task, target, extract)
                }
            )
            parseModPackGeneric(file, targetFolder, task, config)
        }
    }
}

/**
 * Tries parsing with [JdkZipFile], falling back to [ApacheZipFile] on trouble
 */
suspend fun <T> withZipFile(
    file: File,
    loaderJdk: suspend (JdkZipFile) -> T,
    loaderApache: suspend (ApacheZipFile) -> T
): T {
    return withContext(Dispatchers.IO) {
        try {
            JdkZipFile(file).use { loaderJdk(it) }
        } catch (e: Exception) {
            Logger.warning(TAG, "JDK ZipFile failed to parse ${file.name}, fallback to Apache ZipFile.", e)
            ApacheZipFile.builder().setFile(file).get().use { loaderApache(it) }
        }
    }
}

/**
 * Generic modpack parsing logic
 */
private suspend fun <M, P : AbstractPack> parseModPackGeneric(
    file: File,
    targetFolder: File,
    task: Task,
    config: PackParserConfig<M, P>
): ModPackInfo = withZipFile(
    file = file,
    loaderJdk = { zip ->
        task.updateProgress(-1f)

        val json = zip.readText(config.manifestPath)
        val manifest = GSON.fromJson(json, config.manifestType)
        val pack = config.createPack(manifest)

        config.readPack(pack, task, targetFolder) { internal, out ->
            zip.extractFromZip(internal, out)
        }
    },
    loaderApache = { zip ->
        task.updateProgress(-1f)

        val entry = zip.getEntry(config.manifestPath)
            ?: throw IOException("${config.manifestPath} not found in ${file.name}")

        val json = zip.getInputStream(entry).bufferedReader().readText()
        val manifest = GSON.fromJson(json, config.manifestType)
        val pack = config.createPack(manifest)

        config.readPack(pack, task, targetFolder) { internal, out ->
            zip.extractFromZip(internal, out)
        }
    }
)