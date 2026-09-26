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

import com.endiq.turtlelauncher.filemanager.os.FmLog
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private const val TAG = "FileAccessScope"

/** Access scope controller */
class AccessScope(val root: Path) {
    init {
        require(Files.isDirectory(root)) {
            "AccessScope root must be an existing directory: $root"
        }
    }

    /**
     * Normalizes [target] and verifies it lies within [root] (relative paths resolve against [root]); returns the normalized absolute path
     * @throws OutOfScopeException when out of scope
     */
    fun guard(target: Path): Path {
        val resolved = resolveAgainstRoot(target)
        val normalized = resolved.normalize().toAbsolutePath()
        requireInside(normalized)
        return normalized
    }

    /**
     * Verifies absolute path [target] lies within [root]; returns the normalized absolute path
     * @throws OutOfScopeException when out of scope
     */
    fun guardAbsolute(target: Path): Path {
        val normalized = target.normalize().toAbsolutePath()
        requireInside(normalized)
        return normalized
    }

    /** Whether [child] is a descendant of [root] */
    fun isUnder(child: Path): Boolean {
        val normalized = child.normalize().toAbsolutePath()
        return normalized == rootAbs || normalized.startsWith(rootAbs)
    }

    val rootAbs: Path = root.normalize().toAbsolutePath()

    private fun resolveAgainstRoot(target: Path): Path {
        return if (target.isAbsolute) {
            target
        } else {
            rootAbs.resolve(target)
        }
    }

    private fun requireInside(normalized: Path) {
        if (!normalized.startsWith(rootAbs)) {
            FmLog.warn(TAG, "Denied out-of-scope access.")
            throw OutOfScopeException("Target path is outside the accessible scope.")
        }
    }

    companion object {
        /**
         * Builds an [AccessScope] from a string path
         * @throws IllegalArgumentException when the root directory does not exist
         */
        fun ofRoot(rootPath: String): AccessScope {
            val root = Paths.get(rootPath).normalize().toAbsolutePath()
            if (!Files.isDirectory(root)) {
                throw IllegalArgumentException("Root directory does not exist or is not a directory")
            }
            return AccessScope(root)
        }
    }
}

/** Exception thrown when escaping the accessible scope; carries no path details */
class OutOfScopeException(message: String) : SecurityException(message)