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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.endiq.turtlelauncher.ui.screens.content.elements.DisabledAlpha

/**
 * Navigation rail item with icon and text. When the item is selected,
 * a capsule background animation expands outward from the center.
 *
 * @param text composable defining the item's text content
 * @param onClick callback when the item is clicked
 * @param selected whether the item is currently selected;
 *                 the selection animation is driven by this state
 * @param icon composable defining the displayed icon, shown before the text.
 * @param selectedPadding inner padding of the item while selected
 * @param unSelectedPadding inner padding of the item while unselected
 * @param shape shape defining the item's clipping and click area.
 * @param backgroundColor animated background color of the selection.
 * @param selectedContentColor icon and text color while selected.
 * @param unselectedContentColor icon and text color while unselected.
 */
@Composable
fun TextRailItem(
    modifier: Modifier = Modifier,
    text: @Composable RowScope.() -> Unit,
    onClick: () -> Unit,
    icon: @Composable RowScope.() -> Unit = {},
    selected: Boolean,
    selectedPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    unSelectedPadding: PaddingValues = selectedPadding,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    backgroundColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    selectedContentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true
) {
    val animationProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "SelectionAnimation"
    )

    Box(
        modifier = modifier
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else DisabledAlpha)
    ) {
        //Background expanding animation
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .alpha(animationProgress)
        ) {
            val maxWidth = size.width
            val minWidth = 0f
            val currentWidth = minWidth + (maxWidth - minWidth) * animationProgress

            val left = (maxWidth - currentWidth) / 2

            //Draw the capsule-shaped background
            drawRoundRect(
                color = backgroundColor,
                topLeft = Offset(left, 0f),
                size = Size(currentWidth, size.height),
                cornerRadius = CornerRadius(size.height / 2, size.height / 2)
            )
        }

        val paddingLeft by animateDpAsState(
            if (selected) selectedPadding.calculateLeftPadding(LayoutDirection.Ltr) else unSelectedPadding.calculateLeftPadding(LayoutDirection.Ltr)
        )
        val paddingRight by animateDpAsState(
            if (selected) selectedPadding.calculateRightPadding(LayoutDirection.Ltr) else unSelectedPadding.calculateRightPadding(LayoutDirection.Ltr)
        )
        val paddingTop by animateDpAsState(
            if (selected) selectedPadding.calculateTopPadding() else unSelectedPadding.calculateTopPadding()
        )
        val paddingBottom by animateDpAsState(
            if (selected) selectedPadding.calculateBottomPadding() else unSelectedPadding.calculateBottomPadding()
        )

        Row(
            modifier = Modifier.padding(
                start = paddingLeft,
                end = paddingRight,
                top = paddingTop,
                bottom = paddingBottom
            ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val contentColor by animateColorAsState(
                targetValue = if (selected) selectedContentColor else unselectedContentColor
            )
            CompositionLocalProvider(
                LocalContentColor provides contentColor
            ) {
                icon()
                text()
            }
        }
    }
}