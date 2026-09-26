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

package com.endiq.turtlelauncher.game.versioninfo

import com.endiq.turtlelauncher.game.versioninfo.models.VersionManifest

/**
 * Minecraft version.
 * @param version The version string.
 * @param type The type of the version.
 * @param summary The summary of the version.
 */
class MinecraftVersion(
    val version: VersionManifest.Version,
    val type: Type,
    val summary: Int?,
    val urlSuffix: String? = null
): Comparable<MinecraftVersion> {
    override fun compareTo(other: MinecraftVersion): Int {
        return version.releaseTime.compareTo(other.version.releaseTime)
    }

    enum class Type {
        /**
         * Release
         */
        Release,

        /**
         * Snapshot
         */
        Snapshot,

        /**
         * Ancient Beta
         */
        OldBeta,

        /**
         * Ancient Alpha
         */
        OldAlpha,

        /**
         * April Fools
         */
        AprilFools,

        Unknown
    }
}