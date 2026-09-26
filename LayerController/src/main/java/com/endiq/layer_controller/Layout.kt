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

package com.endiq.layer_controller

import androidx.annotation.FloatRange
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.endiq.layer_controller.data.HideLayerWhen
import com.endiq.layer_controller.data.VisibilityType
import com.endiq.layer_controller.event.EventHandler
import com.endiq.layer_controller.layout.JoystickWidgetRenderer
import com.endiq.layer_controller.layout.TextButton
import com.endiq.layer_controller.observable.ObservableButtonStyle
import com.endiq.layer_controller.observable.ObservableControlLayer
import com.endiq.layer_controller.observable.ObservableControlLayout
import com.endiq.layer_controller.observable.ObservableJoystickStyle
import com.endiq.layer_controller.observable.ObservableWidget
import com.endiq.layer_controller.observable.PointerEventBus
import com.endiq.layer_controller.observable.TouchProcessor
import com.endiq.layer_controller.utils.getWidgetPosition

/**
 * Control layout canvas
 * @param observedLayout the control layout to observe and draw
 * @param eventHandler the handler used to process control layout events
 * @param checkOccupiedPointers checks occupied pointers
 * @param opacity overall opacity of the control layout canvas, 0f~1f
 * @param markPointerAsMoveOnly marks a pointer as move-only
 * @param hideLayerWhen decides whether to hide the given widget layer
 */
@Composable
fun ControlBoxLayout(
    modifier: Modifier = Modifier,
    observedLayout: ObservableControlLayout? = null,
    eventHandler: EventHandler = EventHandler(),
    isCursorGrabbing: Boolean,
    checkOccupiedPointers: (PointerId) -> Boolean,
    @FloatRange(0.0, 1.0) opacity: Float = 1f,
    markPointerAsMoveOnly: (PointerId) -> Unit = {},
    onOccupiedPointer: (PointerId) -> Unit = {},
    onReleasePointer: (PointerId) -> Unit = {},
    hideLayerWhen: HideLayerWhen = HideLayerWhen.None,
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable BoxScope.() -> Unit
) {
    when {
        observedLayout == null -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.BottomCenter
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.padding(all = 16.dp)
                )
            }
        }
        else -> {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                key(observedLayout.hashCode()) {
                    BoxWithConstraints(
                        modifier = modifier
                    ) {
                        BaseControlBoxLayout(
                            modifier = Modifier.fillMaxSize(),
                            observedLayout = observedLayout,
                            eventHandler = eventHandler,
                            checkOccupiedPointers = checkOccupiedPointers,
                            opacity = opacity,
                            markPointerAsMoveOnly = markPointerAsMoveOnly,
                            onOccupiedPointer = onOccupiedPointer,
                            onReleasePointer = onReleasePointer,
                            isCursorGrabbing = isCursorGrabbing,
                            hideLayerWhen = hideLayerWhen,
                            isDark = isDark,
                            content = content
                        )
                    }
                }
            }
        }
    }
}

/**
 * Control layout canvas
 */
