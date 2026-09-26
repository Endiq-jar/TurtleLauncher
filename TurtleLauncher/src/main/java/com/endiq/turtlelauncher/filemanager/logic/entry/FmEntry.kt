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

package com.endiq.turtlelauncher.filemanager.logic.entry

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * File / directory entry
 * @param path normalized absolute path
 * @param archiveType extractable archive type; null for non-archives
 */
data class FmEntry(
    val path: Path,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedMs: Long,
    val hidden: Boolean,
    val archiveType: ArchiveType?,
    val writable: Boolean
) {
    val isFile: Boolean get() = !isDirectory
}

/** Archive suffix types */
enum class ArchiveType {
    ZIP,
    SEVEN_Z,
    TAR;

    companion object {
        fun knownSuffix() = listOf(".tar.gz", ".tar.bz2", ".tar.xz", ".zip", ".7z", ".tar")

        fun of(name: String): ArchiveType? {
            val lower = name.lowercase()
            return when {
                lower.endsWith(".zip") -> ZIP
                lower.endsWith(".7z") -> SEVEN_Z
                lower.endsWith(".tar.gz") || lower.endsWith(".tgz") -> TAR
                lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") -> TAR
                lower.endsWith(".tar.xz") || lower.endsWith(".txz") -> TAR
                lower.endsWith(".tar") -> TAR
                else -> null
            }
        }
    }
}

/**
 * Result of a single browse
 * @param ancestors ancestor chain from the root to the current directory (root included, current excluded)
 * @param entries all entries inside the current directory (hidden entries included)
 * @param writable whether the current directory is writable
 */
data class FmListResult(
    val rootDir: Path,
    val currentDir: Path,
    val ancestors: List<Path>,
    val entries: List<FmEntry>,
    val hasSubdirectory: Boolean,
    val writable: Boolean
)

/** Exception thrown when browsing fails */
class BrowseException(message: String, cause: Throwable? = null) : Exception(message, cause)


internal fun Path.toFmEntry(
    attrs: BasicFileAttributes,
): FmEntry {
    val name = fileName?.toString() ?: toString()
    return FmEntry(
        path = this,
        name = name,
        isDirectory = attrs.isDirectory,
        size = if (attrs.isRegularFile) attrs.size() else 0L,
        modifiedMs = attrs.lastModifiedTime().toMillis(),
        hidden = name.startsWith("."),
        archiveType = if (attrs.isRegularFile) ArchiveType.of(name) else null,
        writable = Files.isWritable(this)
    )
}