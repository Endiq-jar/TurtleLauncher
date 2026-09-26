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

import androidx.compose.ui.input.pointer.PointerId

/**
 * Shared multi-pointer state manager
 * Manages each pointer's active widget list and swipe-chain state
 */
class PointerEventBus {
    /**
     * pointer → widgets currently pressed (in join order)
     */
    private val _activeWidgets = mutableMapOf<PointerId, MutableList<ObservableWidget>>()

    /**
     * Set of pointers currently inside a swipe chain
     */
    private val _swipeChainPointers = mutableSetOf<PointerId>()

    /**
     * Checks occupied pointers (from external layers like MouseControlLayout / Hotbar)
     */
    var checkOccupiedPointers: (PointerId) -> Boolean = { false }

    /**
     * Marks a pointer as move-only (without consuming events)
     */
    var markPointerAsMoveOnly: (PointerId) -> Unit = {}

    // ────────────────────────────────────────────────────────
    // Lifecycle
    // ────────────────────────────────────────────────────────

    /**
     * On finger lift: clears all state of that pointer and returns the widgets to release
     */
    fun endPointer(pointerId: PointerId): List<ObservableWidget> {
        _swipeChainPointers.remove(pointerId)
        return _activeWidgets.remove(pointerId)?.toList() ?: emptyList()
    }

    // ────────────────────────────────────────────────────────
    // Active widget management
    // ────────────────────────────────────────────────────────

    /**
     * Returns the current active widget list of the given pointer
     */
    fun activeWidgets(pointerId: PointerId): List<ObservableWidget> {
        return _activeWidgets[pointerId]?.toList() ?: emptyList()
    }

    /**
     * Adds a widget to the given pointer's active list
     */
    fun addActiveWidget(pointerId: PointerId, widget: ObservableWidget) {
        _activeWidgets.getOrPut(pointerId) { mutableListOf() }.add(widget)
    }

    /**
     * Returns a snapshot of the active widgets
     */
    fun snapshot(pointerId: PointerId): List<ObservableWidget> = activeWidgets(pointerId)

    /**
     * Replaces the given pointer's active widget list
     */
    fun setActiveWidgets(pointerId: PointerId, widgets: List<ObservableWidget>) {
        _activeWidgets[pointerId] = widgets.toMutableList()
    }

    // ────────────────────────────────────────────────────────
    // Swipe chain management
    // ────────────────────────────────────────────────────────

    /** Whether the given pointer is inside a swipe chain */
    fun isInSwipeChain(pointerId: PointerId): Boolean = pointerId in _swipeChainPointers

    /** Marks a pointer as having entered a swipe chain */
    fun enterSwipeChain(pointerId: PointerId) {
        _swipeChainPointers.add(pointerId)
    }

    /** Removes a pointer from the swipe chain */
    fun exitSwipeChain(pointerId: PointerId) {
        _swipeChainPointers.remove(pointerId)
    }
}
