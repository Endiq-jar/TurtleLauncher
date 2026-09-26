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

package com.endiq.turtlelauncher.game.download.modpack.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.game.download.assets.platform.Platform

/**
 * Modpack formats supported by the launcher
 * @param identifier identifier name used internally
 */
enum class PackPlatform(val identifier: String) {
    CurseForge(Platform.CURSEFORGE.displayName) {
        @Composable
        override fun getIcon(): Painter {
            return painterResource(R.drawable.img_platform_curseforge)
        }
    },
    Modrinth(Platform.MODRINTH.displayName) {
        @Composable
        override fun getIcon(): Painter {
            return painterResource(R.drawable.img_platform_modrinth)
        }
    },
    MultiMC("MultiMC") {
        @Composable
        override fun getIcon(): Painter {
            return painterResource(R.drawable.img_platform_multimc)
        }
    },
    MCBBS("MCBBS") {
        @Composable
        override fun getIcon(): Painter {
            return painterResource(R.drawable.img_chest)
        }
    };

    /**
     * Returns the format name used by the UI
     */
    @Composable
    open fun getText(): String = this.identifier

    /**
     * Returns the format icon used by the UI
     */
    @Composable
    abstract fun getIcon(): Painter
}