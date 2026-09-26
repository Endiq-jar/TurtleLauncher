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

package com.endiq.layer_controller.layout

import com.endiq.layer_controller.data.JoystickData
import com.endiq.layer_controller.data.NormalData
import com.endiq.layer_controller.data.TextData
import com.endiq.layer_controller.data.VisibilityType
import com.endiq.layer_controller.observable.Modifiable
import com.endiq.layer_controller.observable.isModified
import com.endiq.layer_controller.utils.randomUUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single layer of a control layout, storing control components like a graphic layer
 * @param name the layer name
 * @param hide whether the layer is hidden
 * @param hideWhenMouse whether to hide it while a physical mouse is in use
 * @param hideWhenGamepad whether to hide it while a gamepad is in use
 * @param visibilityType visibility scenarios of the layer
 * @param normalButtons the normal button list
 * @param textBoxes the text display box list
 * @param joystickButtons the joystick widget list
 */
@Serializable
data class ControlLayer(
    @SerialName("name")
    val name: String,
    @SerialName("uuid")
    val uuid: String,
    @SerialName("hide")
    val hide: Boolean,
    @SerialName("hideWhenMouse")
    val hideWhenMouse: Boolean = true,
    @SerialName("hideWhenGamepad")
    val hideWhenGamepad: Boolean = true,
    @SerialName("visibilityType")
    val visibilityType: VisibilityType,
    @SerialName("normalButtons")
    val normalButtons: List<NormalData> = emptyList(),
    @SerialName("textBoxes")
    val textBoxes: List<TextData> = emptyList(),
    @SerialName("joystickButtons")
    val joystickButtons: List<JoystickData> = emptyList()
): Modifiable<ControlLayer> {
    override fun isModified(other: ControlLayer): Boolean {
        return this.name != other.name ||
                this.uuid != other.uuid ||
                this.hide != other.hide ||
                this.hideWhenMouse != other.hideWhenMouse ||
                this.hideWhenGamepad != other.hideWhenGamepad ||
                this.visibilityType != other.visibilityType ||
                this.normalButtons.isModified(other.normalButtons) ||
                this.textBoxes.isModified(other.textBoxes) ||
                this.joystickButtons.isModified(other.joystickButtons)
    }
}

fun createNewLayer(defaultLayerName: String = ""): ControlLayer {
    return ControlLayer(
        name = defaultLayerName,
        uuid = randomUUID(),
        hide = false,
        hideWhenMouse = true,
        hideWhenGamepad = true,
        visibilityType = VisibilityType.ALWAYS
    )
}