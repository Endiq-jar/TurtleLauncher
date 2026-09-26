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

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.endiq.turtlelauncher.setting.enums.isLauncherInDarkTheme
import com.endiq.turtlelauncher.ui.theme.components.activeMaskView
import com.endiq.turtlelauncher.utils.festival.Festival
import com.endiq.turtlelauncher.utils.festival.LocalFestivals
import com.endiq.turtlelauncher.viewmodel.BackgroundViewModel
import com.endiq.turtlelauncher.viewmodel.LocalBackgroundViewModel

private val verdantDawnLight = lightColorScheme(
    primary = primaryLight.verdantDawn,
    onPrimary = onPrimaryLight.verdantDawn,
    primaryContainer = primaryContainerLight.verdantDawn,
    onPrimaryContainer = onPrimaryContainerLight.verdantDawn,
    secondary = secondaryLight.verdantDawn,
    onSecondary = onSecondaryLight.verdantDawn,
    secondaryContainer = secondaryContainerLight.verdantDawn,
    onSecondaryContainer = onSecondaryContainerLight.verdantDawn,
    tertiary = tertiaryLight.verdantDawn,
    onTertiary = onTertiaryLight.verdantDawn,
    tertiaryContainer = tertiaryContainerLight.verdantDawn,
    onTertiaryContainer = onTertiaryContainerLight.verdantDawn,
    error = errorLight.verdantDawn,
    onError = onErrorLight.verdantDawn,
    errorContainer = errorContainerLight.verdantDawn,
    onErrorContainer = onErrorContainerLight.verdantDawn,
    background = backgroundLight.verdantDawn,
    onBackground = onBackgroundLight.verdantDawn,
    surface = surfaceLight.verdantDawn,
    onSurface = onSurfaceLight.verdantDawn,
    surfaceVariant = surfaceVariantLight.verdantDawn,
    onSurfaceVariant = onSurfaceVariantLight.verdantDawn,
    outline = outlineLight.verdantDawn,
    outlineVariant = outlineVariantLight.verdantDawn,
    scrim = scrimLight.verdantDawn,
    inverseSurface = inverseSurfaceLight.verdantDawn,
    inverseOnSurface = inverseOnSurfaceLight.verdantDawn,
    inversePrimary = inversePrimaryLight.verdantDawn,
    surfaceDim = surfaceDimLight.verdantDawn,
    surfaceBright = surfaceBrightLight.verdantDawn,
    surfaceContainerLowest = surfaceContainerLowestLight.verdantDawn,
    surfaceContainerLow = surfaceContainerLowLight.verdantDawn,
    surfaceContainer = surfaceContainerLight.verdantDawn,
    surfaceContainerHigh = surfaceContainerHighLight.verdantDawn,
    surfaceContainerHighest = surfaceContainerHighestLight.verdantDawn,
)

private val verdantDawnDark = darkColorScheme(
    primary = primaryDark.verdantDawn,
    onPrimary = onPrimaryDark.verdantDawn,
    primaryContainer = primaryContainerDark.verdantDawn,
    onPrimaryContainer = onPrimaryContainerDark.verdantDawn,
    secondary = secondaryDark.verdantDawn,
    onSecondary = onSecondaryDark.verdantDawn,
    secondaryContainer = secondaryContainerDark.verdantDawn,
    onSecondaryContainer = onSecondaryContainerDark.verdantDawn,
    tertiary = tertiaryDark.verdantDawn,
    onTertiary = onTertiaryDark.verdantDawn,
    tertiaryContainer = tertiaryContainerDark.verdantDawn,
    onTertiaryContainer = onTertiaryContainerDark.verdantDawn,
    error = errorDark.verdantDawn,
    onError = onErrorDark.verdantDawn,
    errorContainer = errorContainerDark.verdantDawn,
    onErrorContainer = onErrorContainerDark.verdantDawn,
    background = backgroundDark.verdantDawn,
    onBackground = onBackgroundDark.verdantDawn,
    surface = surfaceDark.verdantDawn,
    onSurface = onSurfaceDark.verdantDawn,
    surfaceVariant = surfaceVariantDark.verdantDawn,
    onSurfaceVariant = onSurfaceVariantDark.verdantDawn,
    outline = outlineDark.verdantDawn,
    outlineVariant = outlineVariantDark.verdantDawn,
    scrim = scrimDark.verdantDawn,
    inverseSurface = inverseSurfaceDark.verdantDawn,
    inverseOnSurface = inverseOnSurfaceDark.verdantDawn,
    inversePrimary = inversePrimaryDark.verdantDawn,
    surfaceDim = surfaceDimDark.verdantDawn,
    surfaceBright = surfaceBrightDark.verdantDawn,
    surfaceContainerLowest = surfaceContainerLowestDark.verdantDawn,
    surfaceContainerLow = surfaceContainerLowDark.verdantDawn,
    surfaceContainer = surfaceContainerDark.verdantDawn,
    surfaceContainerHigh = surfaceContainerHighDark.verdantDawn,
    surfaceContainerHighest = surfaceContainerHighestDark.verdantDawn,
)

@Composable
fun TurtleLauncherTheme(
    darkTheme: Boolean = isLauncherInDarkTheme(),
    backgroundViewModel: BackgroundViewModel? = null,
    festivals: List<Festival> = emptyList(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    //The launcher ships a single fixed green palette (Verdant Dawn);
    //color presets were removed so every device looks identical.
    val targetColorScheme = remember(darkTheme) {
        if (darkTheme) verdantDawnDark else verdantDawnLight
    }

    var currentDarkTheme by remember { mutableStateOf(darkTheme) }
    var currentDisplayScheme by remember { mutableStateOf(targetColorScheme) }

    LaunchedEffect(darkTheme, targetColorScheme) {
        if (darkTheme != currentDarkTheme) {
            context.activeMaskView(
                maskComplete = {
                    currentDarkTheme = darkTheme
                    currentDisplayScheme = targetColorScheme
                },
                maskAnimFinish = {

                }
            )
        } else {
            currentDarkTheme = darkTheme
            currentDisplayScheme = targetColorScheme
        }
    }

    CompositionLocalProvider(
        LocalBackgroundViewModel provides backgroundViewModel,
        LocalFestivals provides festivals
    ) {
        MaterialExpressiveTheme(
            colorScheme = currentDisplayScheme,
            motionScheme = MotionScheme.expressive(),
            typography = AppTypography,
            content = content
        )
    }
}
