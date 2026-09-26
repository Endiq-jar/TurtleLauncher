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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.screens.content.settings.layouts.CardPosition
import com.endiq.turtlelauncher.ui.screens.content.settings.layouts.rememberSettingsCardShape

/**
 * A simple warning/hint card presenting info prominently
 * @param title the card's title
 * @param text the card's actual content
 * @param position the card's position in its UI group, controlling its four corner radii
 * @param influencedByBackground whether its background follows the custom launcher background
 */
@Composable
fun WarningCard(
    title: String,
    text: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable (innerModifier: Modifier) -> Unit) = @Composable { innerModifier ->
        Icon(
            modifier = innerModifier,
            painter = painterResource(R.drawable.ic_warning_filled),
            contentDescription = title
        )
    },
    position: CardPosition = CardPosition.Single,
    outerShapeSize: Dp = 12.dp,
    innerShapeSize: Dp = 4.dp,
    influencedByBackground: Boolean = true,
    blur: Int = AllSettings.backgroundBlur.state,
    containerColor: Color = influencedByBackgroundColor(
        color = MaterialTheme.colorScheme.secondaryContainer,
        enabled = influencedByBackground
    ),
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    val cardShape = rememberSettingsCardShape(
        position = position,
        outerShape = outerShapeSize,
        innerShape = innerShapeSize
    )

    BackgroundCard(
        modifier = modifier,
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        blur = blur,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            //Title section
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon(
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .padding(vertical = 2.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            //The custom warning content section
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = text
            )
        }
    }
}