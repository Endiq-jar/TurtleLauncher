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

package com.endiq.turtlelauncher.game.download.game.models

import com.google.gson.annotations.SerializedName

/**
 * Version info of the current version, written during install so the launcher recognizes versions better
 */
class LaunchFor(
    @SerializedName("infos")
    val infos: Array<Info>
) {
    class Info(
        /**
         * Version
         * e.g. a Minecraft version: 1.21.4
         * e.g. a NeoForge version: 21.4.136
         */
        @SerializedName("version")
        val version: String,
        /**
         * Name
         * e.g. Minecraft, NeoForge
         */
        @SerializedName("name")
        val name: String
    )
}