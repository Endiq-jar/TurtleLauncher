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

package com.endiq.turtlelauncher.game.addons.modloader.modlike

import com.endiq.turtlelauncher.game.addons.modloader.AddonVersion
import com.endiq.turtlelauncher.game.download.assets.platform.modrinth.models.ModrinthFile
import com.endiq.turtlelauncher.game.download.assets.platform.modrinth.models.ModrinthVersion

/**
 * Mod versions; only Modrinth is supported for now thanks to its accessibility
 */
open class ModVersion(
    /** Minecraft version */
    inherit: String,
    /** Display name */
    val displayName: String,
    /** Detailed version info class */
    val version: ModrinthVersion,
    /** Downloadable main file */
    val file: ModrinthFile
) : AddonVersion(
    inherit = inherit
) {

    override fun getAddonVersion(): String = this.version.versionNumber

    override fun isVersion(versionString: String): Boolean {
        return this.version.versionNumber == versionString
    }
}