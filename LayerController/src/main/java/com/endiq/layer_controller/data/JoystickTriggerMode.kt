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

package com.endiq.layer_controller.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Joystick trigger mode
 */
@Serializable
enum class JoystickTriggerMode {
    /**
     * Drag trigger
     * The finger must drag on the joystick to use it
     */
    @SerialName("drag")
    DRAG,
    /**
     * Touch trigger
     * Merely touching the joystick's touchable area triggers and uses it
     */
    @SerialName("touch")
    TOUCH
}
