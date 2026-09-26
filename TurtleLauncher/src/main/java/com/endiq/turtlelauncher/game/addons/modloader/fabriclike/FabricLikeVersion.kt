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

package com.endiq.turtlelauncher.game.addons.modloader.fabriclike

import com.endiq.turtlelauncher.game.addons.modloader.AddonVersion

abstract class FabricLikeVersion(
    /** Minecraft version */
    inherit: String,
    /** Loader name */
    val loaderName: String,
    /** Loader version */
    val version: String,
    /** Version status: true = stable (ignored by Quilt) */
    val stable: Boolean = true
) : AddonVersion(
    inherit = inherit
) {
    abstract val loaderUrl: String

    /**
     * Returns the version JSON download URL of the matching version
     */
    val loaderJsonUrl: String =
        "$loaderUrl/${
            inherit.replace("∞", "infinite")
        }/$version/profile/json"

    override fun getAddonVersion(): String = this.version

    override fun isVersion(versionString: String): Boolean {
        return this.version == versionString
    }
}