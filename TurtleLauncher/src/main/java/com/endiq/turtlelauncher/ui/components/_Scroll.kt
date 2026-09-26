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

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Gives a scrollable component edge-fade feedback, an intuitive way to hint that the area scrolls,
 * similar to the fading edge of the classic Android View system
 * - This implementation relies on `graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)`,
 *   which draws content into a separate off-screen buffer, enabling blend modes (like DstOut) to erase
 * - Placing this modifier after `padding()`, `scroll()` or others,
 *   the off-screen layer may wrap only part of the content, so the fade cannot cover the visible range and looks broken
 * @param direction the fade direction; vertical by default
 * @param position which edges to fade (top, bottom, or both)
 * @param style the fade style
 */
@Composable
fun Modifier.fadeEdge(
    state: ScrollState,
    length: Dp = 12.dp,
    direction: EdgeDirection = EdgeDirection.Vertical,
    position: EdgeSide = EdgeSide.Both,
    style: FadeStyle = createDefaultFadeStyle(direction)
): Modifier {
    val fadePx = with(LocalDensity.current) { length.toPx() }

    val topDistance = state.value.toFloat()
    val startFade = if (state.canScrollBackward) {
        (topDistance / fadePx).coerceIn(0f, 1f) * fadePx
    } else 0f

    val bottomDistance = (state.maxValue - state.value).toFloat()
    val endFade = if (state.canScrollForward) {
        (bottomDistance / fadePx).coerceIn(0f, 1f) * fadePx
    } else 0f

    return this
        //Use off-screen compositing so the blend mode only affects this component, not parents or siblings
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            drawFadeEdges(startFade, endFade, direction, position, style)
        }
}

/**
 * Gives a scrollable component edge-fade feedback, an intuitive way to hint that the area scrolls,
 * similar to the fading edge of the classic Android View system
 * - This implementation relies on `graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)`,
 *   which draws content into a separate off-screen buffer, enabling blend modes (like DstOut) to erase
 * - Placing this modifier after `padding()`, `scroll()` or others,
 *   the off-screen layer may wrap only part of the content, so the fade cannot cover the visible range and looks broken
 * @param direction the fade direction; vertical by default
 * @param position which edges to fade (top, bottom, or both)
 * @param style the fade style
 */
@Composable
fun Modifier.fadeEdge(
    state: LazyListState,
    length: Dp = 12.dp,
    direction: EdgeDirection = EdgeDirection.Vertical,
    position: EdgeSide = EdgeSide.Both,
    style: FadeStyle = createDefaultFadeStyle(direction)
): Modifier {
    val fadePx = with(LocalDensity.current) { length.toPx() }
    val layoutInfo = state.layoutInfo

    val firstItem = layoutInfo.visibleItemsInfo.firstOrNull()
    val topDistancePx = when {
        firstItem == null -> 0f
        state.firstVisibleItemIndex == 0 -> -firstItem.offset.toFloat()
        else -> fadePx //no longer at the top: full strength
    }
    val startFade = if (state.canScrollBackward) {
        (topDistancePx / fadePx).coerceIn(0f, 1f) * fadePx
    } else 0f

    val lastItem = layoutInfo.visibleItemsInfo.lastOrNull()
    val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    val bottomDistancePx = when {
        lastItem == null -> 0f
        state.canScrollForward.not() -> 0f
        state.firstVisibleItemIndex + layoutInfo.visibleItemsInfo.size == layoutInfo.totalItemsCount &&
                lastItem.offset + lastItem.size > viewportHeight ->
            (lastItem.offset + lastItem.size - viewportHeight).toFloat()
        else -> fadePx //not at the bottom yet: full strength
    }
    val endFade = if (state.canScrollForward) {
        (bottomDistancePx / fadePx).coerceIn(0f, 1f) * fadePx
    } else 0f

    return this
        //Use off-screen compositing so the blend mode only affects this component, not parents or siblings
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            drawFadeEdges(startFade, endFade, direction, position, style)
        }
}

