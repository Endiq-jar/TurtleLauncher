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

package com.endiq.turtlelauncher.filemanager.logic

import com.endiq.turtlelauncher.filemanager.logic.ops.deleteRecursivePath
import java.nio.file.Files
import java.nio.file.Path

private const val COMPRESS_SUBDIR = "fileManagerCompress"
private const val EXTRACT_SUBDIR = "fileManagerExtract"
private const val IMPORT_SUBDIR = "fileManagerImport"

/** Temporary workspace */
class TempWorkspace(private val cacheRoot: Path) {
    /** Generates a temporary file path for compression */
    fun compressTempFile(extension: String): Path {
        val dir = cacheRoot.resolve(COMPRESS_SUBDIR)
        runCatching {
            Files.createDirectories(dir)
        }
        return dir.resolve("tmp_${System.nanoTime()}.$extension")
    }

    /** Generates a temporary directory path for extraction */
    fun extractTempDir(): Path = uniqueDir(EXTRACT_SUBDIR)

    /** Generates a temporary directory path for imports */
    fun importTempDir(): Path = uniqueDir(IMPORT_SUBDIR)

    /** Recursively deletes a temporary file or directory */
    suspend fun delete(path: Path) {
        deleteRecursivePath(path)
    }

    /** Checks whether a path lies inside the temporary workspace */
    fun isInside(path: Path): Boolean {
        val normalized = path.normalize().toAbsolutePath()
        return normalized.startsWith(cacheRoot)
    }

    private fun uniqueDir(sub: String): Path {
        val base = cacheRoot.resolve(sub)
        runCatching {
            Files.createDirectories(base)
        }
        return base.resolve("tmp_${System.nanoTime()}")
    }
}
