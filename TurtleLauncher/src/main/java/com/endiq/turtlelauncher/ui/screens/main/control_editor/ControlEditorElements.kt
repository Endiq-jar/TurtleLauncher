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

package com.endiq.turtlelauncher.ui.screens.main.control_editor

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.endiq.layer_controller.data.HideLayerWhen
import com.endiq.layer_controller.data.JoystickTriggerMode
import com.endiq.layer_controller.data.VisibilityType
import com.endiq.layer_controller.event.ClickEvent
import com.endiq.layer_controller.observable.ObservableButtonStyle
import com.endiq.layer_controller.observable.ObservableClickEventsProvider
import com.endiq.layer_controller.observable.ObservableControlLayer
import com.endiq.layer_controller.observable.ObservableJoystickStyle
import com.endiq.layer_controller.observable.ObservableTranslatableString
import com.endiq.layer_controller.observable.ObservableWidget
import com.endiq.layer_controller.utils.snap.SnapMode
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.bridge.CURSOR_DISABLED
import com.endiq.turtlelauncher.bridge.CURSOR_ENABLED
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.components.DualMenuSubscreen
import com.endiq.turtlelauncher.ui.components.FloatingBall
import com.endiq.turtlelauncher.ui.components.MarqueeText
import com.endiq.turtlelauncher.ui.components.MenuListLayout
import com.endiq.turtlelauncher.ui.components.MenuState
import com.endiq.turtlelauncher.ui.components.MenuSwitchButton
import com.endiq.turtlelauncher.ui.components.MenuTextButton
import com.endiq.turtlelauncher.ui.components.ScalingActionButton
import com.endiq.turtlelauncher.ui.components.lazyScrollWithBar
import com.endiq.turtlelauncher.ui.theme.cardColor
import com.endiq.turtlelauncher.ui.theme.onCardColor
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Control layout editor operation states
 */
sealed interface EditorOperation {
    data object None : EditorOperation
    /** A widget was selected for editing */
    data object SelectButton : EditorOperation
    /** Edit widget layer properties */
    data class EditLayer(val layer: ObservableControlLayer) : EditorOperation
    /** Delete the widget layer */
    data class DeleteLayer(val layer: ObservableControlLayer) : EditorOperation
    /** Open the widget style list */
    data object OpenStyleList : EditorOperation
    /** Create a widget style */
    data object CreateStyle : EditorOperation
    /** Edit a widget style */
    data object EditButtonStyle : EditorOperation
    /** Delete a widget style */
    data class DeleteButtonStyle(val style: ObservableButtonStyle) : EditorOperation
    /** Edit a joystick style */
    data object EditJoystickStyle : EditorOperation
    /** Delete a joystick style */
    data class DeleteJoystickStyle(val style: ObservableJoystickStyle) : EditorOperation
    /** Open the joystick style list */
    data object OpenJoystickStyleList : EditorOperation
    /** Create a joystick style */
    data object CreateJoystickStyle : EditorOperation
    /** Control layout is being saved */
    data object Saving : EditorOperation
    /** Control layout save failed */
    data class SaveFailed(val error: Throwable) : EditorOperation
}

/**
 * Control layout editor widget operation states
 */
sealed interface EditorWidgetOperation {
    data object None : EditorWidgetOperation
    /** A widget was selected; ask the user which widget layers to copy it into */
    data class CloneButton(val data: ObservableWidget, val layer: ObservableControlLayer) : EditorWidgetOperation
    /** Delete a widget */
    data class DeleteButton(val data: ObservableWidget, val layer: ObservableControlLayer) : EditorWidgetOperation
    /** Edit the widget's display text */
    data class EditWidgetText(val string: ObservableTranslatableString) : EditorWidgetOperation
    /** Edit the widget-layer visibility switching click event */
    data class SwitchLayersVisibility(
        val data: ObservableClickEventsProvider,
        val type: ClickEvent.Type
    ) : EditorWidgetOperation
    /** Edit the text to send */
    data class SendText(val data: ObservableClickEventsProvider) : EditorWidgetOperation
}

