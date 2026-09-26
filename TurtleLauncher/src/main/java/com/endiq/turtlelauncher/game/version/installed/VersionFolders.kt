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

package com.endiq.turtlelauncher.game.version.installed

import java.io.File

enum class VersionFolders(val folderName: String) {
    /** No target folder */
    NONE(""),
    /** Mods folder */
    MOD("mods"),
    /** Resource packs folder */
    RESOURCE_PACK("resourcepacks"),
    /** Saves folder */
    SAVES("saves"),
    /** Shader packs folder */
    SHADERS("shaderpacks"),
    /** Screenshots folder */
    SCREENSHOTS("screenshots");

    fun getDir(gameDir: String): String {
        return File(gameDir, folderName).absolutePath
    }

    fun getDir(gameDir: File): File {
        return File(gameDir, folderName)
    }
}