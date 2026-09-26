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

package com.endiq.turtlelauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.endiq.turtlelauncher.setting.enums.isLauncherInDarkTheme
import com.endiq.turtlelauncher.ui.components.influencedByBackgroundColor

/** The app's overall background color */
@Composable
fun backgroundColor(): Color = MaterialTheme.colorScheme.surfaceContainer
@Composable
fun onBackgroundColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant

/**
 * Card background color
 * [androidx.compose.material3.Card]
 * [com.endiq.turtlelauncher.ui.components.BackgroundCard]
 * [androidx.compose.ui.window.Dialog]
 * @param influencedByBackground whether background content influences its own opacity
 */
@Composable
fun cardColor(
    influencedByBackground: Boolean = true
): Color = influencedByBackgroundColor(
    color = MaterialTheme.colorScheme.surfaceBright,
    enabled = influencedByBackground
)
@Composable
fun onCardColor(): Color = MaterialTheme.colorScheme.onSurface
/**
 * Background color of the card-top title, a translucent surface
 */
@Composable
fun cardTitleColor(
    alpha: Float = 0.5f
): Color = MaterialTheme.colorScheme.surface.copy(alpha = alpha)

/**
 * Background color of items on the card
 * @param influencedByBackground whether background content influences its own opacity
 */
@Composable
fun itemColor(
    influencedByBackground: Boolean = true,
    isDark: Boolean = isLauncherInDarkTheme()
): Color {
    return influencedByBackgroundColor(
        color = if (isDark) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
        },
        enabled = influencedByBackground
    )
}
@Composable
fun onItemColor() = MaterialTheme.colorScheme.onSurface