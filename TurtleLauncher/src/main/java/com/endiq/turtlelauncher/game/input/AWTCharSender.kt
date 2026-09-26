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

package com.endiq.turtlelauncher.game.input

import android.view.KeyEvent
import android.view.MotionEvent
import com.endiq.turtlelauncher.bridge.TLBridge

object AWTCharSender : CharacterSenderStrategy {
    override fun sendChar(character: Char) {
        TLBridge.sendChar(character)
    }

    override fun sendBackspace() {
        TLBridge.sendKey(' ', AWTInputEvent.VK_BACK_SPACE)
    }

    override fun sendLeft() {
        TLBridge.sendKey(' ', AWTInputEvent.VK_LEFT)
    }

    override fun sendRight() {
        TLBridge.sendKey(' ', AWTInputEvent.VK_RIGHT)
    }

    override fun sendUp() {
        TLBridge.sendKey(' ', AWTInputEvent.VK_UP)
    }

    override fun sendDown() {
        TLBridge.sendKey(' ', AWTInputEvent.VK_DOWN)
    }

    override fun sendEnter() {
        TLBridge.sendKey(' ', AWTInputEvent.VK_ENTER)
    }

    override fun sendTab() {
        TLBridge.sendKey(' ', AWTInputEvent.VK_TAB)
    }

    override fun sendOther(key: KeyEvent) {
        // Ignore
    }

    override fun sendCopy() {
        sendModifierCtrl(true)
        TLBridge.sendKey(' ', AWTInputEvent.VK_C)
        sendModifierCtrl(false)
    }

    override fun sendCut() {
        sendModifierCtrl(true)
        TLBridge.sendKey(' ', AWTInputEvent.VK_X)
        sendModifierCtrl(false)
    }

    override fun sendPaste() {
        sendModifierCtrl(true)
        TLBridge.sendKey(' ', AWTInputEvent.VK_V)
        sendModifierCtrl(false)
    }

    override fun sendSelectAll() {
        sendModifierCtrl(true)
        TLBridge.sendKey(' ', AWTInputEvent.VK_A)
        sendModifierCtrl(false)
    }

    override fun sendModifierShift(press: Boolean) {
        TLBridge.sendKey(' ', AWTInputEvent.VK_SHIFT, if (press) 1 else 0)
    }

    override fun sendModifierCtrl(press: Boolean) {
        TLBridge.sendKey(' ', AWTInputEvent.VK_CONTROL, if (press) 1 else 0)
    }

    /**
     * Returns an AWT mouse click event
     */
    fun getMouseButton(button: Int): Int? {
        return when (button) {
            MotionEvent.BUTTON_PRIMARY -> AWTInputEvent.BUTTON1_DOWN_MASK
            MotionEvent.BUTTON_SECONDARY, MotionEvent.BUTTON_STYLUS_SECONDARY -> AWTInputEvent.BUTTON3_DOWN_MASK
            MotionEvent.BUTTON_TERTIARY -> AWTInputEvent.BUTTON2_DOWN_MASK
            else -> null
        }
    }
}