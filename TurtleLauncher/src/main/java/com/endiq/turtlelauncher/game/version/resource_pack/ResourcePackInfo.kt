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

import com.endiq.turtlelauncher.utils.string.stripColorCodes
import java.io.File

/**
 * Resource pack info class
 */
data class ResourcePackInfo(
    /** The resource pack file */
    val file: File,
    /** Precomputed file size (folder resource packs have no computed size) */
    val fileSize: Long? = null,
    /** File name with color codes stripped */
    val rawName: String = file.name.stripColorCodes(),
    /** Display name (archive resource packs have their extension removed) */
    val displayName: String = if (file.isDirectory) file.name else file.nameWithoutExtension,
    /** Whether the resource pack is valid */
    val isValid: Boolean,
    /** The resource pack's description */
    val description: String?,
    /** The resource pack's format version */
    val packFormat: Int?,
    /** The resource pack's icon */
    val icon: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ResourcePackInfo

        if (isValid != other.isValid) return false
        if (packFormat != other.packFormat) return false
        if (file != other.file) return false
        if (description != other.description) return false
        if (icon != null) {
            if (other.icon == null) return false
            if (!icon.contentEquals(other.icon)) return false
        } else if (other.icon != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = isValid.hashCode()
        result = 31 * result + (packFormat ?: 0)
        result = 31 * result + file.hashCode()
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + (icon?.contentHashCode() ?: 0)
        return result
    }
}