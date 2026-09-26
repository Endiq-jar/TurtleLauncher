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

package com.endiq.turtlelauncher.game.version.installed

import com.endiq.turtlelauncher.game.addons.modloader.ModLoader
import com.endiq.turtlelauncher.game.version.installed.VersionType.MODLOADERS
import com.endiq.turtlelauncher.game.version.installed.VersionType.UNKNOWN
import com.endiq.turtlelauncher.game.version.installed.VersionType.VANILLA

/**
 * Version type: distinguishes vanilla from mod loaders
 */
enum class VersionType {
    /**
     * Vanilla
     */
    VANILLA,

    /**
     * With a mod loader
     */
    MODLOADERS,

    /**
     * Unknown, cannot determine
     */
    UNKNOWN
}

private val loaders = ModLoader.entries.filter { it.isLoader }

/**
 * Tries to determine the version type from version info
 */
fun VersionInfo?.getVersionType(): VersionType {
    return when {
        this != null -> {
            when {
                loaderInfo == null || loaderInfo.loader == ModLoader.OPTIFINE -> VANILLA
                loaderInfo.loader in loaders -> MODLOADERS
                else -> UNKNOWN
            }
        }
        else -> UNKNOWN
    }
}

