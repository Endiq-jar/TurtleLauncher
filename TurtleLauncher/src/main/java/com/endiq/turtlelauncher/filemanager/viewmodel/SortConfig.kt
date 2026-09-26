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

package com.endiq.turtlelauncher.filemanager.viewmodel

import com.endiq.turtlelauncher.filemanager.config.FmConfig
import com.endiq.turtlelauncher.filemanager.logic.entry.FmEntry

/** Directory list sort configuration */
data class SortConfig(
    val field: FmConfig.SortField = FmConfig.SortField.NAME,
    val ascending: Boolean = true,
    /** Directories first */
    val folderFirst: Boolean = true
) {
    companion object {
        fun load(): SortConfig = SortConfig(
            field = runCatching {
                FmConfig.SortField.valueOf(FmConfig.sortField())
            }.getOrDefault(FmConfig.SortField.NAME),
            ascending = FmConfig.sortAscending(),
            folderFirst = FmConfig.folderFirst()
        )
    }
}

/** Trash-specific sort configuration */
data class TrashSortConfig(
    val field: FmConfig.TrashSortField = FmConfig.TrashSortField.DELETED,
    /** Descending by default; newer deletions come first */
    val ascending: Boolean = false,
    /** Directories first */
    val folderFirst: Boolean = true
) {
    companion object {
        fun load(): TrashSortConfig = TrashSortConfig(
            field = runCatching {
                FmConfig.TrashSortField.valueOf(FmConfig.trashSortField())
            }.getOrDefault(FmConfig.TrashSortField.DELETED),
            ascending = FmConfig.trashSortAscending(),
            folderFirst = FmConfig.trashFolderFirst()
        )
    }
}

fun TrashSortConfig.persist() {
    FmConfig.setTrashSortField(field.name)
    FmConfig.setTrashSortAscending(ascending)
    FmConfig.setTrashFolderFirst(folderFirst)
}

fun SortConfig.persist() {
    FmConfig.setSortField(field.name)
    FmConfig.setSortAscending(ascending)
    FmConfig.setFolderFirst(folderFirst)
}

fun applyVisibility(
    entries: List<FmEntry>,
    config: SortConfig,
    showHidden: Boolean
): List<FmEntry> {
    var list = entries
    if (!showHidden) {
        list = list.filterNot { it.hidden }
    }
    val comparator: Comparator<FmEntry> = when (config.field) {
        FmConfig.SortField.NAME -> compareBy { it.name.lowercase() }

        // Directories have no size; size sorting falls back to name order for them
        FmConfig.SortField.SIZE -> compareBy<FmEntry> {
            if (it.isDirectory) 0L else it.size
        }.thenBy { it.name.lowercase() }

        FmConfig.SortField.MODIFIED -> compareBy { it.modifiedMs }
    }
    val ordered = if (config.ascending) {
        list.sortedWith(comparator)
    } else {
        list.sortedWith(comparator.reversed())
    }

    if (!config.folderFirst) return ordered

    // Keep "directories first / files after"; preserve current order among directories
    val dirs = ordered.filter { it.isDirectory }
    val files = ordered.filter { it.isFile }
    return dirs + files
}