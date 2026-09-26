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

package com.endiq.turtlelauncher.ui.screens.content.versions.elements

import com.endiq.turtlelauncher.game.version.saves.SaveData
import com.endiq.turtlelauncher.game.version.saves.isCompatible
import com.endiq.turtlelauncher.utils.string.stripColorCodes

sealed interface SavesOperation {
    data object None : SavesOperation
    /** Running a task */
    data object Progress : SavesOperation
    /** Quick launch */
    data class QuickPlay(val saveData: SaveData) : SavesOperation
    /** Rename save input dialog */
    data class RenameSave(val saveData: SaveData) : SavesOperation
    /** Backup save input dialog */
    data class BackupSave(val saveData: SaveData) : SavesOperation
    /** Delete save dialog */
    data class DeleteSave(val saveData: SaveData) : SavesOperation
}

/**
 * Save filter
 */
data class SavesFilter(val onlyShowCompatible: Boolean, val saveName: String = "")

/**
 * A simple filter matching specific saves
 * @param minecraftVersion the current MC version, used for compatibility comparison
 * @param savesFilter the save filter
 */
fun List<SaveData>.filterSaves(
    minecraftVersion: String,
    savesFilter: SavesFilter
) = this.filter {
    val isCompatible = !savesFilter.onlyShowCompatible || it.isCompatible(minecraftVersion)

    val nameMatches = savesFilter.saveName.isEmpty() ||
            //Both save names and folder names are searchable
            //Color placeholders are stripped automatically
            it.levelName?.stripColorCodes()?.contains(savesFilter.saveName, true) == true ||
            it.saveFile.name.stripColorCodes().contains(savesFilter.saveName, true)

    isCompatible && nameMatches
}