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

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.viewmodel.influencedByBackground

/**
 * Desaturates a color
 */
fun Color.desaturate(factor: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    hsv[1] *= factor.coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/**
 * Color influenced by background content; opacity adapts when such content is set
 * @param influencedAlpha the alpha applied while influenced
 */
@Composable
fun influencedByBackgroundColor(
    color: Color,
    influencedAlpha: Float = AllSettings.launcherBackgroundOpacity.state.toFloat() / 100f,
    enabled: Boolean = true
): Color {
    return influencedByBackground(
        value = color,
        influenced = color.copy(alpha = influencedAlpha),
        enabled = enabled
    )
}
