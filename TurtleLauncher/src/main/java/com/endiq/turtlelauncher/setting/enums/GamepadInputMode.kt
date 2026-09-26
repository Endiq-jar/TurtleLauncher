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

import com.endiq.turtlelauncher.R

/**
 * Gamepad input mode
 */
enum class GamepadInputMode(val titleRes: Int, val summaryRes: Int) {
    /**
     * Mapping mode: gamepad buttons/sticks map onto virtual keyboard/mouse events (old games, games without gamepad support)
     */
    Mapped(
        titleRes = R.string.settings_gamepad_input_mode_mapped,
        summaryRes = R.string.settings_gamepad_input_mode_mapped_summary
    ),

    /**
     * SDL passthrough mode: gamepad input goes verbatim to the SDL/GLFW gamepad API (Minecraft 26.3+ native gamepad)
     */
    SdlDirect(
        titleRes = R.string.settings_gamepad_input_mode_sdl,
        summaryRes = R.string.settings_gamepad_input_mode_sdl_summary
    )
}