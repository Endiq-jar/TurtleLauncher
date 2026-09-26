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

package com.endiq.turtlelauncher.game.download.game

import com.google.gson.JsonObject
import com.endiq.turtlelauncher.game.download.game.models.LibraryComponents
import com.endiq.turtlelauncher.game.versioninfo.models.GameManifest
import com.endiq.turtlelauncher.utils.file.ensureDirectory
import com.endiq.turtlelauncher.utils.json.parseToJson
import com.endiq.turtlelauncher.utils.logging.Logger
import java.io.File

private const val TAG = "DownloadGame"

fun GameManifest.isOldVersion(): Boolean = !minecraftArguments.isNullOrEmpty()

/**
 * Tries to read the file at the given path as a JsonObject
 * Throws when the format is invalid
 */
fun String?.getJsonOrNull(tag: String): JsonObject? {
    return this?.let { path ->
        val text: String = File(path).takeIf { it.exists() && it.isFile }?.readText() ?: run {
            Logger.warning(TAG, "The $tag json file is invalid!")
            return@let null
        }
        if (!text.startsWith("{")) {
            Logger.warning(TAG, "The $tag JSON is invalid, first part of the content: ${text.take(1000)}")
            return@let null
        }
        text.parseToJson()
    }
}

/**
 * Copies jar/json files into the temporary game directory as the ModLoader install environment
 * @param sourceGameFolder source game directory
 * @param sourceVersion source game version name
 * @param destinationGameFolder destination game directory
 * @param targetVersion target version name to copy as
 * @param filesToCopy suffixes of the files to copy
 */
fun copyVanillaFiles(
    sourceGameFolder: File,
    sourceVersion: String,
    destinationGameFolder: File,
    targetVersion: String,
    filesToCopy: List<String> = listOf(".json", ".jar")
) {
    val sourceDir = File(sourceGameFolder, "versions/$sourceVersion").ensureDirectory()
    val destinationDir = File(destinationGameFolder, "versions/$targetVersion").ensureDirectory()

    for (extension in filesToCopy) {
        val sourceFile = File(sourceDir, "$sourceVersion$extension")
        val destinationFile = File(destinationDir, "$targetVersion$extension")

        if (!destinationFile.exists() && sourceFile.exists()) {
            sourceFile.copyTo(destinationFile)
        }
    }
}

/**
 * Generates the local path for the given raw library name.
 * @param original the raw library name, e.g. `groupId:artifactId:version`
 * @param baseFolder base folder path used as the path prefix; no joining when null
 */
fun getLibraryPath(
    original: String,
    baseFolder: String? = null
): String {
    val components = parseLibraryComponents(original)

    // Handle the OptiFine special case
    if (isOptiFineLibrary(components.groupId, components.artifactId, components.version)) {
        val specialPath = handleOptiFineSpecialCase(
            baseFolder = baseFolder,
            groupId = components.groupId,
            artifactId = components.artifactId,
            version = components.version
        )
        if (specialPath != null) return specialPath
    }

    val groupIdPath = components.groupId.replace(".", File.separator)
    val classifierSuffix = if (!components.classifier.isNullOrEmpty()) "-${components.classifier}" else ""
    val jarName = "${components.artifactId}-${components.version}$classifierSuffix.jar"

    return listOfNotNull(
        baseFolder?.let { "$it/libraries" },
        groupIdPath,
        components.artifactId,
        components.version,
        jarName
    ).joinToString(File.separator)
}

/**
 * Parses a raw library name string into components (groupId, artifactId, version)
 */
fun parseLibraryComponents(original: String): LibraryComponents {
    val components = original.split(":")
    require(components.size >= 3) { "Invalid library name: $original" }
    return LibraryComponents(
        groupId = components[0],
        artifactId = components[1],
        version = components[2],
        classifier = components.getOrNull(3)
    )
}

private fun buildArtifactPath(
    baseFolder: String?,
    groupId: String,
    artifactId: String,
    version: String,
    jarName: String
): String = buildString {
    baseFolder?.let { append(it).append(File.separator).append("libraries").append(File.separator) }
    append(listOf(groupId, artifactId, version).joinToString(File.separator))
    append(File.separator).append(jarName)
}

private fun isOptiFineLibrary(
    groupId: String,
    artifactId: String,
    version: String
) = groupId == "optifine" && artifactId == "OptiFine" && version.startsWith("1.")

private fun handleOptiFineSpecialCase(
    baseFolder: String?,
    groupId: String,
    artifactId: String,
    version: String
): String? {
    val (major, minor) = parseOptiFineVersion(version)
    if (!shouldUseInstaller(major, minor)) return null

    val installerJarName = "$artifactId-$version-installer.jar"
    val installerPath = buildArtifactPath(baseFolder, groupId, artifactId, version, installerJarName)

    return installerPath.takeIf { File(it).exists() }
}

private fun parseOptiFineVersion(version: String): Pair<Int, Int> {
    val parts = version.split(".", "_")
    return Pair(
        parts.getOrNull(1)?.toIntOrNull() ?: 0,
        parts.getOrNull(2)?.toIntOrNull() ?: 0
    )
}

private fun shouldUseInstaller(major: Int, minor: Int) =
    major == 12 || (major == 20 && minor >= 4) || major >= 21
