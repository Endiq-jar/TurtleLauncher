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

package com.endiq.turtlelauncher.viewmodel

import android.util.SparseBooleanArray
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.core.util.set
import androidx.lifecycle.ViewModel
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.setting.enums.GamepadInputMode
import com.endiq.turtlelauncher.ui.control.gamepad.DpadDirection
import com.endiq.turtlelauncher.ui.control.gamepad.GamepadMap
import com.endiq.turtlelauncher.ui.control.gamepad.GamepadMapping
import com.endiq.turtlelauncher.ui.control.gamepad.GamepadMappingList
import com.endiq.turtlelauncher.ui.control.gamepad.GamepadRemap
import com.endiq.turtlelauncher.ui.control.gamepad.Joystick
import com.endiq.turtlelauncher.ui.control.gamepad.JoystickType
import com.endiq.turtlelauncher.ui.control.gamepad.keyMappingListMMKV
import com.endiq.turtlelauncher.ui.control.gamepad.keyMappingMMKV
import com.endiq.turtlelauncher.ui.control.joystick.JoystickDirection
import io.ktor.util.collections.ConcurrentSet

private const val BUTTON_PRESS_THRESHOLD = 0.85f
const val GAMEPAD_CONFIG_NAME_LENGTH = 16

private const val AXIS_ACTIVATION_THRESHOLD = 0.6f
private const val AXIS_RESET_THRESHOLD = 0.4f

class GamepadViewModel : ViewModel() {
    private val keyListeners = mutableListOf<(KeyEvent) -> Unit>()

    /**
     * Posts a key event
     */
    fun sendKeyEvent(event: KeyEvent) {
        keyListeners.forEach { listener ->
            listener(event)
        }
    }

    /**
     * Adds a native key event listener
     */
    fun registerKeyListener(listener: (KeyEvent) -> Unit) {
        this.keyListeners.add(listener)
    }

    /**
     * Removes a native key event listener
     */
    fun unregisterKeyListener(listener: (KeyEvent) -> Unit) {
        this.keyListeners.remove(listener)
    }

    private val eventListeners = mutableListOf<(Event) -> Unit>()

    private val actionListeners = mutableListOf<() -> Unit>()

    /**
     * Registers a gamepad activity listener
     */
    fun registerActionListener(listener: () -> Unit) {
        actionListeners.add(listener)
    }

    /**
     * Removes a registered gamepad activity listener
     */
    fun unregisterActionListener(listener: () -> Unit) {
        actionListeners.remove(listener)
    }

    private fun sendActionEvent() {
        actionListeners.forEach { listener ->
            listener()
        }
    }

    private val listMMKV = keyMappingListMMKV()
    private val oldMMKV = keyMappingMMKV()

    private val mappingLists = ConcurrentSet<GamepadMappingList>()
    var currentMapping: GamepadMappingList? = null
        private set

    /** Left stick state */
    private val leftJoystick = Joystick(JoystickType.Left)
    /** Right stick state */
    private val rightJoystick = Joystick(JoystickType.Right)

    /**
     * Gamepad activity state control
     */
    var gamepadEngaged by mutableStateOf(false)
        private set

    private var lastActivityTime = System.nanoTime()
    private var pollLevel = PollLevel.Close

    init {
        reloadAllMappings()
    }

    /**
     * Whether the gamepad input mode prompt is showing
     */
    var modePromptVisible by mutableStateOf(false)
        private set

    /**
     * Gamepad activity report; pops the prompt when unanswered
     * @return true when the prompt is unanswered and the caller must swallow this gamepad input,
     * then mapping, SDL passthrough, and binding wizards stay responsive to the gamepad
     */
    fun checkModePrompt(): Boolean {
        if (AllSettings.gamepadInputModePrompted.state) return false
        modePromptVisible = true
        return true
    }

    /**
     * The user confirmed the mode choice; save and close
     */
    fun confirmModePrompt(selected: GamepadInputMode) {
        AllSettings.gamepadInputMode.save(selected)
        AllSettings.gamepadInputModePrompted.save(true)
        modePromptVisible = false
    }

