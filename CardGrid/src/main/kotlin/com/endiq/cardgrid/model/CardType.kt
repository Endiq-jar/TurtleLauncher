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

package com.endiq.cardgrid.model

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.IntOffset

/** Composable type of the card content; the parameter is the card's own id */
typealias CardContent = @Composable CardState.(cardId: String) -> Unit

/**
 * Type declaration of a user card; without a declared shape the theme default is used.
 * @param defaultSpan the default span
 * @param limits the size bounds
 * @param shape the shape
 * @param content the card's UI content
 */
class CardType(
    val typeId: String,
    val defaultSpan: IntOffset,
    val limits: CardLimits = CardLimits.DEFAULT,
    val shape: Shape? = null,
    val content: CardContent
)
