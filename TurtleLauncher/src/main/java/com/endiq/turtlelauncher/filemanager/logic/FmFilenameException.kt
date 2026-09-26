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

/** Filename error category */
enum class FmFilenameError {
    /** Contains illegal characters / traversal sequences (like `..` or leading `/`, `\`) */
    ILLEGAL_CHARACTERS,

    /** Name too long (over 255 characters) */
    INVALID_LENGTH,

    /** Starts or ends with a space */
    LEADING_OR_TRAILING_SPACE,

    /** Duplicates an existing entry in the same directory */
    NAME_CONFLICT
}

/**
 * Filename validation exception
 * @param message error description
 * @param type error category
 * @param invalidLength invalid name length (valid for [FmFilenameError.INVALID_LENGTH], otherwise -1)
 * @param illegalCharacters the matched illegal characters (valid for [FmFilenameError.ILLEGAL_CHARACTERS], otherwise null)
 */
class FmFilenameException(
    message: String,
    val type: FmFilenameError,
    val invalidLength: Int = -1,
    val illegalCharacters: String? = null
) : Exception(message)
