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

package com.endiq.turtlelauncher.ui.screens.game.elements.log_parser

import androidx.compose.ui.graphics.Color

data class LogLevelRule(
    val identifiers: List<String>,
    val textColor: Color,
    val backgroundColor: Color? = null,
    /**
     * Pastel tint applied as the background of the whole log line carrying this
     * level token. Null means the line stays unhighlighted (normal logs).
     */
    val lineBackgroundColor: Color? = null
)

val INFO = LogLevelRule(
    identifiers = listOf("INFO", "Info"),
    textColor = Color.White,
    backgroundColor = Color(0xFF447152)
)

val ERROR = LogLevelRule(
    identifiers = listOf("ERROR", "Error", "FATAL", "Fatal"),
    textColor = Color(0xFF7B241C),
    backgroundColor = Color(0xFFF1948A),
    lineBackgroundColor = Color(0x4DF1948A)
)

val DEBUG = LogLevelRule(
    identifiers = listOf("DEBUG", "Debug"),
    textColor = Color.White,
    backgroundColor = Color(0xFF43698D)
)

val WARN = LogLevelRule(
    identifiers = listOf("WARN", "Warn", "WARNING", "Warning"),
    textColor = Color(0xFF7D6608),
    backgroundColor = Color(0xFFF7DC6F),
    lineBackgroundColor = Color(0x40F7DC6F)
)