    /**
     * Checks and updates gamepad activity
     * @return the current polling rate tier
     */
    fun checkGamepadActive(): PollLevel {
        val now = System.nanoTime()

        if (
            leftJoystick.isUsing() ||
            rightJoystick.isUsing()
        ) {
            lastActivityTime = now
        }

        pollLevel = if (now - lastActivityTime < 10_000_000_000L) PollLevel.High else PollLevel.Close
        gamepadEngaged = pollLevel != PollLevel.Close

        return pollLevel
    }

    /** Active state updates */
    private fun onActive() {
        notifyActivity()
        val wasInactive = !gamepadEngaged
        lastActivityTime = System.nanoTime()
        if (wasInactive) {
            gamepadEngaged = true
            pollLevel = PollLevel.High
        }
    }

    /**
     * Reports gamepad activity
     */
    fun notifyActivity() {
        sendActionEvent()
    }

    fun reloadAllMappings() {
        mappingLists.clear()

        var movedOldData = false
        val defaultName = "default"

        if (oldMMKV.count() > 0) {
            val defaultMappings = mutableListOf<GamepadMapping>()
            GamepadMap.entries.forEach { entry ->
                val mapping = oldMMKV.decodeParcelable(entry.identifier, GamepadMapping::class.java)
                    ?: GamepadMapping(
                        key = entry.gamepad,
                        dpadDirection = entry.dpadDirection,
                        targetsInGame = entry.defaultKeysInGame,
                        targetsInMenu = entry.defaultKeysInMenu
                    )
                defaultMappings.add(mapping)
            }
            val list = GamepadMappingList(
                name = defaultName,
                list = defaultMappings
            )
            oldMMKV.clearAll()
            listMMKV.encode(defaultName, list)

            mappingLists.add(list)
            movedOldData = true
            AllSettings.gamepadMappingConfig.save(defaultName)
        }

        if (!movedOldData && listMMKV.count() == 0L) {
            //No config exists yet
            val list = createDefaultMapping(defaultName)
            AllSettings.gamepadMappingConfig.save(defaultName)
            listMMKV.encode(defaultName, list)
            mappingLists.add(list)
        } else {
            listMMKV.allKeys()?.forEach { key ->
                if (movedOldData && defaultName == key) return@forEach
                listMMKV.decodeParcelable(key, GamepadMappingList::class.java)?.let {
                    mappingLists.add(it)
                }
            }
        }

        refreshLists()
    }

    private fun refreshLists() {
        mappingLists.forEach { list ->
            list.load()
        }
        currentMapping = loadCurrentConfig()
    }

    private fun loadCurrentConfig(): GamepadMappingList? {
        val config = AllSettings.gamepadMappingConfig.getValue()
        return mappingLists.find {
            it.name == config
        } ?: mappingLists.firstOrNull()?.also { config ->
            AllSettings.gamepadMappingConfig.save(config.name)
        }
    }

    /**
     * Returns all gamepad mapping config names
     */
    fun getAllConfigKeys(): List<String> {
        return mappingLists.map { it.name }
    }

    /**
     * Whether a saved config already uses the name
     */
    fun containsConfig(name: String): Boolean = listMMKV.containsKey(name)

    /**
     * Creates a new gamepad mapping config
     */
    fun createNewConfig(
        name: String,
        onContainsConfig: () -> Unit,
        onFinished: () -> Unit = {}
    ) {
        val name0 = name.take(GAMEPAD_CONFIG_NAME_LENGTH)
        if (containsConfig(name0)) onContainsConfig()

        val list = createDefaultMapping(name0)

        mappingLists.add(list)
        listMMKV.encode(name0, list)

        AllSettings.gamepadMappingConfig.save(name0)
        refreshLists()

        onFinished()
    }

    /**
     * Creates a default mapping config
     */
    private fun createDefaultMapping(name: String): GamepadMappingList {
        val defaultMappings = mutableListOf<GamepadMapping>()
        GamepadMap.entries.forEach { entry ->
            defaultMappings.add(
                GamepadMapping(
                    key = entry.gamepad,
                    dpadDirection = entry.dpadDirection,
                    targetsInGame = entry.defaultKeysInGame,
                    targetsInMenu = entry.defaultKeysInMenu
                )
            )
        }
        return GamepadMappingList(
            name = name,
            list = defaultMappings
        )
    }