@Composable
private fun BoxWithConstraintsScope.BaseControlBoxLayout(
    modifier: Modifier = Modifier,
    observedLayout: ObservableControlLayout,
    eventHandler: EventHandler,
    checkOccupiedPointers: (PointerId) -> Boolean,
    @FloatRange(0.0, 1.0) opacity: Float,
    markPointerAsMoveOnly: (PointerId) -> Unit,
    onOccupiedPointer: (PointerId) -> Unit,
    onReleasePointer: (PointerId) -> Unit,
    isCursorGrabbing: Boolean,
    hideLayerWhen: HideLayerWhen,
    isDark: Boolean,
    content: @Composable BoxScope.() -> Unit
) {
    val layers by observedLayout.layers.collectAsStateWithLifecycle()
    val reversedLayers = remember(layers) { layers.reversed() }
    val styles by observedLayout.styles.collectAsStateWithLifecycle()
    val joystickStyles by observedLayout.joystickStyles.collectAsStateWithLifecycle()

    val currentCheckOccupiedPointers by rememberUpdatedState(checkOccupiedPointers)
    val currentIsCursorGrabbing by rememberUpdatedState(isCursorGrabbing)
    val currentHideLayerWhen by rememberUpdatedState(hideLayerWhen)

    val density = LocalDensity.current
    val screenSize = remember(maxWidth, maxHeight) {
        with(density) {
            IntSize(
                width = maxWidth.roundToPx(),
                height = maxHeight.roundToPx()
            )
        }
    }

    // Shared multi-pointer state manager
    val pointerEventBus = remember { PointerEventBus() }
    pointerEventBus.checkOccupiedPointers = currentCheckOccupiedPointers
    pointerEventBus.markPointerAsMoveOnly = markPointerAsMoveOnly

    val touchProcessor = remember(screenSize) {
        TouchProcessor(eventHandler) { widget ->
            getWidgetPosition(widget, widget.internalRenderSize, screenSize)
        }
    }

    val currentTouchProcessor by rememberUpdatedState(touchProcessor)
    val currentMarkPointerAsMoveOnly by rememberUpdatedState(markPointerAsMoveOnly)

    Box(
        modifier = modifier
            .pointerInput(reversedLayers, hideLayerWhen) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)

                        event.changes.forEach { change ->
                            val pointerId = change.id
                            //Finger lifted: clear all state of this pointer
                            if (!change.pressed) {
                                pointerEventBus.endPointer(pointerId).forEach { widget ->
                                    //Release this pointer's events
                                    widget.onReleaseEvent(eventHandler, reversedLayers)
                                }
                                return@forEach
                            }

                            if (change.isConsumed || currentCheckOccupiedPointers(pointerId)) {
                                return@forEach //skip consumed or occupied pointers
                            }

                            //Collect visible widgets
                            val visibleWidgets = collectVisibleWidgets(
                                layers = layers,
                                hideLayerWhen = currentHideLayerWhen,
                                isCursorGrabbing = currentIsCursorGrabbing,
                            )

                            currentTouchProcessor.processFrame(
                                session = pointerEventBus,
                                change = change,
                                visibleWidgets = visibleWidgets,
                                allLayers = reversedLayers,
                                consumeEvent = { it.consume() },
                                markPointerAsMoveOnly = currentMarkPointerAsMoveOnly,
                            )
                        }
                    }
                }
            }
    ) {
        content()

        ControlsRendererLayer(
            isDark = isDark,
            opacity = opacity,
            layers = reversedLayers,
            styles = styles,
            joystickStyles = joystickStyles,
            screenSize = screenSize,
            pointerEventBus = pointerEventBus,
            eventHandler = eventHandler,
            isCursorGrabbing = currentIsCursorGrabbing,
            hideLayerWhen = currentHideLayerWhen,
            onOccupiedPointer = onOccupiedPointer,
            onReleasePointer = onReleasePointer
        )
    }
}

