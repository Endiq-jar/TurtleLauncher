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

package com.endiq.layer_controller.layout

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.endiq.layer_controller.data.TextAlignment
import com.endiq.layer_controller.event.EventHandler
import com.endiq.layer_controller.observable.DefaultObservableButtonStyle
import com.endiq.layer_controller.observable.ObservableButtonStyle
import com.endiq.layer_controller.observable.ObservableNormalData
import com.endiq.layer_controller.observable.ObservableTextData
import com.endiq.layer_controller.observable.ObservableTranslatableString
import com.endiq.layer_controller.observable.ObservableWidget
import com.endiq.layer_controller.utils.buttonContentColorAsState
import com.endiq.layer_controller.utils.buttonFontSizeAsState
import com.endiq.layer_controller.utils.buttonSize
import com.endiq.layer_controller.utils.buttonStyle
import com.endiq.layer_controller.utils.editMode
import com.endiq.layer_controller.utils.snap.GuideLine
import com.endiq.layer_controller.utils.snap.SnapMode

private data class ButtonTextStyle(
    val text: ObservableTranslatableString,
    val textAlignment: TextAlignment,
    val textBold: Boolean,
    val textItalic: Boolean,
    val textUnderline: Boolean
)

/**
 * Basic text widget
 * @param allStyles all styles of the current control layout (used to load widget styles)
 * @param enableSnap whether snapping is enabled in edit mode
 * @param snapMode the snap mode
 * @param localSnapRange local snap range (only effective in Local mode)
 * @param getOtherWidgets fetches other widgets' info, used to compute snap positions in edit mode
 * @param snapThresholdValue the snap distance threshold
 * @param eventHandler the event handler
 * @param drawLine draws the snap guide lines
 * @param onLineCancel cancels the snap guide lines
 */
@Composable
internal fun TextButton(
    isEditMode: Boolean,
    data: ObservableWidget,
    allStyles: List<ObservableButtonStyle>,
    screenSize: IntSize,
    isDark: Boolean = isSystemInDarkTheme(),
    visible: Boolean = true,
    enableSnap: Boolean = false,
    snapMode: SnapMode = SnapMode.FullScreen,
    localSnapRange: Dp = 50.dp,
    getOtherWidgets: () -> List<ObservableWidget>,
    snapThresholdValue: Dp,
    eventHandler: EventHandler? = null,
    drawLine: (ObservableWidget, List<GuideLine>) -> Unit = { _, _ -> },
    onLineCancel: (ObservableWidget) -> Unit = {},
    isPressed: Boolean,
    onTapInEditMode: () -> Unit = {}
) {
    if (visible) {
        val styleId = data.styleId
        val style = allStyles
            .takeIf { data.styleId != null }
            ?.find { it.uuid == styleId }
            ?: DefaultObservableButtonStyle

        val locale = LocalConfiguration.current.locales[0]

        Box(
            modifier = Modifier
                .buttonSize(data, screenSize)
                .buttonStyle(
                    style = style,
                    isDark = isDark,
                    isPressed = isPressed
                )
                .editMode(
                    isEditMode = isEditMode,
                    data = data,
                    screenSize = screenSize,
                    enableSnap = enableSnap,
                    snapMode = snapMode,
                    localSnapRange = localSnapRange,
                    getOtherWidgets = getOtherWidgets,
                    snapThresholdValue = snapThresholdValue,
                    drawLine = drawLine,
                    onLineCancel = onLineCancel,
                    onTapInEditMode = onTapInEditMode
                ),
            contentAlignment = Alignment.Center
        ) {
            val color by buttonContentColorAsState(
                style = style,
                isDark = isDark,
                isPressed = isPressed
            )
            val fontSize by buttonFontSizeAsState(
                style = style,
                isDark = isDark,
                isPressed = isPressed
            )
            val buttonTextStyle = when (data) {
                is ObservableNormalData -> ButtonTextStyle(
                    text = data.text,
                    textAlignment = data.textAlignment,
                    textBold = data.textBold,
                    textItalic = data.textItalic,
                    textUnderline = data.textUnderline
                )
                is ObservableTextData -> ButtonTextStyle(
                    text = data.text,
                    textAlignment = data.textAlignment,
                    textBold = data.textBold,
                    textItalic = data.textItalic,
                    textUnderline = data.textUnderline
                )
                else -> error("Unknown widget type")
            }
            RtLText(
                text = buttonTextStyle.text.translate(locale),
                color = color,
                fontSize = fontSize.sp,
                textAlign = buttonTextStyle.textAlignment.textAlign,
                fontWeight = if (buttonTextStyle.textBold) FontWeight.Bold else null,
                fontStyle = if (buttonTextStyle.textItalic) FontStyle.Italic else null,
                textDecoration = if (buttonTextStyle.textUnderline) TextDecoration.Underline else null,
                style = LocalTextStyle.current.copy(
                    lineHeight = (fontSize * 1.1).sp
                )
            )

            DisposableEffect(Unit) {
                data.onCompositionStart(eventHandler)
                onDispose {
                    data.onCompositionDispose(eventHandler)
                }
            }
        }
    } else {
        //A fake widget using an empty component, just so the Layout has something to measure
        Spacer(
            modifier = Modifier.buttonSize(data, screenSize)
        )
    }
}

/**
 * Component that only renders the widget appearance
 */
@Composable
fun RendererStyleBox(
    style: ObservableButtonStyle,
    modifier: Modifier = Modifier,
    text: String = "",
    isDark: Boolean,
    isPressed: Boolean
) {
    Box(
        modifier = modifier.buttonStyle(
            style = style,
            isDark = isDark,
            isPressed = isPressed
        ),
        contentAlignment = Alignment.Center
    ) {
        val color by buttonContentColorAsState(style = style, isDark = isDark, isPressed = isPressed)
        val fontSize by buttonFontSizeAsState(style = style, isDark = isDark, isPressed = isPressed)
        RtLText(
            text = text,
            color = color,
            fontSize = fontSize.sp
        )
    }
}