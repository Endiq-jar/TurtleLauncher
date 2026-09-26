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
import com.endiq.turtlelauncher.game.addons.modloader.ModLoader
import com.endiq.turtlelauncher.utils.logging.Logger
import kotlinx.io.IOException
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val TAG = "LocalMod"

/** Local mod info */
class LocalMod(
    /** The local mod's file */
    modFile: File,

    /** The local mod's file size */
    val fileSize: Long,

    /** Mod ID */
    val id: String,

    /** The loader the mod belongs to */
    val loader: ModLoader,

    /** The mod's display name */
    val name: String,

    /** Mod description */
    val description: String? = null,

    /** Mod version */
    val version: String? = null,

    /** The mod's author list */
    val authors: List<String>,

    /** The mod's icon */
    val icon: ByteArray? = null,

    /**
     * Flags whether this is a non-mod
     */
    val notMod: Boolean = false,

    /**
     * Whether to fetch mod info remotely
     */
    val checkRemote: Boolean = true,
) {
    var file by mutableStateOf(modFile)
        private set

    /**
     * Disables the mod
     */
    fun disable() {
        val currentPath = file.absolutePath
        if (file.isDisabled()) return

        val newFile = File("$currentPath.disabled")
        if (!file.renameToSafely(newFile)) return

        file = newFile
    }

    /**
     * Enables the mod
     */
    fun enable() {
        val newFile = enabledMod(file)
        if (!file.renameToSafely(newFile)) return

        file = newFile
    }

    private fun File.renameToSafely(dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            Files.move(
                this.toPath(),
                dest.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            true
        } catch (e: IOException) {
            Logger.warning(TAG, "Failed to rename file {$this} to $dest!", e)
            false
        }
    }
}

/**
 * Whether the mod is enabled
 */
fun File.isEnabled(): Boolean = !absolutePath.endsWith(".disabled", ignoreCase = true)

/**
 * Whether the mod is disabled
 */
fun File.isDisabled(): Boolean = !this.isEnabled()

/**
 * Creates a non-mod file
 */
fun createNotMod(file: File): LocalMod = LocalMod(
    modFile = file,
    fileSize = FileUtils.sizeOf(file),
    id = "",
    loader = ModLoader.UNKNOWN,
    name = file.name,
    description = null,
    version = null,
    authors = emptyList(),
    icon = null,
    notMod = true
)

fun enabledMod(file: File): File {
    if (file.isEnabled()) return file

    val currentPath = file.absolutePath
    val newPath = currentPath.dropLast(".disabled".length)
    return File(newPath)
}