/**
 * Warning-style operation states of the control layout editor
 */
sealed interface EditorWarningOperation {
    data object None : EditorWarningOperation
    /** No widget layers; remind the user to add one */
    data object WarningNoLayers : EditorWarningOperation
    /** No widget layer selected; remind the user to pick one */
    data object WarningNoSelectLayer : EditorWarningOperation
}

/**
 * Control layout preview scenarios
 */
enum class PreviewScenario(
    val textRes: Int,
    val cursorMode: Int,
    val isCursorGrabbing: Boolean = cursorMode == CURSOR_DISABLED
) {
    InGame(R.string.control_editor_menu_preview_mode_in_game, cursorMode = CURSOR_DISABLED),
    InMenu(R.string.control_editor_menu_preview_mode_in_menu, cursorMode = CURSOR_ENABLED)
}

@Composable
fun VisibilityType.getVisibilityText(): String {
    val textRes = when (this) {
        VisibilityType.ALWAYS -> R.string.control_editor_edit_visibility_always
        VisibilityType.IN_GAME -> R.string.control_editor_edit_visibility_in_game
        VisibilityType.IN_MENU -> R.string.control_editor_edit_visibility_in_menu
    }
    return stringResource(textRes)
}

@Composable
fun JoystickTriggerMode.getTriggerModeText(): String {
    val textRes = when (this) {
        JoystickTriggerMode.DRAG -> R.string.control_editor_edit_joystick_trigger_mode_drag
        JoystickTriggerMode.TOUCH -> R.string.control_editor_edit_joystick_trigger_mode_touch
    }
    return stringResource(textRes)
}

