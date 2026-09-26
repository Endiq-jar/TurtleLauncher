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

/**
 * Form-factor classification derived from a card's span, letting card content switch its presentation,
 * the height tier is bucketed by the absolute span (in cells)
 */
enum class CardSizeClass(val maxSpan: Int) {
    COMPACT(4),
    SMALL(5),
    MEDIUM(7),
    LARGE(9),
    EXTRA_LARGE(Int.MAX_VALUE);

    companion object {
        /** Derives the tier from the height span (in cells) */
        fun fromSpan(span: Int): CardSizeClass =
            entries.first { span <= it.maxSpan }

        /** Derives the tier from the width's share of the grid width */
        fun fromFraction(width: Int, columns: Int): CardSizeClass {
            val classes = entries
            if (columns <= 0 || width <= 0) return COMPACT
            val fraction = width / columns.toFloat()
            val index = (fraction * classes.size).toInt().coerceIn(0, classes.lastIndex)
            return classes[index]
        }
    }
}

/** Record of card form factors */
data class CardSize(
    val width: CardSizeClass,
    val height: CardSizeClass
)

/** Derives the form-factor tiers of width and height from the card span */
fun deriveSizeClass(
    width: Int,
    height: Int,
    columns: Int
): CardSize = CardSize(
    width = CardSizeClass.fromFraction(width, columns),
    height = CardSizeClass.fromSpan(height)
)
