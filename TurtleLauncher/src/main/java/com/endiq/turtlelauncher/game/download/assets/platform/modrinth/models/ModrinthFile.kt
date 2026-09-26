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

package com.endiq.turtlelauncher.game.download.assets.platform.modrinth.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ModrinthFile(
    /** Map of file hashes: key = hash algorithm, value = hash string */
    @SerialName("hashes")
    val hashes: Hash,

    /** The file's direct URL */
    @SerialName("url")
    val url: String,

    /** File name */
    @SerialName("filename")
    val fileName: String,

    /**
     * Whether the file is its version's primary file. Each version has at most one primary file; with none, the first file counts as primary.
     */
    @SerialName("primary")
    val primary: Boolean,

    /** File size, in bytes */
    @SerialName("size")
    val size: Long,

    /** Type of an additional file, mainly for attaching resource packs to data packs */
    @SerialName("file_type")
    val fileType: String? = null
) {
    @Serializable
    class Hash(
        @SerialName("sha1")
        val sha1: String,

        @SerialName("sha512")
        val sha512: String
    )
}

fun Array<ModrinthFile>.getPrimary(): ModrinthFile? {
    val files = this.takeIf { it.isNotEmpty() } ?: run {
        return null
    }
    return files.find { it.primary } ?: this[0] //only download the primary file
}