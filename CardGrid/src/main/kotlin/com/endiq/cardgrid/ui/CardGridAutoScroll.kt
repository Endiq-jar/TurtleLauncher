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

package com.endiq.cardgrid.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.endiq.cardgrid.state.CardGridState

/** Width of the band that triggers edge auto-scroll */
private val AutoScrollEdge = 56.dp

/** Maximum auto-scroll speed (dp/s) */
private const val AutoScrollMaxSpeed = 900f

/**
 * Edge auto-scrolling during drag/resize sessions:
 * drives [scrollState] to scroll when the pointer nears the start/end of the viewport,
 * and recomputes the pointer position after scrolling so the session follows.
 * Must be placed in a composition that reports viewport coordinates, sharing the scroll container with [CardGrid].
 */
@Composable
fun CardGridAutoScroll(
    state: CardGridState,
    scrollState: ScrollState
) {
    val density = LocalDensity.current
    LaunchedEffect(state.hasSession) {
        if (!state.hasSession) return@LaunchedEffect
        var lastFrameNanos = 0L
        val edgePx = with(density) { AutoScrollEdge.toPx() }
        val maxSpeedPx = with(density) { AutoScrollMaxSpeed.dp.toPx() }
        while (true) {
            val frameNanos = withFrameNanos { it }
            if (lastFrameNanos != 0L) {
                val dt = (frameNanos - lastFrameNanos) / 1_000_000_000f
                val pointer = state.pointerPosition
                if (pointer != null) {
                    val viewportY = state.areaOffsetInRoot.y + pointer.y - state.viewportTopPx
                    val bottomDistance = state.viewportHeightPx - viewportY
                    val dyScroll = when {
                        viewportY in 0f..edgePx ->
                            -maxSpeedPx * (1f - viewportY / edgePx) * dt
                        bottomDistance in 0f..edgePx ->
                            maxSpeedPx * (1f - bottomDistance / edgePx) * dt
                        else -> 0f
                    }
                    if (dyScroll != 0f) {
                        scrollState.dispatchRawDelta(dyScroll)
                        state.onAutoScroll()
                    }
                }
            }
            lastFrameNanos = frameNanos
        }
    }
}
