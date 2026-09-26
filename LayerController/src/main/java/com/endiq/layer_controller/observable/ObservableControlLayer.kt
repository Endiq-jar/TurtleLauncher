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

package com.endiq.layer_controller.observable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.endiq.layer_controller.data.JoystickData
import com.endiq.layer_controller.data.NormalData
import com.endiq.layer_controller.data.TextData
import com.endiq.layer_controller.layout.ControlLayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Observable ControlLayer wrapper
 */
class ObservableControlLayer(
    private val layer: ControlLayer
): Packable<ControlLayer> {
    val uuid: String = layer.uuid

    var editorHide by mutableStateOf(layer.hide)

    var name by mutableStateOf(layer.name)
    var hide by mutableStateOf(editorHide)
    var hideWhenMouse by mutableStateOf(layer.hideWhenMouse)
    var hideWhenGamepad by mutableStateOf(layer.hideWhenGamepad)
    var visibilityType by mutableStateOf(layer.visibilityType)
    
    private val _normalButtons = MutableStateFlow(layer.normalButtons.map { ObservableNormalData(it) })
    val normalButtons = _normalButtons.asStateFlow()
    
    private val _textBoxes = MutableStateFlow(layer.textBoxes.map { ObservableTextData(it) })
    val textBoxes = _textBoxes.asStateFlow()

    private val _joystickButtons = MutableStateFlow(layer.joystickButtons.map { ObservableJoystickData(it) })
    val joystickButtons = _joystickButtons.asStateFlow()

    /**
     * Adds a normal button
     */
    fun addNormalButton(button: NormalData) {
        addNormalButton(ObservableNormalData(button))
    }

    /**
     * Adds a normal button
     */
    fun addNormalButton(button: ObservableNormalData) {
        _normalButtons.update { it + button }
    }

    /**
     * Adds normal buttons in bulk
     */
    fun addAllNormalButton(buttons: List<ObservableNormalData>) {
        _normalButtons.update { it + buttons }
    }

    /**
     * Removes a normal button
     */
    fun removeNormalButton(uuid: String) {
        _normalButtons.update { oldList ->
            oldList.filterNot { it.uuid == uuid }
        }
    }

    /**
     * Adds a text display box
     */
    fun addTextBox(textBox: TextData) {
        addTextBox(ObservableTextData(textBox))
    }

    /**
     * Adds a text display box
     */
    fun addTextBox(textBox: ObservableTextData) {
        _textBoxes.update { it + textBox }
    }

    /**
     * Adds text display boxes in bulk
     */
    fun addAllTextBox(textBoxes: List<ObservableTextData>) {
        _textBoxes.update { it + textBoxes }
    }

    /**
     * Removes a text display box
     */
    fun removeTextBox(uuid: String) {
        _textBoxes.update { oldList ->
            oldList.filterNot { it.uuid == uuid }
        }
    }

    /**
     * Adds a joystick widget
     */
    fun addJoystickButton(joystick: JoystickData) {
        addJoystickButton(ObservableJoystickData(joystick))
    }

    /**
     * Adds a joystick widget
     */
    fun addJoystickButton(joystick: ObservableJoystickData) {
        _joystickButtons.update { it + joystick }
    }

    /**
     * Adds joystick widgets in bulk
     */
    fun addAllJoystickButton(joysticks: List<ObservableJoystickData>) {
        _joystickButtons.update { it + joysticks }
    }

    /**
     * Removes a joystick widget
     */
    fun removeJoystickButton(uuid: String) {
        _joystickButtons.update { oldList ->
            oldList.filterNot { it.uuid == uuid }
        }
    }

    override fun pack(): ControlLayer {
        return ControlLayer(
            name = name,
            uuid = uuid,
            hide = editorHide,
            hideWhenMouse = hideWhenMouse,
            hideWhenGamepad = hideWhenGamepad,
            visibilityType = visibilityType,
            normalButtons = _normalButtons.value.map { it.packNormal() },
            textBoxes = _textBoxes.value.map { it.packText() },
            joystickButtons = _joystickButtons.value.map { it.packJoystick() }
        )
    }

    override fun isModified(): Boolean {
        return this.layer.isModified(pack())
    }
}