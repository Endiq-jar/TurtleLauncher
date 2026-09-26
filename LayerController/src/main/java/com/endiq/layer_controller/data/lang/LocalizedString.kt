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

package com.endiq.layer_controller.data.lang

import com.endiq.layer_controller.observable.Modifiable
import com.endiq.layer_controller.utils.compareLangTag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Localized display string
 * @param languageTag the language tag
 */
@Serializable
data class LocalizedString(
    @SerialName("language_tag")
    val languageTag: String,
    @SerialName("value")
    val value: String
): Modifiable<LocalizedString> {
    override fun isModified(other: LocalizedString): Boolean {
        return this.languageTag != other.languageTag ||
                this.value != other.value
    }
}

val EmptyLocalizedString = LocalizedString(languageTag = "", value = "")

/**
 * Attempts to check whether the language matches
 */
fun LocalizedString.check(
    locale: Locale = Locale.getDefault()
): String? = value.takeIf {
    locale.compareLangTag(languageTag)
}