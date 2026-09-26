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

package com.endiq.turtlelauncher.filemanager.logic.ops

import com.endiq.turtlelauncher.filemanager.os.FmLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.ArrayDeque

private const val TAG = "FmDirScanner"

/** Directory statistics */
data class DirStats(
    val totalSize: Long = 0L,
    val fileCount: Long = 0L,
    val dirCount: Long = 0L
)

object DirectoryScanner {
    /**
     * Iteratively scans the [root] directory, tallying size and count
     * @param onProgress progress callback (current stats and the directory being scanned)
     * @param checkCancelled cancellation check; returning true throws CancellationException
     * @return the directory statistics
     */
    suspend fun scan(
        root: Path,
        onProgress: ((stats: DirStats, current: Path?) -> Unit)? = null,
        checkCancelled: () -> Boolean = { false }
    ): DirStats = withContext(Dispatchers.IO) {
        var stats = DirStats()
        val stack = ArrayDeque<Path>()
        stack.push(root)

        while (stack.isNotEmpty()) {
            if (checkCancelled()) throw CancellationException("Directory scan cancelled")
            val dir = stack.pop()

            runCatching {
                Files.newDirectoryStream(dir).use { entries ->
                    for (child in entries) {
                        if (checkCancelled()) throw CancellationException("Directory scan cancelled")
                        try {
                            val attrs = Files.readAttributes(
                                child,
                                BasicFileAttributes::class.java,
                                LinkOption.NOFOLLOW_LINKS
                            )
                            if (attrs.isSymbolicLink) {
                                //Skip symbolic links
                                FmLog.debug(TAG, "Skip symlink during scan: $child")
                                continue
                            }
                            if (attrs.isDirectory) {
                                stats = stats.copy(dirCount = stats.dirCount + 1)
                                stack.push(child)
                            } else if (attrs.isRegularFile) {
                                stats = stats.copy(
                                    fileCount = stats.fileCount + 1,
                                    totalSize = stats.totalSize + attrs.size()
                                )
                            }
                        } catch (e: Exception) {
                            FmLog.warn(TAG, "Failed to stat: $child", e)
                            //A failing entry doesn't abort the overall scan
                        }
                    }
                }
            }.onFailure { e ->
                FmLog.warn(TAG, "Failed to read directory: $dir", e)
            }

            onProgress?.invoke(stats, dir)
        }

        stats
    }
}