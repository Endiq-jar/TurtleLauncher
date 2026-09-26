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

package com.endiq.turtlelauncher.game.version.saves

import java.io.File

/**
 * Parsed save info class
 */
data class SaveData(
    /** Saves folder */
    val saveFile: File,
// For performance, save size is no longer computed
//    /** Precomputed save size */
//    val saveSize: Long,
    /** Whether this save is valid */
    val isValid: Boolean,
    /** The save's real name */
    val levelName: String? = null,
    /** The game's version name */
    val levelMCVersion: String? = null,
    /** Timestamp of this save's last save */
    val lastPlayed: Long? = null,
    /** Total play time */
    val playTime: Long? = null,
    /** The save's game mode */
    val gameMode: GameMode? = null,
    /** The save's difficulty level */
    val difficulty: Difficulty? = null,
    /** Whether the difficulty is locked */
    val difficultyLocked: Boolean? = null,
    /** Whether hardcore mode is on */
    val hardcoreMode: Boolean? = null,
    /** Whether commands (cheats) are enabled */
    val allowCommands: Boolean? = null,
    /** The world seed */
    val worldSeed: Long? = null
)
