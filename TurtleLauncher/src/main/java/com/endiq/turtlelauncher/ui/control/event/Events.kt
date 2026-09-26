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

package com.endiq.turtlelauncher.ui.control.event

import com.endiq.inputmap.keycodes.ControlEventKeycode
import org.lwjgl.glfw.CallbackBridge

/**
 * Handles LWJGL key events when buttons are tapped
 */
fun lwjglEvent(
    eventKey: String,
    isMouse: Boolean,
    isPressed: Boolean
) {
    val keycode: Int = ControlEventKeycode.getKeycodeFromEvent(eventKey)?.toInt() ?: return

    if (isMouse) {
        CallbackBridge.sendMouseButton(keycode, isPressed)
    } else {
        CallbackBridge.sendKeyPress(keycode, CallbackBridge.getCurrentMods(), isPressed)
        CallbackBridge.setModifiers(keycode, isPressed)
    }
}

//Launcher click event

/** Switch the IME */
const val LAUNCHER_EVENT_SWITCH_IME = "launcher.event.switch_ime"
/** Toggle the menu */
const val LAUNCHER_EVENT_SWITCH_MENU = "launcher.event.switch_menu"
/** Virtual mouse wheel up: repeat while held */
const val LAUNCHER_EVENT_SCROLL_UP = "launcher.event.scroll_up"
/** Virtual mouse wheel up: single tap */
const val LAUNCHER_EVENT_SCROLL_UP_SINGLE = "launcher.event.scroll_up.single"
/** Virtual mouse wheel down: repeat while held */
const val LAUNCHER_EVENT_SCROLL_DOWN = "launcher.event.scroll_down"
/** Virtual mouse wheel down: single tap */
const val LAUNCHER_EVENT_SCROLL_DOWN_SINGLE = "launcher.event.scroll_down.single"

/**
 * Handles launcher events when buttons are tapped
 */
fun launcherEvent(
    eventKey: String,
    isPressed: Boolean,
    onSwitchIME: () -> Unit,
    onSwitchMenu: () -> Unit,
    onSingleScrollUp: () -> Unit,
    onSingleScrollDown: () -> Unit,
    onLongScrollUp: () -> Unit,
    onLongScrollUpCancel: () -> Unit,
    onLongScrollDown: () -> Unit,
    onLongScrollDownCancel: () -> Unit
) {
    if (eventKey.startsWith("GLFW_MOUSE_", false)) {
        //Handle mouse events
        lwjglEvent(eventKey = eventKey, isMouse = true, isPressed = isPressed)
    } else {
        if (isPressed) {
            when (eventKey) {
                LAUNCHER_EVENT_SWITCH_IME -> onSwitchIME()
                LAUNCHER_EVENT_SWITCH_MENU -> onSwitchMenu()
                LAUNCHER_EVENT_SCROLL_UP_SINGLE -> onSingleScrollUp()
                LAUNCHER_EVENT_SCROLL_DOWN_SINGLE -> onSingleScrollDown()
            }
        }
        when (eventKey) {
            LAUNCHER_EVENT_SCROLL_UP -> {
                if (isPressed) {
                    onLongScrollUp()
                } else {
                    onLongScrollUpCancel()
                }
            }
            LAUNCHER_EVENT_SCROLL_DOWN -> {
                if (isPressed) {
                    onLongScrollDown()
                } else {
                    onLongScrollDownCancel()
                }
            }
        }
    }
}