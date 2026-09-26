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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.endiq.turtlelauncher.utils.animation.swapAnimateDpAsState

/**
 * Vertical layout container with cascading animation support
 * Children are built via [AnimatedColumnScope.AnimatedItem], each getting an incremented animation delay
 * @param isVisible triggers animations of all children in the container
 * @param baseDelay base animation delay in ms; children increment from it
 * @param delayIncrement animation delay increment between adjacent children (ms)
 */
@Composable
fun AnimatedColumn(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    baseDelay: Int = 0,
    delayIncrement: Int = 50,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable AnimatedColumnScope.(ColumnScope) -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment
    ) {
        AnimatedColumnScopeImpl(isVisible, baseDelay, delayIncrement).content(this@Column)
    }
}

interface AnimatedColumnScope {
    /**
     * Declares an animated child inside an [AnimatedColumn]
     * @param delay the child animation delay in ms; overrides the computed one when set
     * @param targetValue initial offset distance (Dp)
     */
    @Composable
    fun AnimatedItem(
        columnScope: ColumnScope,
        modifier: Modifier = Modifier,
        delay: Int? = null,
        targetValue: Int = -40,
        content: @Composable ColumnScope.(yOffset: Dp) -> Unit
    )
}

private class AnimatedColumnScopeImpl(
    private val isVisible: Boolean,
    private val baseDelay: Int,
    private val delayIncrement: Int
) : AnimatedColumnScope {
    private var itemIndex = 0

    @Composable
    override fun AnimatedItem(
        columnScope: ColumnScope,
        modifier: Modifier,
        delay: Int?,
        targetValue: Int,
        content: @Composable ColumnScope.(yOffset: Dp) -> Unit
    ) {
        val currentIndex = itemIndex++
        //Prefer explicit delays; otherwise compute an incremented delay from the index
        val actualDelay = delay ?: (baseDelay + currentIndex * delayIncrement)

        val yOffset by swapAnimateDpAsState(
            targetValue = targetValue.dp,
            swapIn = isVisible,
            delayMillis = actualDelay
        )

        columnScope.content(yOffset)
    }
}


// Lazy Column



/**
 * Lazy vertical list container with cascading animation support
 * Children are declared via [AnimatedLazyListScope.animatedItems] or [AnimatedLazyListScope.animatedItem], each getting an incremented delay
 * @param isVisible triggers animations of all items in the list
 * @param baseDelay base animation delay in ms; children increment from it
 * @param delayIncrement animation delay increment between adjacent children (ms)
 */
@Composable
fun AnimatedLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    isVisible: Boolean,
    baseDelay: Int = 0,
    delayIncrement: Int = 50,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: AnimatedLazyListScope.(LazyListScope) -> Unit
) {
    val scope = remember(isVisible, baseDelay, delayIncrement) {
        AnimatedLazyListScopeImpl(isVisible, baseDelay, delayIncrement)
    }
    LazyColumn(
        modifier = modifier,
        state = state,
        verticalArrangement = verticalArrangement,
        contentPadding = contentPadding
    ) {
        scope.content(this@LazyColumn)
    }
}

interface AnimatedLazyListScope {
    /**
     * Adds a single animated list item in a [LazyListScope]
     * @param delay this item animation delay in ms; overrides the computed one when set
     * @param targetValue initial offset distance (Dp)
     */
    fun animatedItem(
        lazyListScope: LazyListScope,
        key: Any? = null,
        delay: Int? = null,
        targetValue: Int = -40,
        content: @Composable LazyItemScope.(yOffset: Dp) -> Unit
    )

    /**
     * Adds animated list items in bulk in a [LazyListScope]
     * @param delay base delay in ms; each item increments from it by index
     * @param targetValue initial offset distance (Dp)
     */
    fun <T> animatedItems(
        lazyListScope: LazyListScope,
        items: List<T>,
        delay: Int = 0,
        key: ((item: T) -> Any)? = null,
        targetValue: Int = -40,
        content: @Composable LazyItemScope.(index: Int, item: T, yOffset: Dp) -> Unit
    )
}

private class AnimatedLazyListScopeImpl(
    private val isVisible: Boolean,
    private val baseDelay: Int,
    private val delayIncrement: Int
) : AnimatedLazyListScope {
    private var itemIndex = 0

    override fun animatedItem(
        lazyListScope: LazyListScope,
        key: Any?,
        delay: Int?,
        targetValue: Int,
        content: @Composable LazyItemScope.(yOffset: Dp) -> Unit
    ) {
        val currentIndex = itemIndex++
        //Prefer explicit delays; otherwise compute an incremented delay from the index
        val actualDelay = delay ?: (baseDelay + currentIndex * delayIncrement)

        lazyListScope.item(key = key) {
            val yOffset by swapAnimateDpAsState(
                targetValue = targetValue.dp,
                swapIn = isVisible,
                delayMillis = actualDelay
            )
            content(yOffset)
        }
    }

    override fun <T> animatedItems(
        lazyListScope: LazyListScope,
        items: List<T>,
        delay: Int,
        key: ((item: T) -> Any)?,
        targetValue: Int,
        content: @Composable LazyItemScope.(index: Int, item: T, yOffset: Dp) -> Unit
    ) {
        lazyListScope.itemsIndexed(
            items = items,
            key = if (key != null) { _, item -> key(item) } else null
        ) { index, item ->
            val actualDelay = delay + baseDelay + index * delayIncrement
            val yOffset by swapAnimateDpAsState(
                targetValue = targetValue.dp,
                swapIn = isVisible,
                delayMillis = actualDelay
            )
            content(index, item, yOffset)
        }
    }
}