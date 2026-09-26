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

import java.io.File

sealed interface ShaderOperation {
    data object None : ShaderOperation
    /** Running a task */
    data object Progress : ShaderOperation
    /** Rename shader pack input dialog */
    data class Rename(val info: ShaderPackInfo) : ShaderOperation
    /** Delete shader pack dialog */
    data class Delete(val info: ShaderPackInfo) : ShaderOperation
}

/**
 * Shader pack info
 */
data class ShaderPackInfo(
    val file: File,
    val fileSize: Long
)

/**
 * A simple filter matching specific shader packs
 */
fun List<ShaderPackInfo>.filterShaders(
    nameFilter: String
) = this.filter {
    nameFilter.isEmpty() || it.file.name.contains(nameFilter, true)
}