@Composable
private fun ControlsRendererLayer(
    isDark: Boolean,
    @FloatRange(0.0, 1.0) opacity: Float,
    layers: List<ObservableControlLayer>,
    styles: List<ObservableButtonStyle>,
    joystickStyles: List<ObservableJoystickStyle>,
    screenSize: IntSize,
    pointerEventBus: PointerEventBus,
    eventHandler: EventHandler,
    isCursorGrabbing: Boolean,
    hideLayerWhen: HideLayerWhen,
    onOccupiedPointer: (PointerId) -> Unit,
    onReleasePointer: (PointerId) -> Unit
) {
    Layout(
        modifier = Modifier.alpha(alpha = opacity),
        content = {
            //Render all visible widgets in layer order
            layers.forEach { layer ->
                val layerVisibility = checkLayerVisibility(
                    layer = layer,
                    hideLayerWhen = hideLayerWhen,
                    isCursorGrabbing = isCursorGrabbing,
                    visibilityType = layer.visibilityType
                )
                val normalButtons by layer.normalButtons.collectAsStateWithLifecycle()
                val textBoxes by layer.textBoxes.collectAsStateWithLifecycle()
                val joystickButtons by layer.joystickButtons.collectAsStateWithLifecycle()

                textBoxes.forEach { data ->
                    TextButton(
                        isEditMode = false,
                        data = data,
                        allStyles = styles,
                        screenSize = screenSize,
                        isDark = isDark,
                        visible = layerVisibility && checkVisibility(isCursorGrabbing, data.visibilityType),
                        getOtherWidgets = { emptyList() }, //no snapping needed
                        snapThresholdValue = 4.dp,
                        eventHandler = eventHandler,
                        isPressed = false //text boxes need no pressed state
                    )
                }

                normalButtons.forEach { data ->
                    TextButton(
                        isEditMode = false,
                        data = data,
                        allStyles = styles,
                        screenSize = screenSize,
                        isDark = isDark,
                        visible = layerVisibility && checkVisibility(isCursorGrabbing, data.visibilityType),
                        getOtherWidgets = { emptyList() }, //no snapping needed
                        snapThresholdValue = 4.dp,
                        eventHandler = eventHandler,
                        isPressed = data.isPressed
                    )
                }

                joystickButtons.forEach { data ->
                    JoystickWidgetRenderer(
                        data = data,
                        joystickStyles = joystickStyles,
                        screenSize = screenSize,
                        isDark = isDark,
                        visible = layerVisibility && checkVisibility(isCursorGrabbing, data.visibilityType),
                        pointerEventBus = pointerEventBus,
                        eventHandler = eventHandler,
                        reversedLayers = layers,
                        onOccupiedPointer = onOccupiedPointer,
                        onReleasePointer = onReleasePointer
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { measurable ->
            measurable.measure(constraints)
        }

        var index = 0
        fun ObservableWidget.putSize() {
            if (index < placeables.size) {
                val placeable = placeables[index]
                this.internalRenderSize = IntSize(placeable.width, placeable.height)
                index++
            }
        }

        layers.fastForEach { layer ->
            layer.textBoxes.value.fastForEach { it.putSize() }
            layer.normalButtons.value.fastForEach { it.putSize() }
            layer.joystickButtons.value.fastForEach { it.putSize() }
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            var placeableIndex = 0
            fun ObservableWidget.place() {
                if (placeableIndex < placeables.size) {
                    val placeable = placeables[placeableIndex]
                    val position = getWidgetPosition(
                        data = this,
                        widgetSize = IntSize(placeable.width, placeable.height),
                        screenSize = screenSize
                    )
                    placeable.place(position.x.toInt(), position.y.toInt())
                    placeableIndex++
                }
            }

            layers.fastForEach { layer ->
                layer.textBoxes.value.fastForEach { it.place() }
                layer.normalButtons.value.fastForEach { it.place() }
                layer.joystickButtons.value.fastForEach { it.place() }
            }
        }
    }
}

/**
 * Collects touchable widgets from all visible control layers
 */
private fun collectVisibleWidgets(
    layers: List<ObservableControlLayer>,
    hideLayerWhen: HideLayerWhen,
    isCursorGrabbing: Boolean,
): List<ObservableWidget> {
    return layers
        .filter { layer ->
            checkLayerVisibility(
                layer = layer,
                hideLayerWhen = hideLayerWhen,
                isCursorGrabbing = isCursorGrabbing,
                visibilityType = layer.visibilityType,
            )
        }
        .flatMap { layer ->
            //Reversed, top to bottom
            layer.normalButtons.value.reversed()
        }
        .filter { widget ->
            widget.canTouch() && checkVisibility(
                isCursorGrabbing = isCursorGrabbing,
                visibilityType = widget.onCheckVisibilityType()
            )
        }
}

private fun checkLayerVisibility(
    layer: ObservableControlLayer,
    hideLayerWhen: HideLayerWhen,
    isCursorGrabbing: Boolean,
    visibilityType: VisibilityType
): Boolean {
    if (layer.hide || !checkVisibility(isCursorGrabbing, visibilityType)) {
        return false
    }

    return !when (hideLayerWhen) {
        HideLayerWhen.WhenMouse -> layer.hideWhenMouse
        HideLayerWhen.WhenGamepad -> layer.hideWhenGamepad
        HideLayerWhen.None -> false
    }
}

/**
 * Uses virtual mouse capture state to decide whether widgets should be shown right now
 */
private fun checkVisibility(
    isCursorGrabbing: Boolean,
    visibilityType: VisibilityType
): Boolean {
    return when (visibilityType) {
        VisibilityType.ALWAYS -> true
        VisibilityType.IN_GAME -> isCursorGrabbing
        VisibilityType.IN_MENU -> !isCursorGrabbing
    }
}
