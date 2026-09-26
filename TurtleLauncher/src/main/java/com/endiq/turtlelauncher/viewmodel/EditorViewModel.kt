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

package com.endiq.turtlelauncher.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.endiq.layer_controller.data.HideLayerWhen
import com.endiq.layer_controller.layout.ControlLayout
import com.endiq.layer_controller.observable.ObservableButtonStyle
import com.endiq.layer_controller.observable.ObservableControlLayer
import com.endiq.layer_controller.observable.ObservableControlLayout
import com.endiq.layer_controller.observable.ObservableJoystickData
import com.endiq.layer_controller.observable.ObservableJoystickStyle
import com.endiq.layer_controller.observable.ObservableNormalData
import com.endiq.layer_controller.observable.ObservableTextData
import com.endiq.layer_controller.observable.ObservableWidget
import com.endiq.layer_controller.observable.cloneJoystick
import com.endiq.layer_controller.observable.cloneNormal
import com.endiq.layer_controller.observable.cloneText
import com.endiq.layer_controller.utils.saveToFile
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.ui.components.MenuState
import com.endiq.turtlelauncher.ui.screens.main.control_editor.EditorOperation
import com.endiq.turtlelauncher.ui.screens.main.control_editor.EditorWarningOperation
import com.endiq.turtlelauncher.ui.screens.main.control_editor.EditorWidgetOperation
import com.endiq.turtlelauncher.ui.screens.main.control_editor.PreviewScenario
import com.endiq.turtlelauncher.ui.screens.main.control_editor.edit_widget.SelectedWidgetData
import com.endiq.turtlelauncher.ui.theme.showThemed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Control layout editor
 */
class EditorViewModel : ViewModel() {
    lateinit var observableLayout: ObservableControlLayout
        private set

    /**
     * Currently selected widget layer
     */
    var selectedLayer by mutableStateOf<ObservableControlLayer?>(null)

    /**
     * Currently selected component (edit component dialog only)
     */
    var selectedWidget by mutableStateOf<SelectedWidgetData?>(null)

    /**
     * Currently selected widget style (style edit dialog only)
     */
    var selectedStyle by mutableStateOf<ObservableButtonStyle?>(null)

    /**
     * Currently selected joystick style (joystick style dialog only)
     */
    var selectedJoystickStyle by mutableStateOf<ObservableJoystickStyle?>(null)

    /**
     * Editor menu state
     */
    var editorMenu by mutableStateOf(MenuState.HIDE)

    /** The editor menu floating ball's current position */
    var editorBallPosition by mutableStateOf(Offset.Zero)

    /**
     * Editor operation items
     */
    var editorOperation by mutableStateOf<EditorOperation>(EditorOperation.None)

    /**
     * Editor widget operation items
     */
    var editorWidgetOperation by mutableStateOf<EditorWidgetOperation>(EditorWidgetOperation.None)

    /**
     * Editor warning state items
     */
    var editorWarningOperation by mutableStateOf<EditorWarningOperation>(EditorWarningOperation.None)

    /**
     * Whether widget layer focus mode is on
     */
    var isLayerFocus by mutableStateOf(false)

    /**
     * Whether it's control layout preview mode
     */
    var isPreviewMode by mutableStateOf(false)

    /**
     * Control layout preview scenarios
     */
    var previewScenario by mutableStateOf(PreviewScenario.InMenu)

    /**
     * Hide widget layers by device in layout preview
     */
    var previewHideLayerWhen by mutableStateOf(HideLayerWhen.None)



    fun initLayout(layout: ControlLayout) {
        if (!::observableLayout.isInitialized) {
            this.observableLayout = ObservableControlLayout(layout)
        }
    }



    /**
     * Toggles the editor menu
     */
    fun switchMenu() {
        editorMenu = editorMenu.next()
    }

    /**
     * Removes a control layer
     */
    fun removeLayer(layer: ObservableControlLayer) {
        if (layer == selectedLayer) selectedLayer = null
        observableLayout.removeLayer(layer.uuid)
    }

    /**
     * Adds a widget to a widget layer
     */
    fun addWidget(layers: List<ObservableControlLayer>, addToLayer: (ObservableControlLayer) -> Unit) {
        val layer = selectedLayer
        if (layers.isEmpty()) {
            editorWarningOperation = EditorWarningOperation.WarningNoLayers
        } else if (layer == null) {
            editorWarningOperation = EditorWarningOperation.WarningNoSelectLayer
        } else {
            addToLayer(layer)
        }
    }

