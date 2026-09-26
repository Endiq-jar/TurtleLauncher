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

package com.endiq.turtlelauncher.game.path

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Game directories
 */
@Entity(tableName = "gamePaths")
data class GamePath(
    /**
     * 单项唯一ID
     */
    @PrimaryKey
    val id: String,
    /**
     * Game directories的标题
     */
    var title: String,
    /**
     * 目标路径
     */
    val path: String
)
