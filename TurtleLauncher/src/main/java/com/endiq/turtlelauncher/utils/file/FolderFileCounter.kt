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

package com.endiq.turtlelauncher.utils.file

import java.io.File

/**
 * Records the file count inside a folder
 */
class FolderFileCounter(
    private val dir: File
) {
    private var counts: Int? = null

    /**
     * Records the file count of a directory and checks for changes
     * @return the current directory
     */
    fun checkDir(): Boolean {
        val tempCount = if (dir.isFile) {
            0
        } else {
            dir.list()?.size ?: 0
        }
        val result = getRecordedCount() != tempCount
        counts = tempCount
        return result
    }

    /**
     * Gets the previously recorded file count of the directory
     */
    fun getRecordedCount(): Int = counts ?: 0

    /**
     * Whether the file count was never checked
     */
    fun isUnchecked(): Boolean = counts == null
}