/**
 * Internal unified fade renderer
 */
private fun DrawScope.drawFadeEdges(
    startFade: Float,
    endFade: Float,
    direction: EdgeDirection,
    position: EdgeSide,
    style: FadeStyle
) {
    val totalSize = if (direction == EdgeDirection.Vertical) size.height else size.width

    if (position.includeStart && startFade > 0f) {
        drawFadeEdge(
            insetStart = 0f,
            insetEnd = totalSize - startFade,
            direction = direction,
            reverse = false,
            style = style
        )
    }

    if (position.includeEnd && endFade > 0f) {
        drawFadeEdge(
            insetStart = totalSize - endFade,
            insetEnd = 0f,
            direction = direction,
            reverse = true,
            style = style
        )
    }
}

private fun DrawScope.drawFadeEdge(
    insetStart: Float,
    insetEnd: Float,
    direction: EdgeDirection,
    reverse: Boolean,
    style: FadeStyle
) {
    val isVertical = direction == EdgeDirection.Vertical
    inset(
        left = if (!isVertical) insetStart else 0f,
        top = if (isVertical) insetStart else 0f,
        right = if (!isVertical) insetEnd else 0f,
        bottom = if (isVertical) insetEnd else 0f
    ) {
        rotate(if (reverse) 180f else 0f) {
            drawRect(brush = style.brush, blendMode = style.blendMode)
        }
    }
}

/**
 * Controls the fade direction
 */
@Immutable
enum class EdgeDirection {
    Vertical, Horizontal
}

/**
 * Specifies which edge fades
 */
@Immutable
enum class EdgeSide(val includeStart: Boolean, val includeEnd: Boolean) {
    /** Top/left */
    Start(true, false),
    /** Bottom/right */
    End(false, true),
    /** Both sides */
    Both(true, true)
}

/**
 * Fade style definition; the default [BlendMode.DstOut] suffices
 */
@Immutable
data class FadeStyle(
    val brush: Brush,
    val blendMode: BlendMode
)

/**
 * Black-to-transparent linear gradient; with [BlendMode.DstOut] it achieves the fade
 * @param direction which way the gradient runs
 */
fun createDefaultFadeStyle(
    direction: EdgeDirection
): FadeStyle {
    //Black -> transparent
    val colorStops = arrayOf(
        0f to Color.Black,
        1f to Color.Transparent
    )

    return FadeStyle(
        brush = when (direction) {
            EdgeDirection.Vertical -> Brush.verticalGradient(*colorStops)
            EdgeDirection.Horizontal -> Brush.horizontalGradient(*colorStops)
        },
        blendMode = BlendMode.DstOut
    )
}


@Composable
fun Modifier.verticalScrollWithBar(
    state: ScrollState,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false
): Modifier = this.nonInteractiveScrollbar(
    state = state.scrollIndicatorState!!,
    orientation = Orientation.Vertical
).verticalScroll(
    state = state,
    enabled = enabled,
    flingBehavior = flingBehavior,
    reverseScrolling = reverseScrolling,
)

@Composable
fun Modifier.verticalScrollWithBar(
    state: ScrollState,
    overscrollEffect: OverscrollEffect?,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false
): Modifier = this.nonInteractiveScrollbar(
    state = state.scrollIndicatorState!!,
    orientation = Orientation.Vertical
).verticalScroll(
    state = state,
    overscrollEffect = overscrollEffect,
    enabled = enabled,
    flingBehavior = flingBehavior,
    reverseScrolling = reverseScrolling,
)

@Composable
fun Modifier.lazyScrollWithBar(
    state: LazyListState,
    orientation: Orientation = Orientation.Vertical
): Modifier = this.nonInteractiveScrollbar(
    state = state.scrollIndicatorState!!,
    orientation = orientation
)