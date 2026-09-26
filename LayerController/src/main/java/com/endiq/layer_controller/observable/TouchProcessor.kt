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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import com.endiq.layer_controller.event.EventHandler

/**
 * Frame-level touch event processor
 */
class TouchProcessor(
    private val eventHandler: EventHandler,
    private val widgetPosition: (ObservableWidget) -> Offset,
) {
    /**
     * Checks whether the pointer coordinates fall inside a widget rect
     */
    private val hitTest: (widget: ObservableWidget, position: Offset) -> Boolean = { widget, position ->
        val size = widget.internalRenderSize
        val offset = widgetPosition(widget)
        position.x in offset.x..(offset.x + size.width) &&
                position.y in offset.y..(offset.y + size.height)
    }



    /**
     * Processes one frame of pointer events
     * @param visibleWidgets the pre-filtered visible widget list
     * @param allLayers all control layers
     * @param consumeEvent callback that consumes an event
     * @param markPointerAsMoveOnly callback that marks a pointer as move-only
     */
    fun processFrame(
        session: PointerEventBus,
        change: PointerInputChange,
        visibleWidgets: List<ObservableWidget>,
        allLayers: List<ObservableControlLayer>,
        consumeEvent: (PointerInputChange) -> Unit,
        markPointerAsMoveOnly: (PointerId) -> Unit,
    ) {
        val pointerId = change.id
        val position = change.position

        //Get the widgets hit by the pointer
        val targets = findTargets(visibleWidgets, position)
        handleOutOfBounds(session, pointerId, position, allLayers)

        routeToTargets(
            session = session,
            change = change,
            pointerId = pointerId,
            targets = targets,
            allLayers = allLayers,
            consumeEvent = consumeEvent,
            markPointerAsMoveOnly = markPointerAsMoveOnly,
        )
    }

    /**
     * Finds the widgets hit by the current pointer among the visible ones
     */
    private fun findTargets(
        visibleWidgets: List<ObservableWidget>,
        position: Offset,
    ): List<ObservableWidget> {
        val hitList = visibleWidgets.filter { widget ->
            widget.canTouch() && hitTest(widget, position)
        }
        if (hitList.isEmpty()) return emptyList()

        //Find the first widget that supports depth testing
        val firstDeepWidget = hitList
            .firstOrNull { it.supportsDeepTouchDetection() }
            ?: return hitList

        val topIndex = hitList.indexOf(firstDeepWidget)
        //Keep only that widget and the penetrable widgets above it
        return hitList.subList(0, topIndex + 1)
            .filter { !it.canProcess() }
    }

    /**
     * Handles out-of-bounds release of active widgets
     */
    private fun handleOutOfBounds(
        session: PointerEventBus,
        pointerId: PointerId,
        position: Offset,
        allLayers: List<ObservableControlLayer>,
    ) {
        val widgets = session.activeWidgets(pointerId)
        if (widgets.isEmpty()) return

        val preSnapshot = session.snapshot(pointerId)
        val backInBounds = mutableListOf<ObservableWidget>()
        val removed = mutableListOf<ObservableWidget>()

        for (widget in widgets) {
            if (!widget.behavior.releaseOnOutOfBounds) continue

            if (!hitTest(widget, position)) {
                widget.onReleaseEvent(eventHandler, allLayers)
                removed.add(widget)
            } else {
                //Pointer returned inside the widget bounds
                backInBounds.add(widget)
            }
        }

        if (removed.isNotEmpty()) {
            session.setActiveWidgets(pointerId, widgets - removed)
        }

        for (widget in backInBounds) {
            widget.onPointerBackInBounds(eventHandler, allLayers)
        }


        val currentWidgets = session.activeWidgets(pointerId)
        if (
            currentWidgets.isEmpty() &&
            //A swipple widget existed before going out of bounds
            preSnapshot.any { it.behavior is InteractionBehavior.Swipable }
        ) {
            session.enterSwipeChain(pointerId)
        }

        //Pointer is back on a widget: leave the swipe chain
        if (currentWidgets.isNotEmpty() && session.isInSwipeChain(pointerId)) {
            session.exitSwipeChain(pointerId)
        }
    }


    private fun routeToTargets(
        session: PointerEventBus,
        change: PointerInputChange,
        pointerId: PointerId,
        targets: List<ObservableWidget>,
        allLayers: List<ObservableControlLayer>,
        consumeEvent: (PointerInputChange) -> Unit,
        markPointerAsMoveOnly: (PointerId) -> Unit,
    ) {
        if (targets.isEmpty()) return

        val activeWidgets = session.activeWidgets(pointerId)

        for (target in targets) {
            if (target.canProcess()) return

            //Only swipple and non-toggleable widgets may pass
            if (
                session.isInSwipeChain(pointerId) &&
                !target.behavior.canBeSwipedTo
            ) {
                continue
            }

            target.onTouchEvent(
                eventHandler = eventHandler,
                allLayers = allLayers,
                activeWidgets = activeWidgets,
                addThis = {
                    session.addActiveWidget(pointerId, target)
                },
                consumeEvent = { shouldConsume ->
                    if (shouldConsume) {
                        consumeEvent(change)
                    } else {
                        markPointerAsMoveOnly(pointerId)
                    }
                },
            )
        }
    }
}