@Composable
fun MenuBox(
    position: Offset,
    onPositionChanged: (Offset) -> Unit,
    opened: Boolean,
    onClick: () -> Unit
) {
    FloatingBall(
        position = position,
        onPositionChanged = onPositionChanged,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .padding(all = 2.dp)
                .size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(opened) { state ->
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(
                        if (state) {
                            R.drawable.ic_menu_open
                        } else {
                            R.drawable.ic_menu
                        }
                    ),
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
fun EditorMenu(
    state: MenuState,
    closeScreen: () -> Unit,
    layers: List<ObservableControlLayer>,
    onReorder: (from: Int, to: Int) -> Unit,
    selectedLayer: ObservableControlLayer?,
    onLayerSelected: (ObservableControlLayer?) -> Unit,
    createLayer: () -> Unit,
    onAttribute: (ObservableControlLayer) -> Unit,
    onHideSwitch: (ObservableControlLayer) -> Unit,
    addNewButton: () -> Unit,
    addNewText: () -> Unit,
    addNewJoystick: () -> Unit,
    openStyleList: () -> Unit,
    openJoystickStyleList: () -> Unit,
    isLayerFocus: Boolean,
    onLayerFocusChanged: (Boolean) -> Unit,
    isPreviewMode: Boolean,
    onPreviewChanged: (Boolean) -> Unit,
    previewScenario: PreviewScenario,
    onPreviewScenarioChanged: (PreviewScenario) -> Unit,
    previewHideLayerWhen: HideLayerWhen,
    onPreviewHideLayerChanged: (HideLayerWhen) -> Unit,
    onSave: () -> Unit,
    saveAndExit: () -> Unit,
    onExit: () -> Unit
) {
    DualMenuSubscreen(
        state = state,
        closeScreen = closeScreen,
        leftMenuTitle = {
            Text(
                modifier = Modifier.padding(all = 8.dp),
                text = stringResource(R.string.control_editor_menu_title),
                style = MaterialTheme.typography.titleMedium
            )
        },
        leftMenuContent = {
            EditorMenuContent(
                modifier = Modifier.weight(1f),
                closeScreen = closeScreen,
                addNewButton = addNewButton,
                addNewText = addNewText,
                addNewJoystick = addNewJoystick,
                openStyleList = openStyleList,
                openJoystickStyleList = openJoystickStyleList,
                isPreviewMode = isPreviewMode,
                onPreviewChanged = onPreviewChanged,
                previewScenario = previewScenario,
                onPreviewScenarioChanged = onPreviewScenarioChanged,
                previewHideLayerWhen = previewHideLayerWhen,
                onPreviewHideLayerChanged = onPreviewHideLayerChanged,
                onSave = onSave,
                saveAndExit = saveAndExit,
                onExit = onExit
            )
        },
        rightMenuTitle = {
            Text(
                modifier = Modifier.padding(all = 8.dp),
                text = stringResource(R.string.control_editor_layers_title),
                style = MaterialTheme.typography.titleMedium
            )
            //Switch widget layer focus
            IconButton(
                modifier = Modifier.align(Alignment.CenterEnd),
                onClick = {
                    onLayerFocusChanged(isLayerFocus.not())
                },
                enabled = isPreviewMode.not() && selectedLayer != null
            ) {
                Crossfade(
                    targetState = isLayerFocus
                ) { isFocus ->
                    Icon(
                        painter = painterResource(
                            if (isFocus) {
                                R.drawable.ic_center_focus_strong_filled
                            } else {
                                R.drawable.ic_center_focus_strong_outlined
                            }
                        ),
                        contentDescription = null
                    )
                }
            }
        },
        rightMenuContent = {
            ControlLayerMenu(
                layers = layers,
                onReorder = onReorder,
                selectedLayer = selectedLayer,
                onLayerSelected = onLayerSelected,
                createLayer = createLayer,
                onAttribute = onAttribute,
                onHideSwitch = onHideSwitch,
                enabled = isPreviewMode.not()
            )
        }
    )
}

@Composable
private fun EditorMenuContent(
    closeScreen: () -> Unit,
    addNewButton: () -> Unit,
    addNewText: () -> Unit,
    addNewJoystick: () -> Unit,
    openStyleList: () -> Unit,
    openJoystickStyleList: () -> Unit,
    isPreviewMode: Boolean,
    onPreviewChanged: (Boolean) -> Unit,
    previewScenario: PreviewScenario,
    onPreviewScenarioChanged: (PreviewScenario) -> Unit,
    previewHideLayerWhen: HideLayerWhen,
    onPreviewHideLayerChanged: (HideLayerWhen) -> Unit,
    onSave: () -> Unit,
    saveAndExit: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = cardColor(false),
    contentColor: Color = onCardColor()
) {
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = modifier.lazyScrollWithBar(listState),
        state = listState,
        contentPadding = PaddingValues(all = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        //Add a button
        item {
            MenuTextButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = isPreviewMode.not(),
                text = stringResource(R.string.control_editor_menu_new_widget_button),
                onClick = addNewButton,
                color = color,
                contentColor = contentColor,
            )
        }

        //Add a text box
        item {
            MenuTextButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = isPreviewMode.not(),
                text = stringResource(R.string.control_editor_menu_new_widget_text),
                onClick = addNewText,
                color = color,
                contentColor = contentColor,
            )
        }

        //Add a joystick
        item {
            MenuTextButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = isPreviewMode.not(),
                text = stringResource(R.string.control_editor_menu_new_widget_joystick),
                onClick = addNewJoystick,
                color = color,
                contentColor = contentColor,
            )
        }

        //Widget style list
        item {
            MenuTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.control_editor_edit_style_config),
                enabled = isPreviewMode.not(),
                onClick = {
                    openStyleList()
                    closeScreen()
                },
                color = color,
                contentColor = contentColor,
            )
        }

        //Joystick style list
        item {
            MenuTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.control_editor_edit_joystick_style_list),
                enabled = isPreviewMode.not(),
                onClick = {
                    openJoystickStyleList()
                    closeScreen()
                },
                color = color,
                contentColor = contentColor,
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        //Preview the control layout
        item {
            MenuSwitchButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.control_editor_menu_preview_mode),
                switch = isPreviewMode,
                onSwitch = { onPreviewChanged(it) },
                color = color,
                contentColor = contentColor,
            )
        }

        //Preview scenarios
        item {
            MenuListLayout(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.control_editor_menu_preview_mode_scenario),
                items = PreviewScenario.entries,
                currentItem = previewScenario,
                onItemChange = onPreviewScenarioChanged,
                getItemText = { scenario ->
                    stringResource(scenario.textRes)
                },
                color = color,
                contentColor = contentColor,
                enabled = isPreviewMode
            )
        }

        //A physical mouse is in use
        item {
            MenuSwitchButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.control_editor_menu_preview_is_mouse),
                switch = previewHideLayerWhen == HideLayerWhen.WhenMouse,
                onSwitch = { value ->
                    onPreviewHideLayerChanged(
                        if (value) HideLayerWhen.WhenMouse
                        else HideLayerWhen.None
                    )
                },
                color = color,
                contentColor = contentColor,
                enabled = isPreviewMode
            )
        }

        //A gamepad is in use
        item {
            MenuSwitchButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.control_editor_menu_preview_is_gamepad),
                switch = previewHideLayerWhen == HideLayerWhen.WhenGamepad,
                onSwitch = { value ->
                    onPreviewHideLayerChanged(
                        if (value) HideLayerWhen.WhenGamepad
                        else HideLayerWhen.None
                    )
                },
                color = color,
                contentColor = contentColor,
                enabled = isPreviewMode
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        //Widget snapping
        item {
            MenuSwitchButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.control_editor_menu_widget_snap),
                switch = AllSettings.editorEnableWidgetSnap.state,
                onSwitch = { AllSettings.editorEnableWidgetSnap.save(it) },
                color = color,
                contentColor = contentColor,
            )
        }

        //Snap across all widget layers
        item {
            MenuSwitchButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.control_editor_menu_widget_snap_all_layers),
                switch = AllSettings.editorSnapInAllLayers.state,
                onSwitch = { AllSettings.editorSnapInAllLayers.save(it) },
                color = color,
                contentColor = contentColor,
            )
        }

        //Widget snap mode
        item {
            MenuListLayout(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.control_editor_menu_widget_snap_mode),
                items = SnapMode.entries,
                currentItem = AllSettings.editorWidgetSnapMode.state,
                onItemChange = { AllSettings.editorWidgetSnapMode.save(it) },
                getItemText = { mode ->
                    val textRes = when (mode) {
                        SnapMode.FullScreen -> R.string.control_editor_menu_widget_snap_mode_fullscreen
                        SnapMode.Local -> R.string.control_editor_menu_widget_snap_mode_local
                    }
                    stringResource(textRes)
                },
                color = color,
                contentColor = contentColor,
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        //Save
        item {
            MenuTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.generic_save),
                onClick = onSave,
                color = color,
                contentColor = contentColor,
            )
        }

        //Save and exit
        item {
            MenuTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.control_editor_menu_save_and_exit),
                onClick = saveAndExit,
                color = color,
                contentColor = contentColor,
            )
        }

        //Exit directly
        item {
            MenuTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(R.string.control_editor_exit_confirm),
                onClick = onExit,
                color = color,
                contentColor = contentColor,
            )
        }
    }
}

