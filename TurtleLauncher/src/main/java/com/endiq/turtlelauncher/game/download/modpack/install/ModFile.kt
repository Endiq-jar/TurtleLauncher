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

package com.endiq.turtlelauncher.game.download.modpack.install

import java.io.File

/**
 * Downloadable mod file of a modpack
 * @param getFile when building the mod download URL on the spot is impossible or too slow, it can be built here
 */
data class ModFile(
    val outputFile: File? = null,
    val downloadUrls: List<String>? = null,
    val sha1: String? = null,
    val getFile: (suspend () -> ModFile)? = null
)