    /**
     * Removes a widget from a widget layer
     */
    fun removeWidget(layer: ObservableControlLayer, widget: ObservableWidget) {
        when (widget) {
            is ObservableNormalData -> layer.removeNormalButton(widget.uuid)
            is ObservableTextData -> layer.removeTextBox(widget.uuid)
            is ObservableJoystickData -> layer.removeJoystickButton(widget.uuid)
        }
    }

    /**
     * Copies a widget into a widget layer
     */
    fun cloneWidgetToLayers(widget: ObservableWidget, layers: List<ObservableControlLayer>) {
        when (widget) {
            is ObservableNormalData -> {
                layers.forEach { layer ->
                    val newData = widget.cloneNormal()
                    layer.addNormalButton(newData)
                }
            }
            is ObservableTextData -> {
                layers.forEach { layer ->
                    val newData = widget.cloneText()
                    layer.addTextBox(newData)
                }
            }
            is ObservableJoystickData -> {
                layers.forEach { layer ->
                    val newData = widget.cloneJoystick()
                    layer.addJoystickButton(newData)
                }
            }
        }
    }

    /**
     * Creates a new widget style
     */
    fun createNewStyle(name: String) {
        observableLayout.addStyle(
            com.endiq.layer_controller.data.createNewButtonStyle(name)
        )
    }

    /**
     * Duplicates a widget style
     */
    fun cloneStyle(style: ObservableButtonStyle) {
        observableLayout.cloneStyle(style)
    }

    /**
     * Deletes a widget style
     */
    fun removeStyle(style: ObservableButtonStyle) {
        observableLayout.removeStyle(style.uuid)
    }

    /**
     * Removes a joystick style
     */
    fun removeJoystickStyle(style: ObservableJoystickStyle) {
        observableLayout.removeJoystickStyle(style.uuid)
    }

    /**
     * Creates a new joystick style
     */
    fun createNewJoystickStyle(name: String) {
        observableLayout.addJoystickStyle(
            com.endiq.layer_controller.data.createNewJoystickStyle(name)
        )
    }

    /**
     * Duplicates a joystick style
     */
    fun cloneJoystickStyle(style: ObservableJoystickStyle) {
        observableLayout.cloneJoystickStyle(style)
    }

    /**
     * Syncs the editor-side hidden state of layers into the real hidden state
     * so preview mode uses the correct hidden state
     */
    fun applyEditorHide() {
        observableLayout.applyEditorHide()
    }

    /**
     * Saves the control layout
     */
    fun save(
        targetFile: File,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            editorOperation = EditorOperation.Saving
            val layout = observableLayout.pack()
            runCatching {
                layout.saveToFile(targetFile)
            }.onFailure { e ->
                editorOperation = EditorOperation.SaveFailed(e)
            }.onSuccess {
                editorOperation = EditorOperation.None
                onSaved()
            }
        }
    }

    fun onBackPressed(
        context: Context,
        onExit: () -> Unit
    ) {
        //Check and leave the edit widget/style dialogs
        if (editorOperation is EditorOperation.SelectButton || editorOperation is EditorOperation.EditButtonStyle) {
            editorOperation = EditorOperation.None
        } else {
            showExitEditorDialog(
                context = context,
                onExit = onExit
            )
        }
    }

    /**
     * Checks whether the control layout was modified
     */
    private val checkModified = Mutex()

    /**
     * Pops the dialog for leaving the control layout editor
     * @param onExit the user confirmed; exit the editor
     */
    fun showExitEditorDialog(
        context: Context,
        onExit: () -> Unit
    ) {
        viewModelScope.launch {
            val isModified = checkModified.withLock {
                observableLayout.isModified()
            }
            if (isModified) {
                showExitEditorDialogSuspend(
                    context = context,
                    onExit = onExit
                )
            } else {
                //Unmodified: exit directly
                onExit()
            }
        }
    }

    private suspend fun showExitEditorDialogSuspend(
        context: Context,
        onExit: () -> Unit
    ) = withContext(Dispatchers.Main) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.generic_warning)
            .setMessage(R.string.control_editor_exit_message)
            .setPositiveButton(R.string.generic_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton(R.string.control_editor_exit_confirm) { dialog, _ ->
                dialog.dismiss()
                onExit()
            }
            .showThemed()
    }
}