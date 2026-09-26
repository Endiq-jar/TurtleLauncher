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

package com.endiq.layer_controller.event

import com.endiq.layer_controller.observable.Modifiable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Key click event
 * @param type the bound click event type
 * @param key unique event identifier / event value
 */
@Serializable
data class ClickEvent(
    @SerialName("type")
    val type: Type,
    @SerialName("key")
    val key: String
): Modifiable<ClickEvent> {
    @Serializable
    enum class Type {
        /**
         * Clicking triggers a key
         */
        @SerialName("key")
        Key,

        /**
         * Clicking triggers a launcher event
         */
        @SerialName("launcher_event")
        LauncherEvent,

        /**
         * Clicking toggles the control layers
         */
        @SerialName("switch_layer")
        SwitchLayer,

        /**
         * Clicking forcefully shows the control layers
         */
        @SerialName("show_layer")
        ShowLayer,

        /**
         * Clicking forcefully hides the control layers
         */
        @SerialName("hide_layer")
        HideLayer,

        /**
         * Clicking sends a chat message
         */
        @SerialName("send_text")
        SendText;

        /**
         * Whether this click event type concerns control layers
         */
        fun isAboutLayers(): Boolean =
            this == SwitchLayer ||
                    this == ShowLayer ||
                    this == HideLayer
    }

    /**
     * Whether this click event concerns control layers
     */
    fun isAboutLayers(): Boolean = type.isAboutLayers()

    override fun isModified(other: ClickEvent): Boolean {
        return this.type != other.type ||
                this.key != other.key
    }
}