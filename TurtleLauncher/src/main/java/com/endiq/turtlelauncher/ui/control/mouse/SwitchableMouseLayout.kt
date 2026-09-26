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

package com.endiq.turtlelauncher.ui.control.mouse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.endiq.turtlelauncher.bridge.CURSOR_DISABLED
import com.endiq.turtlelauncher.bridge.CURSOR_ENABLED
import com.endiq.turtlelauncher.bridge.TLBridgeStates
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.setting.enums.MouseControlMode
import com.endiq.turtlelauncher.ui.control.gamepad.GamepadStickCameraListener
import com.endiq.turtlelauncher.utils.device.PhysicalMouseChecker
import com.endiq.turtlelauncher.utils.file.ifExists
import com.endiq.turtlelauncher.viewmodel.GamepadViewModel

/**
 * Mouse pointer capture mode
 */
typealias CursorMode = Int

/**
 * Virtual pointer simulation layer that auto-switches on capture mode
 * @param cursorMode                the current pointer capture mode
 * @param controlMode               Control mode: SLIDE (slide control), CLICK (click control)
 * @param enableMouseClick          Whether virtual mouse click actions are enabled (slide control only)
 * @param longPressTimeoutMillis    long-press trigger detection timeout
 * @param requestPointerCapture     whether pointer capture is used
 * @param hideMouseInClickMode      whether the pointer hides in click-control mode
 * @param gamepadViewModel          ViewModel updating gamepad state
 * @param onTouch                   touch on the mouse layer
 * @param onMouse                   physical mouse interaction event
 * @param onTap                     tap callback
 * @param onCapturedTap             captured-mode tap callback; argument is the touch point's absolute coordinates in the control
 * @param onLongPress               long-press start callback
 * @param onLongPressEnd            long-press end callback
 * @param onCapturedLongPress       captured-mode long-press start callback
 * @param onCapturedLongPressEnd    captured-mode long-press end callback
 * @param onPointerMove             pointer move callback; takes the pointer position in SLIDE mode and the finger position in CLICK mode
 * @param onCapturedMove            captured-mode move callback, passing the slide offset
 * @param onMouseScroll             physical mouse wheel scrolling
 * @param onMouseButton             physical mouse button feedback
 * @param isMoveOnlyPointer         whether the parent marked the pointer as move-only
 * @param onOccupiedPointer         occupy-pointer callback
 * @param onReleasePointer          release-pointer callback
 * @param enableScrollGesture       whether the two-finger scroll gesture is enabled
 * @param onScrollGesture           two-finger scroll callback, passing the wheel offset
 * @param mouseSize                 pointer size
 * @param cursorSensitivity         pointer sensitivity (slide mode only)
 */
