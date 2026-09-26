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

package com.endiq.turtlelauncher.game.download.assets.favorites

import com.endiq.turtlelauncher.game.download.assets.platform.Platform
import com.tencent.mmkv.MMKV

/**
 * MMKV cache for Modrinth favorited projects, mapping project ID to project data
 */
fun favoritesModrinth(): MMKV = MMKV.mmkvWithID("FavoriteModrinthProject")
/**
 * MMKV cache for CurseForge favorited projects, mapping project ID to project data
 */
fun favoritesCurseForge(): MMKV = MMKV.mmkvWithID("FavoriteCurseForgeProject")

/**
 * Returns the favorite-projects MMKV cache per platform
 */
fun favoritesMMKV(platform: Platform): MMKV = when (platform) {
    Platform.MODRINTH -> favoritesModrinth()
    Platform.CURSEFORGE -> favoritesCurseForge()
}
