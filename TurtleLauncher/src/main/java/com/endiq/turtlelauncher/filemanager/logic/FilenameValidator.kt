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

package com.endiq.turtlelauncher.filemanager.logic

import com.endiq.turtlelauncher.utils.file.InvalidFilenameException
import com.endiq.turtlelauncher.utils.file.checkFilenameValidity

object FilenameValidator {
    /**
     * Validates a filename
     * @throws FmFilenameException when invalid, carrying the error category and details
     */
    @Throws(FmFilenameException::class)
    fun check(name: String) {
        try {
            checkFilenameValidity(name)
        } catch (e: InvalidFilenameException) {
            throw FmFilenameException(
                message = e.message ?: "Invalid filename",
                type = when {
                    e.isLeadingOrTrailingSpace -> FmFilenameError.LEADING_OR_TRAILING_SPACE
                    e.isInvalidLength -> FmFilenameError.INVALID_LENGTH
                    else -> FmFilenameError.ILLEGAL_CHARACTERS
                },
                invalidLength = if (e.isInvalidLength) e.invalidLength else -1,
                illegalCharacters = if (e.containsIllegalCharacters()) e.illegalCharacters else null
            )
        }
    }

    /**
     * Validates a filename
     * @return the error exception on failure, or null when valid
     */
    fun verify(name: String): FmFilenameException? {
        return try {
            check(name)
            null
        } catch (e: FmFilenameException) {
            e
        }
    }
}
