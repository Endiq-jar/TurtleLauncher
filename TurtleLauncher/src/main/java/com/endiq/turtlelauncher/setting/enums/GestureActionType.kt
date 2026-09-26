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

package com.endiq.turtlelauncher.setting.enums

import com.endiq.inputmap.keycodes.LwjglGlfwKeycode
import com.endiq.turtlelauncher.R

/**
 * The button fires on a gesture-control tap
 */
enum class GestureActionType(val nameRes: Int) {
    MOUSE_RIGHT(R.string.settings_control_gesture_trigger_mouse_right),
    MOUSE_LEFT(R.string.settings_control_gesture_trigger_mouse_left)
}

/**
 * Converts to the actual LWJGL key value
 */
fun GestureActionType.toAction(): Int =
    when (this) {
        GestureActionType.MOUSE_RIGHT -> LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT
        GestureActionType.MOUSE_LEFT -> LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT
    }.toInt()