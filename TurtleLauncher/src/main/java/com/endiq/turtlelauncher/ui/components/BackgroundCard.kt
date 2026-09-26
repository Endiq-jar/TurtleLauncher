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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.screens.content.elements.backgroundGlass
import com.endiq.turtlelauncher.ui.theme.cardColor
import com.endiq.turtlelauncher.ui.theme.cardTitleColor
import com.endiq.turtlelauncher.ui.theme.onCardColor

/**
 * Background card composable,
 * Used just like a [Card], except [BackgroundCard] also configures a more comfortable background color
 */
@Composable
fun BackgroundCard(
    modifier: Modifier = Modifier,
    influencedByBackground: Boolean = true,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = cardColor(influencedByBackground),
        contentColor = onCardColor()
    ),
    elevation: CardElevation = CardDefaults.cardElevation(),
    blur: Int = AllSettings.backgroundBlur.state,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
    ) {
        Column(
            modifier = Modifier.backgroundGlass(blur, colors.containerColor, influencedByBackground),
            content = content
        )
    }
}

/**
 * Background card composable,
 * Used just like a [Card], except [BackgroundCard] also configures a more comfortable background color
 */
@Composable
fun BackgroundCard(
    modifier: Modifier = Modifier,
    influencedByBackground: Boolean = true,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = cardColor(influencedByBackground),
        contentColor = onCardColor(),
        disabledContainerColor = cardColor(influencedByBackground)
    ),
    elevation: CardElevation = CardDefaults.cardElevation(),
    blur: Int = AllSettings.backgroundBlur.state,
    border: BorderStroke? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable @UiComposable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = colors,
        elevation = elevation,
        border = border,
        onClick = onClick,
        enabled = enabled,
    ) {
        Column(
            modifier = Modifier.backgroundGlass(blur, colors.containerColor, influencedByBackground),
            content = content
        )
    }
}

/**
 * A title-bar layout suited to sit atop a BackgroundCard
 * @param alpha opacity of the component background
 */
@Composable
fun CardTitleLayout(
    modifier: Modifier = Modifier,
    influencedByBackground: Boolean = true,
    alpha: Float = 0.5f,
    color: Color = influencedByBackgroundColor(
        color = cardTitleColor(alpha),
        enabled = influencedByBackground
    ),
    contentColor: Color = onCardColor(),
    blur: Int = AllSettings.backgroundBlur.state,
    content: @Composable @UiComposable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = color,
            contentColor = contentColor,
        ) {
            Column(
                modifier = Modifier.backgroundGlass(blur, color, influencedByBackground),
                content = content
            )
        }
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
    }
}