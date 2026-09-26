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

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.setting.AllSettings

/**
 * Launcher UI dark theme
 */
enum class DarkMode(val textRes: Int) {
    Enable(R.string.generic_enable),
    Disable(R.string.generic_disable),

    /**
     * Follows the system
     */
    FollowSystem(R.string.generic_follow_system)
}

/**
 * Whether the launcher is in dark theme; behaves like [isSystemInDarkTheme],
 * but with the launcher settings system intervening
 */
@Composable
fun isLauncherInDarkTheme(): Boolean {
    val value = AllSettings.launcherDarkMode.state
    return key(value) {
        when (value) {
            DarkMode.Enable -> true
            DarkMode.Disable -> false
            DarkMode.FollowSystem -> isSystemInDarkTheme()
        }
    }
}