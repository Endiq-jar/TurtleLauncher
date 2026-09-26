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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.unit.IntSize
import com.endiq.layer_controller.data.ButtonPosition
import com.endiq.layer_controller.data.ButtonSize
import com.endiq.layer_controller.data.VisibilityType
import com.endiq.layer_controller.event.EventHandler

/**
 * Base observable widget wrapper
 */
abstract class ObservableWidget {
    /**
     * In edit mode, whether the position is being edited
     */
    var isEditingPos by mutableStateOf(false)

    /**
     * In edit mode, tracks the live offset
     */
    var movingOffset by mutableStateOf(Offset.Zero)

    /**
     * The widget's internal render size
     */
    internal var internalRenderSize by mutableStateOf(IntSize.Zero)

    /**
     * The component's internal render position property
     */
    internal abstract val internalRenderPosition: ButtonPosition

    /**
     * Stores into the internal render position property
     */
    internal abstract fun putRenderPosition(position: ButtonPosition)

    /**
     * The component's style ID
     */
    internal abstract val styleId: String?

    /**
     * The component's size property
     */
    internal abstract val widgetSize: ButtonSize

    /**
     * Stores into the component's size property
     */
    internal abstract fun putWidgetSize(size: ButtonSize)

    /**
     * Interaction behavior model of a widget
     */
    abstract val behavior: InteractionBehavior

    /**
     * Confirms whether the component may respond to touch events
     */
    open fun canTouch(): Boolean = true

    /**
     * Provides the touch-handling Modifier for this widget
     * Every widget handles touch events through its own pointerInput
     * @param onOccupiedPointer called when a pointer gets occupied, used for pointer isolation
     * @param onReleasePointer called when a pointer gets released
     */
    open fun Modifier.touchModifier(
        pointerEventBus: PointerEventBus,
        eventHandler: EventHandler,
        allLayers: List<ObservableControlLayer>,
        screenSize: IntSize,
        onOccupiedPointer: (PointerId) -> Unit = {},
        onReleasePointer: (PointerId) -> Unit = {}
    ): Modifier = this

    /**
     * When the Compose tree starts layout
     */
    abstract fun onCompositionStart(eventHandler: EventHandler?)

    /**
     * When the Compose tree finishes layout
     */
    abstract fun onCompositionDispose(eventHandler: EventHandler?)

    /**
     * Returns the visibility type of this component
     */
    abstract fun onCheckVisibilityType(): VisibilityType

    /**
     * Whether this component supports deep touch hit-testing and taking the deepest
     */
    abstract fun supportsDeepTouchDetection(): Boolean

    /**
     * Checks whether this touch event should be handled
     * @return whether it can be handled
     */
    abstract fun canProcess(): Boolean

    /**
     * Responds to a touch event
     * @param allLayers all current observable control layers
     * @param activeWidgets widgets active under the current pointer
     * @param addThis marks this widget active under that pointer
     * @param consumeEvent whether to mark the event consumed
     */
    abstract fun onTouchEvent(
        eventHandler: EventHandler,
        allLayers: List<ObservableControlLayer>,
        activeWidgets: List<ObservableWidget>,
        addThis: () -> Unit,
        consumeEvent: (Boolean) -> Unit
    )

    /**
     * Whether this component uses the "out of bounds counts as release" interaction
     */
    abstract fun isReleaseOnOutOfBounds(): Boolean

    /**
     * The finger re-entered the component
     * @param allLayers all current observable control layers
     */
    abstract fun onPointerBackInBounds(
        eventHandler: EventHandler,
        allLayers: List<ObservableControlLayer>
    )

    /**
     * Responds to a touch release event
     * @param allLayers all current observable control layers
     */
    abstract fun onReleaseEvent(
        eventHandler: EventHandler,
        allLayers: List<ObservableControlLayer>
    )
}