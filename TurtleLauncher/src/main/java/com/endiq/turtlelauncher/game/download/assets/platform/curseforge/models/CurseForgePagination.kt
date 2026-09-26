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

package com.endiq.turtlelauncher.game.download.assets.platform.curseforge.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CurseForgePagination(
    /**
     * Current start index of the query
     */
    @SerialName("index")
    val index: Int,

    /**
     * Page size
     */
    @SerialName("pageSize")
    val pageSize: Int,

    /**
     * Number of results returned for the query
     */
    @SerialName("resultCount")
    val resultCount: Int,

    /**
     * Total number of results matching the query
     */
    @SerialName("totalCount")
    val totalCount: Long
)