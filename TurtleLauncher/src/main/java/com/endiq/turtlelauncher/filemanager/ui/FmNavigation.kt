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

package com.endiq.turtlelauncher.filemanager.ui

import androidx.navigation3.runtime.NavBackStack
import java.nio.file.Path

/**
 * Enters the trash page
 */
fun NavBackStack<FmNavKey>.openTrash() {
    if (lastOrNull() == FmNavKey.Trash) return
    add(FmNavKey.Trash)
}

/**
 * Exits the trash page back to the main page
 */
fun NavBackStack<FmNavKey>.closeTrash() {
    if (lastOrNull() == FmNavKey.Trash) {
        removeLastOrNull()
    }
}

/**
 * Enters the text editor page
 * @param path absolute path of the file to edit
 */
fun NavBackStack<FmNavKey>.openEditor(path: Path) {
    if (lastOrNull() is FmNavKey.Editor) return
    add(FmNavKey.Editor(path.toString()))
}

/**
 * Exits the text editor page back to the main page
 */
fun NavBackStack<FmNavKey>.closeEditor() {
    if (lastOrNull() is FmNavKey.Editor) {
        removeLastOrNull()
    }
}
