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

package com.endiq.turtlelauncher.viewmodel

import androidx.lifecycle.ViewModel
import com.endiq.turtlelauncher.ui.screens.NestedNavKey
import com.endiq.turtlelauncher.ui.screens.NormalNavKey

class ScreenBackStackViewModel : ViewModel() {
    /** Main screen */
    val mainScreen = NestedNavKey.Main()
    /** Settings screen */
    val settingsScreen = NestedNavKey.Settings()
    /** Download screen */
    val downloadScreen = NestedNavKey.Download()

    /** Game download screen */
    val downloadGameScreen = NestedNavKey.DownloadGame()
    /** pack download screen */
    val downloadModPackScreen = NestedNavKey.DownloadModPack()
    /** Mod download screen */
    val downloadModScreen = NestedNavKey.DownloadMod()
    /** Resource pack download screen */
    val downloadResourcePackScreen = NestedNavKey.DownloadResourcePack()
    /** Save download screen */
    val downloadSavesScreen = NestedNavKey.DownloadSaves()
    /** Shader download screen */
    val downloadShadersScreen = NestedNavKey.DownloadShaders()
    /** Favorite download screen */
    val downloadFavoritesScreen = NestedNavKey.DownloadFavorites()

    /**
     * Before navigating, drop every page belonging to [clearBeforeNavKeys] from the back stack
     * This avoids user stacking pages or multiple returns between these screens
     */
    val clearBeforeNavKeys = listOf(
        settingsScreen::class,
        downloadScreen::class,
        NormalNavKey.Multiplayer::class
    )
}