    /**
     * Deletes a gamepad mapping config
     */
    fun deleteConfig(
        name: String,
        onFinished: () -> Unit = {}
    ) {
        mappingLists.removeIf { it.name == name }
        listMMKV.remove(name)
        refreshLists()

        onFinished()
    }

    fun updateButton(code: Int, pressed: Boolean) {
        onActive()
        sendEvent(Event.Button(code, pressed))
    }

    fun updateMotion(axisCode: Int, value: Float) {
        onActive()
        when (axisCode) {
            //Update stick state
            GamepadRemap.MotionX.code -> leftJoystick.updateState(horizontal = value)
            GamepadRemap.MotionY.code -> leftJoystick.updateState(vertical = value)
            GamepadRemap.MotionZ.code -> rightJoystick.updateState(horizontal = value)
            GamepadRemap.MotionRZ.code -> rightJoystick.updateState(vertical = value)
        }

        when (axisCode) {
            //Update trigger state
            GamepadRemap.MotionLeftTrigger.code,
            GamepadRemap.MotionRightTrigger.code -> {
                checkAxisPress(
                    axisCode, value,
                    onEvent = { isPressed ->
                        updateButton(axisCode, isPressed)
                    }
                )
            }

            //Update d-pad state
            GamepadRemap.MotionHatX.code -> {
                updateDpad(DpadDirection.Left, value < -BUTTON_PRESS_THRESHOLD)
                updateDpad(DpadDirection.Right, value > BUTTON_PRESS_THRESHOLD)
            }
            GamepadRemap.MotionHatY.code -> {
                updateDpad(DpadDirection.Up, value < -BUTTON_PRESS_THRESHOLD)
                updateDpad(DpadDirection.Down, value > BUTTON_PRESS_THRESHOLD)
            }
        }
    }

    private val axisStates = SparseBooleanArray()
    private fun checkAxisPress(
        axisCode: Int, value: Float,
        onEvent: (isPressed: Boolean) -> Unit,
        activation: Float = AXIS_ACTIVATION_THRESHOLD,
        reset: Float = AXIS_RESET_THRESHOLD
    ) {
        val isPressed = axisStates[axisCode]

        val press = if (isPressed) {
            value >= reset
        } else {
            value >= activation
        }
        axisStates[axisCode] = press

        if (isPressed != press) {
            onEvent(press)
        }
    }

    private fun updateDpad(direction: DpadDirection, pressed: Boolean) {
        onActive()
        sendEvent(Event.Dpad(direction, pressed))
    }

    /**
     * Poll-called; keeps emitting current stick state
     * @param deltaMs milliseconds elapsed since the last poll
     */
    fun pollJoystick(deltaMs: Double) {
        leftJoystick.onTick(deltaMs, ::sendEvent)
        rightJoystick.onTick(deltaMs, ::sendEvent)
    }

    private fun sendEvent(event: Event) {
        eventListeners.forEach { listener ->
            listener(event)
        }
    }

    /**
     * Adds a listener called immediately upon events
     */
    fun addEventListener(listener: (Event) -> Unit) {
        eventListeners.add(listener)
    }

    /**
     * Removes an added event listener
     */
    fun removeEventListener(listener: (Event) -> Unit) {
        eventListeners.remove(listener)
    }

    sealed interface Event {
        /**
         * Gamepad button press/release event
         * @param code the standard button code after mapping
         */
        data class Button(val code: Int, val pressed: Boolean) : Event

        /**
         * Gamepad stick offset event
         * @param joystickType stick type (left, right)
         */
        data class StickOffset(val joystickType: JoystickType, val offset: Offset) : Event

        /**
         * Gamepad stick direction change event
         * @param joystickType stick type (left, right)
         */
        data class StickDirection(val joystickType: JoystickType, val direction: JoystickDirection) : Event

        /**
         * Gamepad d-pad press/release event
         * @param direction the direction
         */
        data class Dpad(val direction: DpadDirection, val pressed: Boolean) : Event
    }

    enum class PollLevel(val delayMs: Long) {
        /**
         * High polling tier: 4ms delay ≈ 250fps
         */
        High(4L),

        /**
         * No polling
         */
        Close(10_000L)
    }
}