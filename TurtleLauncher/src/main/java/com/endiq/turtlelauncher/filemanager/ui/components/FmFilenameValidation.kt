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

package com.endiq.turtlelauncher.filemanager.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.utils.file.InvalidFilenameException
import com.endiq.turtlelauncher.utils.file.checkFilenameValidity

/**
 * Validates a filename in real time, returning a localized error message
 * @return null when valid, otherwise a localized error string
 */
@Composable
fun fmFilenameInvalid(str: String): String? {
    return try {
        checkFilenameValidity(str)
        null
    } catch (e: InvalidFilenameException) {
        e.fmInvalidFilenameSummary()
    }
}

@Composable
fun InvalidFilenameException.fmInvalidFilenameSummary(): String = when {
    containsIllegalCharacters() -> stringResource(R.string.generic_input_invalid_character, illegalCharacters ?: "")
    isInvalidLength -> stringResource(R.string.file_invalid_length, invalidLength, 255)
    isLeadingOrTrailingSpace -> stringResource(R.string.file_invalid_leading_or_trailing_space)
    else -> ""
}
