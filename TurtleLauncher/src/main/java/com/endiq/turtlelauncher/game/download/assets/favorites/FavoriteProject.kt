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

import android.os.Parcelable
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformClasses
import kotlinx.parcelize.Parcelize

/**
 * Local cache data of a favorited project
 * @param projectId the project's platform ID
 * @param iconUrl the project's icon URL
 * @param title project name
 * @param description project description
 * @param authors author list
 * @param classes project type
 * @param followTime favorite timestamp (ms)
 */
@Parcelize
class FavoriteProject(
    val projectId: String,
    val iconUrl: String? = null,
    val title: String,
    val description: String = "",
    val authors: List<String> = emptyList(),
    val classes: PlatformClasses,
    val followTime: Long
) : Parcelable
