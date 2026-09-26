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

package com.endiq.turtlelauncher.filemanager.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Position of a card within a list group, tracking which corners use the large or the small radius
 */
@ConsistentCopyVisibility
data class FmCardPosition private constructor(
    val outerTopStart: Boolean,
    val outerTopEnd: Boolean,
    val outerBottomStart: Boolean,
    val outerBottomEnd: Boolean
) {
    companion object {
        /**
         * All four corners use the large radius, for standalone cards
         */
        val Single = FmCardPosition(
            outerTopStart = true,
            outerTopEnd = true,
            outerBottomStart = true,
            outerBottomEnd = true
        )

        /**
         * Derives the position from the entry's place within the list group
         * @param index entry index
         * @param count total entry count in the group
         * @param columns column count; single-column lists use the default
         */
        fun of(index: Int, count: Int, columns: Int = 1): FmCardPosition {
            if (count <= 1) return Single
            val cols = columns.coerceAtLeast(1)
            // With a single grid row, entries' top/bottom edges sit at the group ends; adjacent edges use the small radius
            if (count <= cols) {
                val start = index == 0
                val end = index == count - 1
                return FmCardPosition(
                    outerTopStart = start,
                    outerTopEnd = end,
                    outerBottomStart = start,
                    outerBottomEnd = end
                )
            }
            val row = index / cols
            val col = index % cols
            val lastRow = (count - 1) / cols
            val lastCol = (count - 1) % cols

            val top = row == 0
            val start = col == 0
            // When the last row isn't full, the group's last entry pulls the right edge in
            val end = col == cols - 1 || (row == lastRow && col == lastCol)
            // When the last row isn't full, entries beyond its occupied columns expose their bottoms on the group edge
            val bottom = row == lastRow || (row == lastRow - 1 && col > lastCol)

            // A corner uses the large radius only when both adjacent edges sit on the group edge
            return FmCardPosition(
                outerTopStart = top && start,
                outerTopEnd = top && end,
                outerBottomStart = bottom && start,
                outerBottomEnd = bottom && end
            )
        }
    }
}

/**
 * Composes the four corner radii from the card position
 */
@Composable
fun rememberFmCardShape(
    position: FmCardPosition,
    outerShape: Dp = 28.dp,
    innerShape: Dp = 4.dp
): Shape {
    return remember(position, outerShape, innerShape) {
        RoundedCornerShape(
            topStart = if (position.outerTopStart) outerShape else innerShape,
            topEnd = if (position.outerTopEnd) outerShape else innerShape,
            bottomStart = if (position.outerBottomStart) outerShape else innerShape,
            bottomEnd = if (position.outerBottomEnd) outerShape else innerShape
        )
    }
}
