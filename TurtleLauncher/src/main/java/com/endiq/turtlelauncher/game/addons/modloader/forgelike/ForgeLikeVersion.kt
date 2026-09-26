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

package com.endiq.turtlelauncher.game.addons.modloader.forgelike

import com.endiq.turtlelauncher.game.addons.modloader.AddonVersion

/**
 * [Reference PCL2](https://github.com/Hex-Dragon/PCL2/blob/44aea3e/Plain%20Craft%20Launcher%202/Modules/Minecraft/ModDownload.vb#L512-L563)
 */
abstract class ForgeLikeVersion(
    /** Loader display name */
    val loaderName: String,
    /** Normalized version number, only usable for comparison and sorting */
    val forgeBuildVersion: ForgeBuildVersion,
    /** Non-normalized version name displayable to players */
    val versionName: String,
    /** Corresponding Minecraft version */
    inherit: String,
    /** File extension */
    val fileExtension: String
) : AddonVersion(
    inherit = inherit
) {
    /**
     * Forge: whether the MC version is below 1.13. (1.13+ version numbers always have a major part over 20)
     * NeoForge: whether the MC version is 1.20.1. (1.20.1's major number is artificially defined to start with 19)
     */
    val isLegacy: Boolean get() = forgeBuildVersion.major < 20

    override fun getAddonVersion(): String = forgeBuildVersion.toString()

    override fun isVersion(versionString: String): Boolean {
        val target = ForgeBuildVersion.parse(versionString).toString()
        return target == forgeBuildVersion.toString()
    }
}