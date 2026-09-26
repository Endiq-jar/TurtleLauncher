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

package com.endiq.turtlelauncher.upgrade

import com.endiq.turtlelauncher.utils.compareLangTag
import java.util.Locale

/**
 * Finds the right body by system language
 */
fun RemoteData.findCurrentBody(
    locale: Locale
): RemoteData.RemoteBody? {
    return bodies.sortedByDescending {
        it.language.contains("_")
    }.find { body ->
        locale.compareLangTag(body.language)
    }
}

/**
 * Finds the right cloud-drive link by system language, falling back to the default
 */
fun RemoteData.getCurrentCouldDrive(
    locale: Locale
): RemoteData.CloudDrive? {
    return cloudDrives.sortedByDescending {
        it.language.contains("_")
    }.find { drive ->
        locale.compareLangTag(drive.language)
    } ?: defaultCloudDrive?.takeIf {
        //NULL: available in every region
        //otherwise availability depends on language
        it.language == "NULL" || locale.compareLangTag(it.language)
    }
}