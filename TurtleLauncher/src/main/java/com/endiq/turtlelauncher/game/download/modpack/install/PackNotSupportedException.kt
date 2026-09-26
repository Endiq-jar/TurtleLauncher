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
package com.endiq.turtlelauncher.game.download.modpack.install

/**
 * Thrown when a modpack is unsupported and cannot be imported
 * @param reason why it's unsupported
 */
class PackNotSupportedException(
    val reason: UnsupportedPackReason
) : RuntimeException(
    reason.reasonText
)

/**
 * Reasons for the launcher to judge a modpack unsupported
 */
enum class UnsupportedPackReason(
    val reasonText: String
) {
    /**
     * The archive is corrupted or unpacking failed
     */
    CorruptedArchive("The archive is corrupted or failed to extract."),

    /**
     * Unsupported modpack format
     */
    UnsupportedFormat("The modpack format is not supported.")
}