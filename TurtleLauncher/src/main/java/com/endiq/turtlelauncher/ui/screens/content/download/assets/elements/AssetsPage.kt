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

package com.endiq.turtlelauncher.ui.screens.content.download.assets.elements

import com.endiq.turtlelauncher.game.download.assets.platform.PlatformSearchData
import com.endiq.turtlelauncher.game.download.assets.utils.ModTranslations

/**
 * Resource search results page info
 * @param pageNumber the page number
 * @param pageIndex the page index
 * @param totalPage total page count
 * @param isLastPage whether it's the last page
 * @param data cached search results
 */
data class AssetsPage(
    val pageNumber: Int,
    val pageIndex: Int,
    val totalPage: Int,
    val isLastPage: Boolean,
    val data: List<Pair<PlatformSearchData, ModTranslations.McMod?>>
)