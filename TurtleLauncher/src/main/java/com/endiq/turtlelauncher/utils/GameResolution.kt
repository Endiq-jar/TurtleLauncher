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

package com.endiq.turtlelauncher.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.setting.enums.ResolutionRule
import kotlin.math.roundToInt

private const val CUSTOM_RESOLUTION_MIN_SCALE = 0.2f    // 20%
private const val CUSTOM_RESOLUTION_MAX_SCALE = 3f      // 300%

/**
 * Legal range of custom resolutions
 */
fun customResolutionRange(screenSide: Int): IntRange {
    val min = getDisplayFriendlyRes(
        (screenSide * CUSTOM_RESOLUTION_MIN_SCALE).roundToInt().coerceAtLeast(2),
        1f
    ).coerceAtLeast(2)
    val max = (screenSide * CUSTOM_RESOLUTION_MAX_SCALE).roundToInt()
    return min..max.coerceAtLeast(min)
}

/**
 * Display layout and coordinate mapping of the game view
 * @param renderSize the game render resolution
 * @param displaySize the on-screen display size of the game view
 * @param offset the display area's offset from the top-left corner (letterboxing of aspect-fit centering)
 */
data class GameDisplayLayout(
    val renderSize: IntSize,
    val displaySize: IntSize,
    val offset: IntOffset
) {
    /**
     * Maps fullscreen layout coordinates into game render coordinates
     */
    fun mapToGame(position: Offset): Offset {
        val scaleX = renderSize.width / displaySize.width.toFloat()
        val scaleY = renderSize.height / displaySize.height.toFloat()
        return Offset(
            (position.x - offset.x) * scaleX,
            (position.y - offset.y) * scaleY
        )
    }
}

/**
 * Computes the game render resolution by rules
 * With the custom rule, width/height clamp within 20%–300% of the real screen size,
 * non-positive values count as uninitialized, falling back to screen size
 */
fun computeGameRenderSize(
    screenSize: IntSize,
    rule: ResolutionRule,
    percentage: Int,
    customWidth: Int,
    customHeight: Int
): IntSize {
    return if (rule == ResolutionRule.CUSTOM) {
        fun fixCustomSide(value: Int, screenSide: Int): Int {
            val sanitized = value.takeIf { it > 0 } ?: screenSide
            return getDisplayFriendlyRes(sanitized.coerceIn(customResolutionRange(screenSide)), 1f)
        }
        IntSize(
            width = fixCustomSide(customWidth, screenSize.width),
            height = fixCustomSide(customHeight, screenSize.height)
        )
    } else {
        val scale = percentage / 100f
        IntSize(
            width = getDisplayFriendlyRes(screenSize.width, scale),
            height = getDisplayFriendlyRes(screenSize.height, scale)
        )
    }
}

/**
 * Reads current settings, computing the game render resolution
 */
fun computeGameRenderSize(screenSize: IntSize): IntSize = computeGameRenderSize(
    screenSize = screenSize,
    rule = AllSettings.resolutionRule.getValue(),
    percentage = AllSettings.resolutionRatio.getValue(),
    customWidth = AllSettings.customResolutionWidth.getValue(),
    customHeight = AllSettings.customResolutionHeight.getValue()
)

/**
 * Observable form: recomputes the game render resolution when any related setting changes
 */
@Composable
fun rememberGameRenderSize(screenSize: IntSize): IntSize {
    val rule = AllSettings.resolutionRule.state
    val percentage = AllSettings.resolutionRatio.state
    val customWidth = AllSettings.customResolutionWidth.state
    val customHeight = AllSettings.customResolutionHeight.state
    return remember(screenSize, rule, percentage, customWidth, customHeight) {
        computeGameRenderSize(screenSize, rule, percentage, customWidth, customHeight)
    }
}

/**
 * Computes the game view's display layout from current settings
 * The percentage rule fills the screen
 * The custom rule scales aspect-fit to the largest screen-fitting size, centered
 */
fun currentGameDisplayLayout(screenSize: IntSize): GameDisplayLayout {
    val renderSize = computeGameRenderSize(screenSize)
    return if (AllSettings.resolutionRule.state == ResolutionRule.CUSTOM) {
        computeGameDisplayLayout(screenSize, renderSize)
    } else {
        GameDisplayLayout(
            renderSize = renderSize,
            displaySize = screenSize,
            offset = IntOffset.Zero
        )
    }
}

/**
 * Computes the custom resolution's display layout
 */
fun computeGameDisplayLayout(screenSize: IntSize, renderSize: IntSize): GameDisplayLayout {
    val scale = minOf(
        screenSize.width / renderSize.width.toFloat(),
        screenSize.height / renderSize.height.toFloat()
    )
    val displaySize = IntSize(
        width = (renderSize.width * scale).roundToInt().coerceIn(1, screenSize.width),
        height = (renderSize.height * scale).roundToInt().coerceIn(1, screenSize.height)
    )
    return GameDisplayLayout(
        renderSize = renderSize,
        displaySize = displaySize,
        offset = IntOffset(
            x = (screenSize.width - displaySize.width) / 2,
            y = (screenSize.height - displaySize.height) / 2
        )
    )
}

/**
 * Gets the device's real screen width/height (px)
 */
fun getRealScreenSize(context: Context): IntSize {
    val metrics = context.resources.displayMetrics
    return IntSize(metrics.widthPixels, metrics.heightPixels)
}

/**
 * Before custom resolution init, fill with the real screen size
 */
fun ensureCustomResolutionInitialized(context: Context) {
    if (AllSettings.customResolutionWidth.getValue() > 0 &&
        AllSettings.customResolutionHeight.getValue() > 0
    ) return

    val screenSize = getRealScreenSize(context)
    AllSettings.customResolutionWidth.save(screenSize.width)
    AllSettings.customResolutionHeight.save(screenSize.height)
}
