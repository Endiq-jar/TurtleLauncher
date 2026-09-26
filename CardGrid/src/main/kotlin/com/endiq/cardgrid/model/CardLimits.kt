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

import com.endiq.cardgrid.model.CardLimits.Companion.DEFAULT


/**
 * Size bounds of a card (in cell spans), declared by the card type itself;
 * types that do not declare them fall back to [DEFAULT].
 */
data class CardLimits(
    val minWidth: Int = DEFAULT_MIN_SPAN,
    val minHeight: Int = DEFAULT_MIN_SPAN,
    val maxWidth: Int = Int.MAX_VALUE,
    val maxHeight: Int = Int.MAX_VALUE
) {
    /**
     * Normalizes the bounds against the actual grid width:
     * max span no bigger than the grid, min span no bigger than the max.
     */
    fun clampedFor(columns: Int): CardLimits {
        val maxW = maxWidth.coerceAtMost(columns)
        val minW = minWidth.coerceAtMost(maxW)
        val maxH = maxHeight
        val minH = minHeight.coerceAtMost(maxH)
        return CardLimits(minWidth = minW, minHeight = minH, maxWidth = maxW, maxHeight = maxH)
    }

    fun clampWidth(width: Int): Int = width.coerceIn(minWidth, maxWidth)

    fun clampHeight(height: Int): Int = height.coerceIn(minHeight, maxHeight)

    companion object {
        /** Default minimum span: 4×4 (about 80dp) */
        const val DEFAULT_MIN_SPAN = 4

        /** Default maximum height span: 12 rows (about 240dp) */
        const val DEFAULT_MAX_HEIGHT = 12

        val DEFAULT = CardLimits(maxHeight = DEFAULT_MAX_HEIGHT)
    }
}
