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

package com.endiq.turtlelauncher.game.version.multiplayer.description

import com.endiq.turtlelauncher.game.text.TextColor
import kotlinx.serialization.Serializable

private fun flattenComponents(
    description: ComponentDescription,
    out: StringBuilder
) {
    if (description.text.isNotEmpty()) {
        out.append(description.text)
    }

    description.extra.forEach { child ->
        flattenComponents(child, out)
    }
}

/**
 * Server description as a list of text components
 */
@Serializable
data class ComponentDescriptionRoot(
    val values: List<ComponentDescription>
): ServerDescription {
    override fun toString(): String {
        val content = buildString {
            values.forEach {
                flattenComponents(it, this@buildString)
            }
        }
        return "ComponentDescriptionRoot:\n$content"
    }
}

/**
 * Server description as text components
 * [Reference implementation WIKI](https://minecraft.wiki/w/Text_component_format) (partial implementation)
 * @param text the component's actual text
 * @param color the component's color; null uses the system default
 * @param bold whether the component is bold
 * @param italic whether the component is italic
 * @param underlined whether the component is underlined
 * @param strikethrough whether the component is struck through
 * @param obfuscated whether the component renders as random characters
 * @param extra child components; children inherit only the parent's undefined text properties
 */
@Serializable
data class ComponentDescription(
    val text: String,
    val color: TextColor? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val underlined: Boolean? = null,
    val strikethrough: Boolean? = null,
    val obfuscated: Boolean? = null,
    val extra: List<ComponentDescription> = emptyList()
): ServerDescription {
    override fun toString(): String {
        val content = buildString {
            flattenComponents(this@ComponentDescription, this@buildString)
        }
        return "ComponentDescription:\n$content"
    }
}