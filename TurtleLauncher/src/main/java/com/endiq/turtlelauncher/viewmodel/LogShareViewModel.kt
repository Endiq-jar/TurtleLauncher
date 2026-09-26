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

package com.endiq.turtlelauncher.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.io.File

/**
 * Game log share menu state ViewModel
 */
class LogShareViewModel : ViewModel() {
    /** The log file currently pending share */
    var currentLogFile by mutableStateOf<File?>(null)
        private set

    /** Whether the log action menu is shown */
    var showMenu by mutableStateOf(false)
        private set

    /**
     * Opens the log share menu
     */
    fun openMenu(logFile: File) {
        currentLogFile = logFile
        showMenu = true
    }

    /**
     * Closes the log share menu
     */
    fun closeMenu() {
        showMenu = false
    }

    /**
     * Resets the state
     */
    fun reset() {
        showMenu = false
        currentLogFile = null
    }
}
