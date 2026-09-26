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

package com.endiq.turtlelauncher.ui.control.gamepad

import com.endiq.turtlelauncher.R

/**
 * Joystick control mode
 */
enum class JoystickMode(val titleRes: Int, val summaryRes: Int) {
    /**
     * Left stick moves, right stick steers the camera
     */
    LeftMovement(
        titleRes = R.string.settings_gamepad_joystick_mode_left,
        summaryRes = R.string.settings_gamepad_joystick_mode_left_summary
    ),

    /**
     * Right stick moves, left stick steers the camera
     */
    RightMovement(
        titleRes = R.string.settings_gamepad_joystick_mode_right,
        summaryRes = R.string.settings_gamepad_joystick_mode_right_summary
    )
}