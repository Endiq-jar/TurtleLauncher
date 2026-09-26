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

package com.endiq.turtlelauncher.ui.control.gamepad

import android.os.Parcelable
import android.util.SparseArray
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.viewmodel.GamepadViewModel
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

private const val TAG = "GamepadRemapper"

private const val AXIS_TO_KEY_ACTIVATION_THRESHOLD = 0.6f
private const val AXIS_TO_KEY_RESET_THRESHOLD = 0.4f

/**
 * The current remapping version number
 */
private const val REMAPPER_VERSION = 2

/**
 * Saved data of gamepad event remapping
 * @param motionMapping [MotionEvent] event mapping
 * @param keyMapping [KeyEvent] event mapping
 * @param version the mapping data version; always compared against the local version
 */
@Parcelize
data class GamepadRemapper(
    val motionMapping: Map<Int, Int>,
    val keyMapping: Map<Int, Int>,
    val version: Int = 0
): Parcelable {
    constructor(
        motionMapping: Map<Int, Int>,
        keyMapping: Map<Int, Int>
    ): this(motionMapping, keyMapping, REMAPPER_VERSION)

    @IgnoredOnParcel private val reverseMotionMap by lazy {
        motionMapping.entries.associate { (key, value) ->
            value to key
        }
    }

    @IgnoredOnParcel private val currentKeyValues = SparseArray<Float>()
    @IgnoredOnParcel private val currentMotionValues = SparseArray<Float>()

    /**
     * Compares remapping version numbers
     * @return whether it's outdated
     */
    fun isOldVersion(): Boolean {
        return version < REMAPPER_VERSION
    }

    /**
     * If the event is a valid gamepad event, forward it to the [GamepadViewModel]
     * Note: unchanged values won't fire the handler
     *
     * @param event the current MotionEvent
     * @return whether the input was handled
     */
    fun handleMotionEventInput(event: MotionEvent, gamepadViewModel: GamepadViewModel): Boolean {
        if (!event.isJoystickMoving()) return false

        handleMotionIfDifferent(MotionEvent.AXIS_HAT_X, getRemappedValue(MotionEvent.AXIS_HAT_X, event), gamepadViewModel)
        handleMotionIfDifferent(MotionEvent.AXIS_HAT_Y, getRemappedValue(MotionEvent.AXIS_HAT_Y, event), gamepadViewModel)
        handleMotionIfDifferent(MotionEvent.AXIS_RTRIGGER, getRemappedValue(MotionEvent.AXIS_RTRIGGER, event), gamepadViewModel)
        handleMotionIfDifferent(MotionEvent.AXIS_LTRIGGER, getRemappedValue(MotionEvent.AXIS_LTRIGGER, event), gamepadViewModel)

        handleJoystickInput(event, gamepadViewModel, MotionEvent.AXIS_X, MotionEvent.AXIS_Y)
        handleJoystickInput(event, gamepadViewModel, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ)
        return true
    }

    /**
     * If the event is a valid gamepad key event, pass it to [GamepadViewModel]
     *
     * @param event the current KeyEvent
     * @return whether the input was handled
     */
    fun handleKeyEventInput(
        event: KeyEvent,
        gamepadViewModel: GamepadViewModel
    ): Boolean {
        if (!event.isGamepadKeyEvent()) return false
        if (event.keyCode == KeyEvent.KEYCODE_UNKNOWN) return false
        if (event.repeatCount > 0) return false

        val mappedSource = getRemappedSource(event)
        val currentValue = getRemappedValue(mappedSource, event)
        val lastValue = currentKeyValues[mappedSource]

        if (lastValue == null || currentValue != lastValue) {
            currentValue?.let { value ->
                gamepadViewModel.updateButton(mappedSource, value > 0f)
            }
            currentKeyValues[mappedSource] = currentValue
        }
        return true
    }

    private fun handleJoystickInput(
        event: MotionEvent,
        gamepadViewModel: GamepadViewModel,
        horizontalAxis: Int,
        verticalAxis: Int
    ) {
        var x = getRemappedValue(horizontalAxis, event)
        var y = getRemappedValue(verticalAxis, event)

        val magnitude = getMagnitude(x, y)
        val deadzone = getDeadzone(event, getRemappedSource(horizontalAxis))

        if (magnitude < deadzone) {
            x = 0f
            y = 0f
        } else {
            //Compensate for the deadzone
            x = ((x / magnitude) * ((magnitude - deadzone) / (1 - deadzone))).toFloat()
            y = ((y / magnitude) * ((magnitude - deadzone) / (1 - deadzone))).toFloat()
        }

        handleMotionIfDifferent(horizontalAxis, x, gamepadViewModel)
        handleMotionIfDifferent(verticalAxis, y, gamepadViewModel)
    }

    private fun handleMotionIfDifferent(
        mappedSource: Int,
        value: Float,
        gamepadViewModel: GamepadViewModel
    ) {
        val lastValue = currentMotionValues[mappedSource]
        if (lastValue == null || lastValue != value) {
            gamepadViewModel.updateMotion(mappedSource, value)
            currentMotionValues[mappedSource] = value
        }
    }

    /**
     * Gets the distance between (0,0) and (|x|,|y|), i.e. the vector magnitude
     */
    private fun getMagnitude(x: Float, y: Float): Double {
        val dx = abs(x)
        val dy = abs(y)
        return hypot(dx.toDouble(), dy.toDouble())
    }

    private fun getDeadzone(event: MotionEvent, axis: Int): Float {
        return try {
            val range = event.device?.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK)
            val deadzoneScale = AllSettings.gamepadDeadZoneScale.state / 100f
            val deadzone = (range?.flat ?: 0f) * deadzoneScale
            max(deadzone, 0.1f * deadzoneScale)
        } catch (e: Exception) {
            Logger.error(TAG, "Dynamic Deadzone is not supported", e)
            0.2f
        }
    }

    private fun getRemappedSource(axisSource: Int): Int {
        return reverseMotionMap[axisSource] ?: axisSource
    }

    private fun getRemappedValue(originalSource: Int, motionEvent: MotionEvent): Float {
        val mappedSource = getRemappedSource(originalSource)

        return if (supportedAxis.any { it == mappedSource }) {
            motionEvent.getAxisValue(mappedSource)
        } else {
            // Otherwise convert it back into key events
            // Assume only one button maps to the final value
            // Since events convert back into "KeyEvent", values stay 0 or 1
            val isEnabled = (currentMotionValues[originalSource] ?: 0.0f) == 1.0f
            val absoluteValue = abs(motionEvent.getAxisValue(mappedSource))

            if (isEnabled) {
                if (absoluteValue >= AXIS_TO_KEY_RESET_THRESHOLD) 1f else 0f
            } else {
                if (absoluteValue >= AXIS_TO_KEY_ACTIVATION_THRESHOLD) 1f else 0f
            }
        }
    }

    /**
     * Converts a keycode into its axis, needed
     * because some axes and d-pad buttons are functionally the same keycode
     */
    private fun transformKeyEventInput(keycode: Int): Int {
        return when (keycode) {
            KeyEvent.KEYCODE_BUTTON_L2 -> MotionEvent.AXIS_LTRIGGER
            KeyEvent.KEYCODE_BUTTON_R2 -> MotionEvent.AXIS_RTRIGGER
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> MotionEvent.AXIS_HAT_Y
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> MotionEvent.AXIS_HAT_X
            else -> keycode
        }
    }

    private fun getRemappedSource(event: KeyEvent): Int {
        val translatedSource = transformKeyEventInput(event.keyCode)
        return keyMapping[translatedSource] ?: translatedSource
    }

    private fun getRemappedValue(mappedSource: Int, keyEvent: KeyEvent): Float? {
        //DPAD and triggers are special: always map to nulll
        val isDpad = (mappedSource == MotionEvent.AXIS_HAT_Y && keyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) ||
                (mappedSource == MotionEvent.AXIS_HAT_X && keyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT)
        val isTrigger = (mappedSource == MotionEvent.AXIS_LTRIGGER || keyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_L2) ||
                (mappedSource == MotionEvent.AXIS_RTRIGGER || keyEvent.keyCode == KeyEvent.KEYCODE_BUTTON_R2)
        if (isDpad || isTrigger) return null

        return when (keyEvent.action) {
            KeyEvent.ACTION_DOWN, KeyEvent.ACTION_MULTIPLE -> 1f
            else -> 0f
        }
    }
}
