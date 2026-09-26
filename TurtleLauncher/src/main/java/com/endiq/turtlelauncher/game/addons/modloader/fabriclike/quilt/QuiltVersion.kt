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

package com.endiq.turtlelauncher.game.addons.modloader.fabriclike.quilt

import com.endiq.turtlelauncher.game.addons.modloader.ModLoader
import com.endiq.turtlelauncher.game.addons.modloader.fabriclike.FabricLikeVersion

class QuiltVersion(
    inherit: String,
    version: String
) : FabricLikeVersion(
    inherit = inherit,
    loaderName = ModLoader.QUILT.displayName,
    version = version,
    /**
     * Quilt doesn't provide a stable key in its JSON
     * It can only be told from whether the version name carries 'beta'
     */
    stable = version.contains("beta")
) {
    override val loaderUrl: String
        get() = "${QuiltVersions.officialUrl}/versions/loader"
}