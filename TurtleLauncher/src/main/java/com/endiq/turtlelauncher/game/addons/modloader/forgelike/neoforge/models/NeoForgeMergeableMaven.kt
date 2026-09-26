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

package com.endiq.turtlelauncher.game.addons.modloader.forgelike.neoforge.models

import com.endiq.turtlelauncher.game.addons.modloader.forgelike.neoforge.NeoForgeVersion

interface NeoForgeMergeableMaven<E>  {
    /**
     * Merges its own version data with other version data
     */
    operator fun plus(maven: E): List<NeoForgeVersion>

    fun isVersionInvalid(versionId: String): Boolean {
        val cantDownload = versionId == "47.1.82" //this version is listed but cannot be downloaded
        val isAlpha = versionId.contains("-alpha") //alpha builds are unstable; avoid downloading them
        return cantDownload || isAlpha
    }
}