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

package com.endiq.turtlelauncher.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.endiq.turtlelauncher.utils.animation.swapAnimateDpAsState

/**
 * Horizontal layout container with cascading animation support
 * Children are built via [AnimatedRowScope.AnimatedItem], each getting an incremented animation delay
 * @param isVisible triggers animations of all children in the container
 * @param baseDelay base animation delay in ms; children increment from it
 * @param delayIncrement animation delay increment between adjacent children (ms)
 */
@Composable
fun AnimatedRow(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    baseDelay: Int = 0,
    delayIncrement: Int = 50,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    content: @Composable AnimatedRowScope.(RowScope) -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement
    ) {
        AnimatedRowScopeImpl(isVisible, baseDelay, delayIncrement).content(this@Row)
    }
}

interface AnimatedRowScope {
    /**
     * Declares an animated child inside an [AnimatedRow]
     * @param delay the child animation delay in ms; overrides the computed one when set
     * @param targetValue initial offset distance (Dp)
     */
    @Composable
    fun AnimatedItem(
        rowScope: RowScope,
        modifier: Modifier = Modifier,
        delay: Int? = null,
        targetValue: Int = 40,
        content: @Composable RowScope.(yOffset: Dp) -> Unit
    )
}

private class AnimatedRowScopeImpl(
    private val isVisible: Boolean,
    private val baseDelay: Int,
    private val delayIncrement: Int
) : AnimatedRowScope {
    private var itemIndex = 0

    @Composable
    override fun AnimatedItem(
        rowScope: RowScope,
        modifier: Modifier,
        delay: Int?,
        targetValue: Int,
        content: @Composable RowScope.(yOffset: Dp) -> Unit
    ) {
        val currentIndex = itemIndex++
        //Prefer explicit delays; otherwise compute an incremented delay from the index
        val actualDelay = delay ?: (baseDelay + currentIndex * delayIncrement)

        val yOffset by swapAnimateDpAsState(
            targetValue = targetValue.dp,
            swapIn = isVisible,
            isHorizontal = true,
            delayMillis = actualDelay
        )

        rowScope.content(yOffset)
    }
}