@Composable
private fun ColumnScope.ControlLayerMenu(
    layers: List<ObservableControlLayer>,
    onReorder: (from: Int, to: Int) -> Unit,
    selectedLayer: ObservableControlLayer?,
    onLayerSelected: (ObservableControlLayer?) -> Unit,
    createLayer: () -> Unit,
    onAttribute: (ObservableControlLayer) -> Unit,
    onHideSwitch: (ObservableControlLayer) -> Unit,
    color: Color = cardColor(false),
    contentColor: Color = onCardColor(),
    enabled: Boolean = true
) {
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            onReorder(from.index, to.index)
        }
    )

    LaunchedEffect(Unit) {
        runCatching {
            val index = layers.indexOfFirst { it == selectedLayer }
            if (index >= 0 && index < layers.size) {
                lazyListState.animateScrollToItem(index)
            }
        }
    }

    //Scroll to the top on new list entries
    var previousSize by remember { mutableIntStateOf(0) }
    val currentSize = layers.size
    LaunchedEffect(currentSize) {
        if (currentSize != previousSize) {
            if (currentSize - previousSize > 0) {
                runCatching {
                    lazyListState.animateScrollToItem(0)
                }
            }
            previousSize = currentSize
        }
    }

    LazyColumn(
        modifier = Modifier
            .weight(1f)
            .lazyScrollWithBar(lazyListState),
        state = lazyListState,
        contentPadding = PaddingValues(all = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(layers, { it.uuid }) { layer ->
            ReorderableItem(
                state = reorderableLazyListState,
                key = layer.uuid,
                enabled = enabled,
            ) { isDragging ->
                val shadowElevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                ControlLayerItem(
                    modifier = Modifier.fillMaxWidth(),
                    layer = layer,
                    dragButtonModifier = Modifier.draggableHandle(),
                    selected = selectedLayer == layer,
                    shadowElevation = shadowElevation,
                    onSelected = {
                        onLayerSelected(layer)
                    },
                    onUnSelected = {
                        onLayerSelected(null)
                    },
                    onAttribute = {
                        onAttribute(layer)
                    },
                    onHideSwitch = {
                        onHideSwitch(layer)
                    },
                    color = color,
                    contentColor = contentColor,
                    enabled = enabled
                )
            }
        }
    }
    ScalingActionButton(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .padding(bottom = 4.dp)
            .fillMaxWidth(),
        onClick = createLayer
    ) {
        MarqueeText(text = stringResource(R.string.control_editor_layers_create))
    }
}

