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

package com.endiq.turtlelauncher.game.emotes

import com.endiq.turtlelauncher.game.addons.modloader.ModLoader
import com.endiq.turtlelauncher.game.version.installed.Version
import com.endiq.turtlelauncher.game.version.installed.VersionFolders
import com.endiq.turtlelauncher.path.GLOBAL_CLIENT
import com.endiq.turtlelauncher.utils.logging.Logger
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest

/** A single Emotecraft release entry as returned by the Modrinth API. */
@Serializable
data class EmotecraftVersion(
    val name: String = "",
    @SerialName("version_number") val versionNumber: String = "",
    @SerialName("game_versions") val gameVersions: List<String> = emptyList(),
    val loaders: List<String> = emptyList(),
    val files: List<EmotecraftFile> = emptyList()
) {
    val displayName: String
        get() = name.ifBlank { versionNumber }
}

@Serializable
data class EmotecraftFile(
    val url: String = "",
    val filename: String = "",
    val primary: Boolean = false,
    val hashes: Map<String, String> = emptyMap()
)

/**
 * Installs the [Emotecraft](https://modrinth.com/mod/emotecraft) mod into
 * game instances so players can use emotes in-game.
 */
object EmotesManager {
    private const val TAG = "Emotes"
    const val MODRINTH_PROJECT = "emotecraft"
    private const val MODRINTH_VERSIONS_URL =
        "https://api.modrinth.com/v2/project/$MODRINTH_PROJECT/version"

    /** Fetches every published Emotecraft release from Modrinth (newest first). */
    suspend fun fetchVersions(): List<EmotecraftVersion> = withContext(Dispatchers.IO) {
        GLOBAL_CLIENT.get(MODRINTH_VERSIONS_URL).body()
    }

    /** Maps the launcher's mod loader to the Modrinth loader slug, when possible. */
    fun modrinthLoaderSlug(loader: ModLoader?): String? = when (loader) {
        ModLoader.FABRIC, ModLoader.LEGACY_FABRIC, ModLoader.BABRIC -> "fabric"
        ModLoader.QUILT -> "quilt"
        ModLoader.FORGE, ModLoader.CLEANROOM -> "forge"
        ModLoader.NEOFORGE -> "neoforge"
        else -> null
    }

    /**
     * Picks the newest release compatible with [mcVersion] and
     * [loaderSlug]. Falls back to looser matches when an exact
     * game-version match does not exist.
     */
    fun pickBestVersion(
        all: List<EmotecraftVersion>,
        mcVersion: String?,
        loaderSlug: String?
    ): EmotecraftVersion? {
        var candidates = all.filter { it.files.isNotEmpty() && it.versionNumber.isNotBlank() }
        val slugged = loaderSlug?.let { slug ->
            candidates.filter { v -> v.loaders.any { it.equals(slug, ignoreCase = true) } }
        } ?: candidates
        if (slugged.isNotEmpty()) candidates = slugged
        mcVersion?.let { mc ->
            val exact = candidates.filter { mc in it.gameVersions }
            if (exact.isNotEmpty()) return exact.first()
        }
        return candidates.firstOrNull()
    }

    /**
     * Downloads and installs [emotecraft] into [version]'s mods folder.
     * Also creates the `emotes/` folder Emotecraft uses for custom emote packs.
     * @return the installed mod file
     */
    suspend fun installInto(
        version: Version,
        emotecraft: EmotecraftVersion
    ): File = withContext(Dispatchers.IO) {
        val gameDir = version.getGameDir()
        val modsDir = File(VersionFolders.MOD.getDir(gameDir.absolutePath))
        if (!modsDir.exists()) modsDir.mkdirs()

        //drop previously installed Emotecraft jars to avoid duplicates
        modsDir.listFiles()
            ?.filter { it.name.startsWith(MODRINTH_PROJECT, ignoreCase = true) && it.extension == "jar" }
            ?.forEach { it.delete() }

        val file = emotecraft.files.firstOrNull { it.primary } ?: emotecraft.files.first()
        val bytes = GLOBAL_CLIENT.get(file.url).body<ByteArray>()

        file.hashes["sha1"]?.let { expected ->
            val actual = sha1(bytes)
            if (!actual.equals(expected, ignoreCase = true)) {
                error("SHA-1 mismatch for ${file.filename} (expected $expected, got $actual)")
            }
        }

        val target = File(modsDir, file.filename)
        target.writeBytes(bytes)
        Logger.info(TAG, "Installed ${file.filename} into ${modsDir.absolutePath}")

        //Emotecraft loads custom emote packs from ./emotes
        File(gameDir, "emotes").mkdirs()

        target
    }

    private fun sha1(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