@Composable
fun SwitchableMouseLayout(
    modifier: Modifier = Modifier,
    screenSize: IntSize,
    cursorMode: CursorMode,
    controlMode: MouseControlMode = AllSettings.mouseControlMode.state,
    enableMouseClick: Boolean = AllSettings.enableMouseClick.state,
    longPressTimeoutMillis: Long = AllSettings.mouseLongPressDelay.state.toLong(),
    requestPointerCapture: Boolean = !AllSettings.physicalMouseMode.state,
    hideMouseInClickMode: Boolean = AllSettings.hideMouse.state,
    gamepadViewModel: GamepadViewModel? = null,
    onTouch: () -> Unit = {},
    onMouse: () -> Unit = {},
    onTap: (Offset) -> Unit = {},
    onCapturedTap: (Offset) -> Unit = {},
    onLongPress: () -> Unit = {},
    onLongPressEnd: () -> Unit = {},
    onCapturedLongPress: () -> Unit = {},
    onCapturedLongPressEnd: () -> Unit = {},
    onPointerMove: (Offset) -> Unit = {},
    onCapturedMove: (Offset) -> Unit = {},
    onMouseScroll: (Offset) -> Unit = {},
    onMouseButton: (button: Int, pressed: Boolean) -> Unit = { _, _ -> },
    isMoveOnlyPointer: (PointerId) -> Boolean = { false },
    onOccupiedPointer: (PointerId) -> Unit = {},
    onReleasePointer: (PointerId) -> Unit = {},
    enableScrollGesture: Boolean = false,
    onScrollGesture: (Offset) -> Unit = {},
    mouseSize: Dp = AllSettings.mouseSize.state.dp,
    cursorSensitivity: Int = AllSettings.cursorSensitivity.state,
    gamepadCursorSensitivity: Int = AllSettings.gamepadCursorSensitivity.state,
    gamepadCameraSensitivity: Int = AllSettings.gamepadCameraSensitivity.state
) {
    val screenWidth: Float = screenSize.width.toFloat()
    val screenHeight: Float = screenSize.height.toFloat()
    val centerPos = Offset(screenWidth / 2f, screenHeight / 2f)

    val speedFactor = remember(cursorSensitivity) { cursorSensitivity / 100f }
    val gamepadCursorSpeedFactor = remember(gamepadCursorSensitivity) { 19f * (gamepadCursorSensitivity / 100f) }
    val gamepadCameraSpeedFactor = remember(gamepadCameraSensitivity) { 18f * (gamepadCameraSensitivity / 100f) }

    val lastVirtualMousePos = remember { object { var value: Offset? = null } }

    //Check whether the mouse is being captured
    val isCaptured by remember(cursorMode) {
        mutableStateOf(
            value = cursorMode == CURSOR_DISABLED
        )
    }

    //Is this physical mouse mode right now?
    var isPhysicalMouseMode by remember {
        mutableStateOf(
            if (PhysicalMouseChecker.physicalMouseConnected) { //physical mouse connected
                !requestPointerCapture //physical mouse visibility follows capture mode (virtual mouse control mode)
            } else {
                false
            }
        )
    }
    //Check and apply the current physical mouse mode
    //If it's in use while not captured, mark physical mouse mode
    fun checkPhysicalMouseMode(using: Boolean) {
        isPhysicalMouseMode = !requestPointerCapture && using
    }

    var showMousePointer by remember {
        mutableStateOf(requestPointerCapture)
    }
    fun updateMousePointer(show: Boolean) {
        showMousePointer = show
    }
    LaunchedEffect(cursorMode, hideMouseInClickMode) {
        updateMousePointer(
            show = if (cursorMode == CURSOR_ENABLED) {
                when {
                    //Physical mouse connected and physical mode active: is it captured control mode?
                    PhysicalMouseChecker.physicalMouseConnected && isPhysicalMouseMode -> requestPointerCapture
                    //Click-control mode: decided by the hide-virtual-mouse setting
                    controlMode == MouseControlMode.CLICK -> !hideMouseInClickMode
                    //Slide control always shows it
                    else -> controlMode == MouseControlMode.SLIDE
                }
            } else false
        )
    }

    val requestPointerCapture1 by remember(isCaptured) {
        mutableStateOf(
            value = if (isCaptured) true //when captured, capture the physical mouse pointer
            else requestPointerCapture
        )
    }

    val textInputActive = rememberTextInputActive()
    val capturePointer = isCaptured || (requestPointerCapture1 && !textInputActive)

    fun updatePointerPos(pos: Offset) {
        lastVirtualMousePos.value = pos
        onPointerMove(pos)
    }
    var pointerPosition by remember {
        mutableStateOf(centerPos)
    }
    LaunchedEffect(isCaptured) {
        val pos = lastVirtualMousePos.value?.takeIf {
            //With a physical mouse in use, fall back to the last virtual mouse position
            //Otherwise default the mouse to the screen center
            isPhysicalMouseMode
        } ?: centerPos
        if (!isCaptured) updatePointerPos(pos)
        pointerPosition = pos
    }

    gamepadViewModel?.let { viewModel ->
        val isGrabbing = cursorMode == CURSOR_DISABLED
        GamepadStickCameraListener(
            gamepadViewModel = viewModel,
            isGrabbing = isGrabbing,
            onOffsetEvent = { offset ->
                if (isGrabbing) {
                    updateMousePointer(false)
                    onCapturedMove(
                        Offset(
                            x = offset.x * gamepadCameraSpeedFactor,
                            y = offset.y * gamepadCameraSpeedFactor
                        )
                    )
                } else {
                    updateMousePointer(true)
                    val newOffset = Offset(
                        x = (pointerPosition.x + (offset.x * gamepadCursorSpeedFactor)).coerceIn(0f, screenWidth),
                        y = (pointerPosition.y + (offset.y * gamepadCursorSpeedFactor)).coerceIn(0f, screenHeight)
                    )
                    pointerPosition = newOffset
                    updatePointerPos(newOffset)
                }
            }
        )
    }

    Box(modifier = modifier) {
        val cursorShape by TLBridgeStates.cursorShape.collectAsStateWithLifecycle()

        if (showMousePointer) {
            MousePointer(
                modifier = Modifier.mouseFixedPosition(
                    mouseSize = mouseSize,
                    cursorShape = cursorShape,
                    pointerPosition = pointerPosition
                ),
                cursorShape = cursorShape,
                mouseSize = mouseSize,
                mouseFile = getMouseFile(cursorShape).ifExists(),
            )
        }

        TouchpadLayout(
            modifier = Modifier.fillMaxSize(),
            controlMode = if (cursorMode == CURSOR_ENABLED) {
                controlMode
            } else {
                //In capture mode, only slide control receives slide offsets
                MouseControlMode.SLIDE
            },
            enableMouseClick = enableMouseClick,
            longPressTimeoutMillis = longPressTimeoutMillis,
            requestPointerCapture = capturePointer,
            pointerIcon = cursorShape.composeIcon,
            onTouch = {
                onTouch()
                checkPhysicalMouseMode(false)
            },
            onMouse = {
                onMouse()
                checkPhysicalMouseMode(true)
            },
            onTap = { fingerPos ->
                when (cursorMode) {
                    CURSOR_DISABLED -> {
                        onCapturedTap(fingerPos)
                    }
                    CURSOR_ENABLED -> {
                        onTap(
                            if (controlMode == MouseControlMode.CLICK) {
                                updateMousePointer(!isCaptured && !hideMouseInClickMode)
                                //The current finger's absolute coordinates
                                pointerPosition = fingerPos
                                fingerPos
                            } else {
                                pointerPosition
                            }
                        )
                    }
                }
            },
            onLongPress = {
                when (cursorMode) {
                    CURSOR_DISABLED -> {
                        onCapturedLongPress()
                    }
                    CURSOR_ENABLED -> {
                        onLongPress()
                    }
                }
            },
            onLongPressEnd = {
                when (cursorMode) {
                    CURSOR_DISABLED -> {
                        onCapturedLongPressEnd()
                    }
                    CURSOR_ENABLED -> {
                        onLongPressEnd()
                    }
                }
            },
            onPointerMove = { offset, isMoveOnly ->
                when (cursorMode) {
                    CURSOR_DISABLED -> {
                        updateMousePointer(false)
                        onCapturedMove(offset)
                    }
                    CURSOR_ENABLED -> {
                        pointerPosition = if (isMoveOnly || controlMode == MouseControlMode.SLIDE) {
                            updateMousePointer(true)
                            Offset(
                                x = (pointerPosition.x + offset.x * speedFactor).coerceIn(0f, screenWidth),
                                y = (pointerPosition.y + offset.y * speedFactor).coerceIn(0f, screenHeight)
                            )
                        } else {
                            updateMousePointer(!hideMouseInClickMode)
                            //The current finger's absolute coordinates
                            offset
                        }
                        updatePointerPos(pointerPosition)
                    }
                }
            },
            onMouseMove = { offset ->
                when (cursorMode) {
                    CURSOR_DISABLED -> {
                        updateMousePointer(false)
                        onCapturedMove(offset)
                    }
                    CURSOR_ENABLED -> {
                        if (capturePointer) {
                            updateMousePointer(true)
                            pointerPosition = Offset(
                                x = (pointerPosition.x + offset.x * speedFactor).coerceIn(0f, screenWidth),
                                y = (pointerPosition.y + offset.y * speedFactor).coerceIn(0f, screenHeight)
                            )
                            updatePointerPos(pointerPosition)
                        } else {
                            //Not pointer-capture mode
                            updateMousePointer(false)
                            pointerPosition = offset
                            updatePointerPos(pointerPosition)
                        }
                    }
                }
            },
            onMouseScroll = onMouseScroll,
            onMouseButton = onMouseButton,
            isMoveOnlyPointer = isMoveOnlyPointer,
            onOccupiedPointer = onOccupiedPointer,
            onReleasePointer = onReleasePointer,
            enableScrollGesture = enableScrollGesture && cursorMode == CURSOR_ENABLED,
            onScrollGesture = onScrollGesture,
            requestFocusKey = cursorMode
        )
    }
}