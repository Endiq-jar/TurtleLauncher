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

package com.endiq.turtlelauncher.filemanager.logic.extract

/**
 * Thrown when extraction detects the archive needs a password or the password is wrong
 * @param type error type (password required / wrong password)
 */
class ArchivePasswordException(
    val type: Type,
    message: String = type.message,
    cause: Throwable? = null
) : Exception(message, cause) {
    /** Password exception types */
    enum class Type(val message: String) {
        /** The archive is encrypted and no password has been provided yet */
        REQUIRED("Archive requires a password"),
        /** The provided password is wrong */
        WRONG("Archive password is incorrect"),
    }
}
