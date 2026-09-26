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

import kotlin.math.roundToInt

/** Minimum column count of the grid */
const val MIN_GRID_COLUMNS = 4

/** Design target for the cell edge length (dp) */
const val DEFAULT_TARGET_CELL_SIZE = 20f

/**
 * Grid geometry information.
 * [columns] is always even, so cards can align strictly to symmetric splits like half-width;
 * [cellSize] is the edge length of one square cell (dp),
 * and the grid edges align exactly with the container edges.
 */
data class GridGeometry(
    val columns: Int,
    val cellSize: Float
)

/**
 * Computes the grid from the container width:
 * subdivides into an even column count targeting [targetCellSize]; the width remainder is spread across all cells,
 * keeping cell edges near the target and the grid edges aligned with the container.
 */
fun computeGridGeometry(
    widthDp: Float,
    targetCellSize: Float = DEFAULT_TARGET_CELL_SIZE
): GridGeometry {
    if (widthDp <= 0f) return GridGeometry(MIN_GRID_COLUMNS, targetCellSize)
    val evenColumns = (widthDp / targetCellSize / 2f)
        .roundToInt()
        .coerceAtLeast(MIN_GRID_COLUMNS / 2) * 2
    return GridGeometry(columns = evenColumns, cellSize = widthDp / evenColumns)
}
