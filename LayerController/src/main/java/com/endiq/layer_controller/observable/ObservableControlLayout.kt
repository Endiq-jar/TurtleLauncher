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

import com.endiq.layer_controller.EDITOR_VERSION
import com.endiq.layer_controller.data.ButtonStyle
import com.endiq.layer_controller.data.JoystickStyle
import com.endiq.layer_controller.layout.ControlLayer
import com.endiq.layer_controller.layout.ControlLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Observable ControlLayout wrapper, used to watch changes
 */
class ObservableControlLayout(
    private val layout: ControlLayout
): Packable<ControlLayout> {
    val info = ObservableControlInfo(layout.info)

    private val _layers = MutableStateFlow(layout.layers.map { ObservableControlLayer(it) })
    val layers = _layers.asStateFlow()
    
    private val _styles = MutableStateFlow(layout.styles.map { ObservableButtonStyle(it) })
    val styles = _styles.asStateFlow()

    private val _joystickStyles = MutableStateFlow(layout.joystickStyles.map { ObservableJoystickStyle(it) })
    val joystickStyles = _joystickStyles.asStateFlow()

    /**
     * Adds a control layer
     * @return the newly added observable control layer
     */
    fun addLayer(layer: ControlLayer): ObservableControlLayer {
        val newLayer = ObservableControlLayer(layer)
        //               Add at the top
        _layers.update { listOf(newLayer) + it }
        return newLayer
    }

    /**
     * Removes a control layer
     */
    fun removeLayer(uuid: String) {
        _layers.update { oldLayers ->
            oldLayers.filterNot { it.uuid == uuid }
        }
    }

    /**
     * Merges into the layer below
     */
    fun mergeDownward(layer: ObservableControlLayer) {
        _layers.update { oldLayers ->
            if (oldLayers.isEmpty()) return@update oldLayers

            val layers = oldLayers.toMutableList()
            val index = layers.indexOf(layer)
            if (index == -1 || index + 1 >= layers.size) return@update oldLayers

            val downLayer = layers[index + 1]

            layer.normalButtons.value.takeIf {
                it.isNotEmpty()
            }?.let {
                downLayer.addAllNormalButton(it)
            }

            layer.textBoxes.value.takeIf {
                it.isNotEmpty()
            }?.let {
                downLayer.addAllTextBox(it)
            }

            layer.joystickButtons.value.takeIf {
                it.isNotEmpty()
            }?.let {
                downLayer.addAllJoystickButton(it)
            }

            layers.removeAt(index)
            layers
        }
    }

    /**
     * Swaps the layer order
     */
    fun reorder(fromIndex: Int, toIndex: Int) {
        _layers.update { oldLayers ->
            oldLayers.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        }
    }

    /**
     * Adds a new button style
     */
    fun addStyle(style: ButtonStyle) {
        _styles.update { it + ObservableButtonStyle(style) }
    }

    /**
     * Duplicates a widget style
     */
    fun cloneStyle(style: ObservableButtonStyle) {
        _styles.update { it + style.cloneNew() }
    }

    /**
     * Removes a button style
     */
    fun removeStyle(uuid: String) {
        _styles.update { oldStyles ->
            oldStyles.filterNot { it.uuid == uuid }
        }
        layers.value.forEach { layer ->
            layer.normalButtons.value.forEach { button ->
                if (button.buttonStyle == uuid) {
                    button.buttonStyle = null
                }
            }
            layer.textBoxes.value.forEach { textBox ->
                if (textBox.buttonStyle == uuid) {
                    textBox.buttonStyle = null
                }
            }
        }
    }

    /**
     * Adds a joystick style
     */
    fun addJoystickStyle(style: JoystickStyle) {
        _joystickStyles.update { it + ObservableJoystickStyle(style) }
    }

    /**
     * Duplicates a joystick style
     */
    fun cloneJoystickStyle(style: ObservableJoystickStyle) {
        _joystickStyles.update { it + style.cloneNew() }
    }

    /**
     * Removes a joystick style
     */
    fun removeJoystickStyle(uuid: String) {
        _joystickStyles.update { oldStyles ->
            oldStyles.filterNot { it.uuid == uuid }
        }
        layers.value.forEach { layer ->
            layer.joystickButtons.value.forEach { joystick ->
                if (joystick.joystickStyleId == uuid) {
                    joystick.joystickStyleId = null
                }
            }
        }
    }

    /**
     * Syncs the editor-side hidden state of layers into the real hidden state
     * so preview mode uses the correct hidden state
     */
    fun applyEditorHide() {
        layers.value.forEach { layer ->
            layer.hide = layer.editorHide
        }
    }

    override fun pack(): ControlLayout {
        return ControlLayout(
            info = info.pack(),
            layers = _layers.value.map { it.pack() },
            styles = _styles.value.map { it.pack() },
            joystickStyles = _joystickStyles.value.map { it.pack() },
            editorVersion = EDITOR_VERSION
        )
    }

    override fun isModified(): Boolean {
        return this.layout.isModified(pack())
    }
}