@Composable
private fun ControlLayerItem(
    modifier: Modifier = Modifier,
    layer: ObservableControlLayer,
    dragButtonModifier: Modifier,
    selected: Boolean,
    onSelected: () -> Unit,
    onUnSelected: () -> Unit,
    onAttribute: () -> Unit,
    onHideSwitch: () -> Unit,
    color: Color,
    contentColor: Color,
    borderColor: Color = MaterialTheme.colorScheme.primary,
    shape: Shape = MaterialTheme.shapes.large,
    shadowElevation: Dp = 0.dp,
    enabled: Boolean = true
) {
    val borderWidth by animateDpAsState(
        if (selected && enabled) 4.dp
        else (-1).dp
    )

    Surface(
        modifier = modifier.border(
            width = borderWidth,
            color = borderColor,
            shape = shape
        ),
        color = color,
        contentColor = contentColor,
        shape = shape,
        shadowElevation = shadowElevation,
        onClick = {
            if (selected) onUnSelected()
            else onSelected()
        },
        enabled = enabled
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape = MaterialTheme.shapes.large)
                .padding(all = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onHideSwitch,
                enabled = enabled
            ) {
                Crossfade(
                    targetState = layer.editorHide
                ) { isHide ->
                    Icon(
                        painter = painterResource(
                            if (isHide) {
                                R.drawable.ic_visibility_off_outlined
                            } else {
                                R.drawable.ic_visibility_outlined
                            }
                        ),
                        contentDescription = null
                    )
                }
            }
            MarqueeText(
                modifier = Modifier.weight(1f),
                text = layer.name,
                style = MaterialTheme.typography.bodyMedium
            )
            IconButton(
                onClick = onAttribute,
                enabled = enabled
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_more_horiz),
                    contentDescription = stringResource(R.string.control_editor_layers_attribute)
                )
            }
            Row(
                modifier = dragButtonModifier
                    .fillMaxHeight()
                    .clip(CircleShape),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    modifier = Modifier.padding(all = 4.dp),
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = null
                )
            }
        }
    }
}
