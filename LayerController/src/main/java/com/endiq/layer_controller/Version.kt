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

package com.endiq.layer_controller

import com.endiq.layer_controller.data.TextAlignment
import com.endiq.layer_controller.layout.ControlLayout

/**
 * Version number of the control editor
 */
internal const val EDITOR_VERSION = 12

/**
 * Automatically processes and migrates control layouts to newer editor versions step by step
 */
internal fun updateLayoutToNew(
    layout: ControlLayout
): ControlLayout {
    return when (layout.editorVersion) {
        1 -> updateLayoutToNew(update1To2(layout))
        2 -> updateLayoutToNew(update2To3(layout))
        3 -> updateLayoutToNew(update3To4(layout))
        4, 5, 6, 7, 8, 9 -> updateLayoutToNew(update4To10(layout))
        10 -> updateLayoutToNew(update10To11(layout))
        11 -> updateLayoutToNew(update11To12(layout))
        else -> layout
    }
}

/**
 * 1 -> 2: widget position and size values rounded to 2 decimal places
 */
private fun update1To2(
    layout: ControlLayout
): ControlLayout = layout.copy(
    editorVersion = 2,
    layers = layout.layers.map { layer ->
        layer.copy(
            normalButtons = layer.normalButtons.map { data ->
                data.copy(
                    position = data.position.copy(
                        x = data.position.x * 10,
                        y = data.position.y * 10
                    ),
                    buttonSize = data.buttonSize.copy(
                        widthPercentage = data.buttonSize.widthPercentage * 10,
                        heightPercentage = data.buttonSize.heightPercentage * 10
                    )
                )
            },
            textBoxes = layer.textBoxes.map { data ->
                data.copy(
                    position = data.position.copy(
                        x = data.position.x * 10,
                        y = data.position.y * 10
                    ),
                    buttonSize = data.buttonSize.copy(
                        widthPercentage = data.buttonSize.widthPercentage * 10,
                        heightPercentage = data.buttonSize.heightPercentage * 10
                    )
                )
            }
        )
    }
)

/**
 * 2 -> 3: supports hiding widget layers while controlled by a physical mouse or gamepad
 */
private fun update2To3(
    layout: ControlLayout
): ControlLayout = layout.copy(
    editorVersion = 3,
    layers = layout.layers.map { layer ->
        layer.copy(
            hideWhenMouse = true,
            hideWhenGamepad = true
        )
    }
)

/**
 * 3 -> 4: supports text alignment, bold, italic, underline
 */
private fun update3To4(
    layout: ControlLayout
): ControlLayout = layout.copy(
    editorVersion = 4,
    layers = layout.layers.map { layer ->
        layer.copy(
            normalButtons = layer.normalButtons.map { data ->
                data.copy(
                    textAlignment = TextAlignment.Left,
                    textBold = false,
                    textItalic = false,
                    textUnderline = false
                )
            },
            textBoxes = layer.textBoxes.map { data ->
                data.copy(
                    textAlignment = TextAlignment.Left,
                    textBold = false,
                    textItalic = false,
                    textUnderline = false
                )
            }
        )
    }
)

/**
 * 4 -> 5: supports click events: `force show layers`, `force hide layers`
 * 5 -> 6: supports click events: `send message`
 * 6 -> 7: supports setting text size
 * 7 -> 8: supports extension settings: the joystick style of launcher layers is configurable
 * 8 -> 9: button size can go as low as 1%
 * 9 -> 10: supports hiding widget layers while using a joystick
 *
 * No layout-file change needed; only bump the version number
 */
private fun update4To10(
    layout: ControlLayout
): ControlLayout = layout.copy(
    editorVersion = 10
)

/**
 * 10 -> 11: widget appearance supports ignoring the system theme
 */
private fun update10To11(
    layout: ControlLayout
): ControlLayout = layout.copy(
    editorVersion = 11,
    styles = layout.styles.map { style ->
        style.copy(
            //Legacy layouts keep theme differentiation enabled
            commonStyle = false
        )
    }
)

/**
 * 11 -> 12: control layers support joystick widgets
 */
private fun update11To12(
    layout: ControlLayout
): ControlLayout = layout.copy(
    editorVersion = 12
)