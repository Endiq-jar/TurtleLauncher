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

import com.endiq.layer_controller.observable.ObservableControlLayer

/**
 * Handler dedicated to widget-triggered events
 * @param handle handles the event
 */
class EventHandler(
    private val handle: (event: ClickEvent, pressed: Boolean) -> Unit = { _, _ -> }
) {
    /**
     * Normal button press event
     * @param handle decides whether to handle the event
     */
    internal fun onKeyPressed(
        clickEvents: List<ClickEvent>,
        isPressed: Boolean,
        handle: (ClickEvent) -> Boolean = { true }
    ) {
        for (event in clickEvents) {
            if (handle(event)) handle(event, isPressed)
        }
    }

    /**
     * Handles toggling layout visibility
     */
    internal fun onSwitchLayer(
        clickEvent: ClickEvent,
        allLayers: List<ObservableControlLayer>,
        switch: (ObservableControlLayer) -> Unit,
        show: (ObservableControlLayer) -> Unit,
        hide: (ObservableControlLayer) -> Unit
    ) {
        fun findLayer() = allLayers.find { it.uuid == clickEvent.key }

        when (clickEvent.type) {
            ClickEvent.Type.SwitchLayer -> findLayer()?.let { switch(it) }
            ClickEvent.Type.ShowLayer -> findLayer()?.let { show(it) }
            ClickEvent.Type.HideLayer -> findLayer()?.let { hide(it) }
            else -> {}
        }
    }
}