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

package com.endiq.turtlelauncher.bridge

import androidx.compose.ui.input.pointer.PointerIcon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import android.view.PointerIcon as NativePointerIcon

object TLBridgeStates {

    private val _cursorMode = MutableStateFlow(CURSOR_ENABLED)
    /** State: pointer mode (enabled, disabled) */
    val cursorMode = _cursorMode.asStateFlow()

    /**
     * Changes the pointer mode
     */
    @JvmStatic
    fun changeCursorMode(mode: Int) {
        require(mode in 0..1)
        this._cursorMode.update { mode }
    }

    private val _cursorShape = MutableStateFlow(CursorShape.Arrow)
    /** State: pointer shape */
    val cursorShape = _cursorShape.asStateFlow()

    /**
     * Changes the pointer shape
     */
    @JvmStatic
    fun changeCursorShape(shape: CursorShape) {
        _cursorShape.update { shape }
    }

    @JvmStatic
    private val _windowChangeKey = MutableStateFlow(false)
    /** State: window change refresh key */
    val windowChangeKey = _windowChangeKey.asStateFlow()

    fun onWindowChange() {
        this._windowChangeKey.update { old -> old.not() }
    }
}

/** Pointer: enabled */
const val CURSOR_ENABLED = 1
/** Pointer: disabled */
const val CURSOR_DISABLED = 0

/**
 * Pointer shapes (currently arrow, ibeam, and hand only)
 */
enum class CursorShape(
    val composeIcon: PointerIcon
) {
    /**
     * Arrow
     */
    Arrow(PointerIcon.Default),

    /**
     * Ibeam
     */
    IBeam(PointerIcon.Text),

    /**
     * Hand
     */
    Hand(PointerIcon.Hand),

    /**
     * Cross
     */
    CrossHair(PointerIcon.Crosshair),

    /**
     * Resize (vertical)
     */
    ResizeNS(PointerIcon(NativePointerIcon.TYPE_VERTICAL_DOUBLE_ARROW)),

    /**
     * Resize (horizontal)
     */
    ResizeEW(PointerIcon(NativePointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW)),

    /**
     * Resize (all directions)
     */
    ResizeAll(PointerIcon(NativePointerIcon.TYPE_ALL_SCROLL)),

    /**
     * Not allowed / invalid operation
     */
    NotAllowed(PointerIcon(NativePointerIcon.TYPE_NO_DROP))
}