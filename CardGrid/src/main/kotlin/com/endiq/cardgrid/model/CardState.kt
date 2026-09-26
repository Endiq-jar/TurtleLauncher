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

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Interaction state between a card and the user */
enum class CardInteraction { Idle, Adjusting, Dragging, Resizing }

/**
 * The card's own state, provided to the card content,
 * letting the card switch its presentation by size class and interaction state.
 */
class CardState(
    val spanWidth: Int,
    val spanHeight: Int,
    val columns: Int,
    val interaction: CardInteraction
) {
    /** Form-factor classification derived from the span */
    val sizeClass: CardSize = deriveSizeClass(spanWidth, spanHeight, columns)
}

/** Inset of the card rect within its cells, toward each edge */
val CardSpacing: Dp = 6.dp
