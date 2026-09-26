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

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.endiq.turtlelauncher.game.sdl.SdlBridge
import com.endiq.turtlelauncher.setting.enums.MouseControlMode
import com.endiq.turtlelauncher.ui.components.FocusableBox
import com.endiq.turtlelauncher.ui.control.input.TouchCharInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.libsdl.app.SDLActivity
import kotlin.time.Duration.Companion.milliseconds

/**
 * Data class holding the drag state
 */
private data class DragState(
    var isDragging: Boolean = false,
    var longPressTriggered: Boolean = false,
    val startPosition: Offset
)

/**
 * @return whether text input is currently active
 */
@Composable
fun rememberTextInputActive(): Boolean {
    // Opening and closing the input session both trigger composeFocus changes, which refresh the state here
    val composeFocusCount by SdlBridge.composeFocus.collectAsStateWithLifecycle()
    return remember(composeFocusCount) {
        TouchCharInput.isActive() || SDLActivity.isUsingSDLTextEdit()
    }
}

/**
 * Maximum interval (ms) for considering two fingers pressed simultaneously
 */
private const val SCROLL_GESTURE_DOWN_WINDOW_MILLIS = 200L
/**
 * When scrolling with a two-finger slide, moving this distance equals one wheel step
 */
private val SCROLL_GESTURE_SCROLL_DISTANCE = 6.dp

/**
 * Raw touch control emulation layer
 * @param controlMode               control mode: SLIDE (slide control), CLICK (tap control)
 * @param enableMouseClick          whether virtual mouse click actions are enabled (slide control only)
 * @param longPressTimeoutMillis    long-press trigger detection timeout
 * @param requestPointerCapture     whether to use pointer capture
 * @param pointerIcon               physical pointer icon
 * @param onTouch                   touch on the mouse layer
 * @param onMouse                   physical mouse interaction event
 * @param onTap                     tap callback; the argument is the absolute position of the touch point inside the widget
 * @param onLongPress               long-press start callback
 * @param onLongPressEnd            long-press end callback
 * @param onPointerMove             pointer move callback; the argument is the pointer position in SLIDE mode and the current finger position in CLICK mode
 * @param onMouseMove               physical mouse pointer move callback
 * @param onMouseScroll             physical mouse wheel scroll
 * @param onMouseButton             physical mouse button press feedback
 * @param isMoveOnlyPointer         whether the pointer has been marked as move-only by the parent
 * @param onOccupiedPointer         pointer occupation callback
 * @param onReleasePointer          pointer release callback
 * @param enableScrollGesture       whether the two-finger scroll gesture is enabled
 * @param onScrollGesture           two-finger scroll callback; the argument is the wheel scroll offset
 * @param inputChange               restarts the inner pointerInput block so touch logic always sees the latest external params
 */
@Composable
fun TouchpadLayout(
    modifier: Modifier = Modifier,
    controlMode: MouseControlMode = MouseControlMode.SLIDE,
    enableMouseClick: Boolean = true,
    longPressTimeoutMillis: Long = -1L,
    requestPointerCapture: Boolean = true,
    pointerIcon: PointerIcon = PointerIcon.Default,
    onTouch: () -> Unit = {},
    onMouse: () -> Unit = {},
    onTap: (Offset) -> Unit = {},
    onLongPress: () -> Unit = {},
    onLongPressEnd: () -> Unit = {},
    onPointerMove: (Offset, isMoveOnly: Boolean) -> Unit = { _, _ -> },
    onMouseMove: (Offset) -> Unit = {},
    onMouseScroll: (Offset) -> Unit = {},
    onMouseButton: (button: Int, pressed: Boolean) -> Unit = { _, _ -> },
    isMoveOnlyPointer: (PointerId) -> Boolean = { false },
    onOccupiedPointer: (PointerId) -> Unit = {},
    onReleasePointer: (PointerId) -> Unit = {},
    enableScrollGesture: Boolean = false,
    onScrollGesture: (Offset) -> Unit = {},
    inputChange: Array<out Any> = arrayOf(Unit),
    requestFocusKey: Any? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    val composeFocusCount by SdlBridge.composeFocus.collectAsStateWithLifecycle()

    //Ensure pointerInput always calls the newest callbacks, avoiding stale closure captures
    val currentOnTouch by rememberUpdatedState(onTouch)
    val currentControlMode by rememberUpdatedState(controlMode)
    val currentEnableMouseClick by rememberUpdatedState(enableMouseClick)
    val currentLongPressTimeoutMillis by rememberUpdatedState(longPressTimeoutMillis)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnLongPressEnd by rememberUpdatedState(onLongPressEnd)
    val currentOnPointerMove by rememberUpdatedState(onPointerMove)
    val currentEnableScrollGesture by rememberUpdatedState(enableScrollGesture)
    val currentOnScrollGesture by rememberUpdatedState(onScrollGesture)
    val currentScrollGestureDistancePx by rememberUpdatedState(
        with(LocalDensity.current) { SCROLL_GESTURE_SCROLL_DISTANCE.toPx() }
    )

    FocusableBox(
        modifier = modifier
            .hoverable(interactionSource)
            .pointerHoverIcon(pointerIcon)
            .pointerInput(*inputChange) {
                coroutineScope {
                    /** All occupied pointers */
                    val occupiedPointers = mutableSetOf<PointerId>()

                    /** The pointer currently being handled */
                    var activePointer: PointerId? = null
                    val longPressJobs = mutableMapOf<PointerId, Job>()

                    /** Per-pointer drag state */
                    val dragStates = mutableMapOf<PointerId, DragState>()

                    /** The moveOnly pointer set, for move events */
                    val moveOnlyPointers = mutableSetOf<PointerId>()
                    /** Pointer occupied by the two-finger scroll gesture */
                    val scrollGesturePointers = mutableSetOf<PointerId>()
                    /** Whether the two-finger scroll gesture is active */
                    var scrollGestureActive = false
                    /** Leftover displacement under one scroll step in the gesture */
                    var scrollGestureRemainder = Offset.Zero
                    /** Press timestamps of active pointers, judging near-simultaneity */
                    var activePointerDownTime = 0L

                    /** Clears the mouse touch layer state */
                    fun resetTouchState() {
                        activePointer = null
                        dragStates.clear()
                        longPressJobs.values.forEach { it.cancel() }
                        longPressJobs.clear()
                        occupiedPointers.forEach { onReleasePointer(it) }
                        occupiedPointers.clear()
                        moveOnlyPointers.clear()
                        scrollGesturePointers.clear()
                        scrollGestureActive = false
                        scrollGestureRemainder = Offset.Zero
                    }

                    awaitPointerEventScope {
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes
                                    .filter { it.changedToDown() }
                                    .forEach { change ->
                                        if (change.type != PointerType.Touch) {
                                            return@forEach
                                        }

                                        //Fire the touch callback when the screen is first touched
                                        currentOnTouch()

                                        val pointerId = change.id
                                        //Whether the parent marked it as move-only
                                        val isMoveOnly = isMoveOnlyPointer(pointerId)

                                        //If it's a moveOnly pointer
                                        if (isMoveOnly) {
                                            //With no active pointer, become the active one
                                            //That way a first moveOnly finger lets a second finger take over
                                            if (activePointer == null) {
                                                activePointer = pointerId
                                                dragStates[pointerId] = DragState(startPosition = change.position)
                                            } else {
                                                //With an active pointer already, only movement is handled
                                                moveOnlyPointers.add(pointerId)
                                            }
                                        } else if (activePointer == null && !change.isConsumed) {
                                            //With no active pointer and this pointer unconsumed, start handling it
                                            //fix: only pointers that truly become activePointer get marked as occupied
                                            if (pointerId !in occupiedPointers) {
                                                onOccupiedPointer(pointerId)
                                                occupiedPointers.add(pointerId)
                                            }

                                            activePointer = pointerId
                                            activePointerDownTime = change.uptimeMillis

                                            dragStates[pointerId] =
                                                DragState(startPosition = change.position)

                                            if (currentControlMode == MouseControlMode.SLIDE && currentEnableMouseClick) {
                                                longPressJobs[pointerId] = launch {
                                                    //Long-press timing runs only in slide/click mode
                                                    val timeout =
                                                        if (currentLongPressTimeoutMillis > 0) {
                                                            currentLongPressTimeoutMillis
                                                        } else {
                                                            viewConfiguration.longPressTimeoutMillis
                                                        }
                                                    delay(timeout.milliseconds)

                                                    //Check we're still handling this pointer that hasn't started a drag
                                                    if (activePointer == pointerId && dragStates[pointerId]?.isDragging != true) {
                                                        dragStates[pointerId]?.longPressTriggered = true
                                                        currentOnLongPress()
                                                    }
                                                }
                                            }

                                            if (currentControlMode == MouseControlMode.CLICK) {
                                                //In click mode, a touch must update the pointer position regardless
                                                currentOnPointerMove(change.position, false)
                                            }
                                        } else if (currentEnableScrollGesture && scrollGesturePointers.isEmpty() && !change.isConsumed) {
                                            //Try pairing with the pressed pointer as a two-finger scroll gesture
                                            //Neither pointer may come from the control layout; both must press nearly together before the first drags the virtual mouse
                                            activePointer?.takeIf { it != pointerId }?.let { first ->
                                                if (!isMoveOnlyPointer(first)
                                                    && change.uptimeMillis - activePointerDownTime <= SCROLL_GESTURE_DOWN_WINDOW_MILLIS
                                                    && dragStates[first]?.let { !it.isDragging && !it.longPressTriggered } == true
                                                ) {
                                                    longPressJobs.remove(first)?.cancel()
                                                    //Mark the first pointer as dragging so the gesture's end isn't a stray click
                                                    dragStates[first]?.isDragging = true
                                                    //Register the second pointer as occupied too, so the layout can't grab it
                                                    if (pointerId !in occupiedPointers) {
                                                        onOccupiedPointer(pointerId)
                                                        occupiedPointers.add(pointerId)
                                                    }
                                                    scrollGesturePointers.add(first)
                                                    scrollGesturePointers.add(pointerId)
                                                    scrollGestureActive = true
                                                    scrollGestureRemainder = Offset.Zero
                                                }
                                            }
                                        }
                                    }

                                //Handle the gesture's movement: average both pointers' displacement,
                                //and fire one wheel scroll each time a threshold's worth accumulates
                                if (scrollGestureActive) {
                                    var deltaSum = Offset.Zero
                                    event.changes
                                        .filter { it.id in scrollGesturePointers && it.positionChanged() && !it.isConsumed }
                                        .forEach { change ->
                                            deltaSum += change.positionChange()
                                            change.consume()
                                        }

                                    if (deltaSum != Offset.Zero) {
                                        val scaled = deltaSum / scrollGesturePointers.size.coerceAtLeast(1).toFloat()
                                        val hScroll = scaled.x / currentScrollGestureDistancePx + scrollGestureRemainder.x
                                        val vScroll = scaled.y / currentScrollGestureDistancePx + scrollGestureRemainder.y
                                        val hRound = hScroll.toInt()
                                        val vRound = vScroll.toInt()

                                        if (hRound != 0 || vRound != 0) {
                                            currentOnScrollGesture(Offset(hScroll, vScroll))
                                        }
                                        scrollGestureRemainder = Offset(
                                            x = hScroll - hRound,
                                            y = vScroll - vRound
                                        )
                                    }
                                }

                                //Handle move events: process the active pointer's movement
                                activePointer?.takeIf { it !in scrollGesturePointers }?.let { pointerId ->
                                    event.changes
                                        .firstOrNull { it.id == pointerId && it.positionChanged() && !it.isConsumed }
                                        ?.let { moveChange ->
                                            val dragState = dragStates[pointerId] ?: return@let
                                            //Whether the parent marked it as move-only
                                            val isMoveOnly = isMoveOnlyPointer(pointerId)

                                            if (isMoveOnly) {
                                                dragState.isDragging = true
                                                val delta = moveChange.positionChange()
                                                currentOnPointerMove(delta, true)
                                            } else {
                                                when (currentControlMode) {
                                                    MouseControlMode.SLIDE -> {
                                                        if (currentEnableMouseClick) {
                                                            val distanceFromStart =
                                                                (moveChange.position - dragState.startPosition).getDistance()

                                                            if (distanceFromStart > viewConfiguration.touchSlop && !dragState.isDragging) {
                                                                //Past the slide threshold: it's a real slide
                                                                dragState.isDragging = true
                                                                longPressJobs.remove(pointerId)
                                                                    ?.cancel() //cancel long-press timing
                                                            }

                                                            if (dragState.isDragging || dragState.longPressTriggered) {
                                                                val delta = moveChange.positionChange()
                                                                currentOnPointerMove(delta, false)
                                                            }
                                                        } else {
                                                            dragState.isDragging = true
                                                            val delta = moveChange.positionChange()
                                                            currentOnPointerMove(delta, false)
                                                        }
                                                    }

                                                    MouseControlMode.CLICK -> {
                                                        if (!dragState.longPressTriggered) {
                                                            dragState.longPressTriggered = true
                                                            longPressJobs.remove(pointerId)?.cancel()
                                                            currentOnLongPress()
                                                        }
                                                        currentOnPointerMove(moveChange.position, false)
                                                    }
                                                }
                                            }

                                            moveChange.consume()
                                        }
                                }

                                //Handle moveOnly pointer movement
                                event.changes
                                    .filter { moveOnlyPointers.contains(it.id) && it.positionChanged() && !it.isConsumed }
                                    .forEach { moveChange ->
                                        val pointerId = moveChange.id
                                        val dragState = dragStates[pointerId]
                                        if (dragState != null) {
                                            dragState.isDragging = true
                                            val delta = moveChange.positionChange()
                                            currentOnPointerMove(delta, true)
                                            moveChange.consume()
                                        }
                                    }

                                //Release
                                event.changes
                                    .filter { it.changedToUpIgnoreConsumed() }
                                    .forEach { change ->
                                        val pointerId = change.id
                                        //Whether the parent marked it as move-only
                                        val isMoveOnly = isMoveOnlyPointer(pointerId)

                                        longPressJobs.remove(pointerId)?.cancel()
                                        val dragState = dragStates.remove(pointerId)

                                        if (pointerId in scrollGesturePointers) {
                                            //A scroll-gesture pointer lift ends the gesture outright, never firing a click
                                            scrollGesturePointers.remove(pointerId)
                                            scrollGestureActive = false
                                            if (pointerId == activePointer) {
                                                activePointer = null
                                            }
                                        } else if (pointerId == activePointer) {
                                            if (!isMoveOnly) {
                                                if (dragState?.longPressTriggered == true) {
                                                    currentOnLongPressEnd()
                                                } else {
                                                    when (currentControlMode) {
                                                        MouseControlMode.SLIDE -> {
                                                            if (currentEnableMouseClick && dragState?.isDragging != true) {
                                                                currentOnTap(change.position)
                                                            }
                                                        }

                                                        MouseControlMode.CLICK -> {
                                                            //No long-press entered: count as a click
                                                            currentOnTap(change.position)
                                                        }
                                                    }
                                                }
                                            }

                                            activePointer = null
                                        }

                                        //Drop it from the moveOnly set
                                        moveOnlyPointers.remove(pointerId)

                                        if (!isMoveOnly && pointerId in occupiedPointers) {
                                            occupiedPointers.remove(pointerId)
                                            onReleasePointer(pointerId)
                                        }
                                    }

                                if (!event.changes.any { it.pressed }) {
                                    resetTouchState()
                                }
                            }
                        } finally {
                            resetTouchState()
                        }
                    }
                }
            }
            .then(
                Modifier.mouseEventModifier(
                    disabled = requestPointerCapture,
                    inputChange = inputChange,
                    onMouse = onMouse,
                    onMouseMove = onMouseMove,
                    onMouseScroll = onMouseScroll,
                    onMouseButton = onMouseButton
                )
            ),
        requestKey = requestFocusKey to composeFocusCount,
        canRequestFocus = { !SDLActivity.isUsingSDLTextEdit() && !TouchCharInput.isActive() }
    )

    SimpleMouseCapture(
        enabled = requestPointerCapture,
        onMouse = onMouse,
        onMouseMove = onMouseMove,
        onMouseScroll = onMouseScroll,
        onMouseButton = onMouseButton
    )
}

/**
 * Simple physical mouse capture layer
 * @param enabled                   whether pointer capture is used
 * @param onMouse                   callback when the physical mouse starts responding
 * @param onMouseMove               physical mouse pointer move callback
 * @param onMouseScroll             physical mouse wheel scroll
 * @param onMouseButton             physical mouse button press feedback
 */
@Composable
private fun SimpleMouseCapture(
    enabled: Boolean,
    onMouse: () -> Unit,
    onMouseMove: (Offset) -> Unit,
    onMouseScroll: (Offset) -> Unit,
    onMouseButton: (button: Int, pressed: Boolean) -> Unit
) {
    val view by rememberUpdatedState(LocalView.current)
    val currentOnMouse by rememberUpdatedState(onMouse)
    val currentOnMouseMove by rememberUpdatedState(onMouseMove)
    val currentOnMouseScroll by rememberUpdatedState(onMouseScroll)
    val currentOnMouseButton by rememberUpdatedState(onMouseButton)

    fun syncCaptureState() {
        if (enabled) {
            if (view.hasWindowFocus()) {
                view.requestPointerCapture()
            }
        } else {
            view.releasePointerCapture()
        }
    }

    val composeFocus by SdlBridge.composeFocus.collectAsStateWithLifecycle()
    LaunchedEffect(composeFocus) {
        syncCaptureState()
    }

    DisposableEffect(view, enabled) {
        view.setOnCapturedPointerListener(null)

        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (enabled && hasFocus) {
                view.requestPointerCapture()
            }
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)

        syncCaptureState()

        if (enabled) {
            val pointerListener = View.OnCapturedPointerListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE -> {
                        var deltaX = 0f
                        var deltaY = 0f

                        val relX = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
                        val relY = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
                        deltaX += if (relX != 0f) relX else event.x
                        deltaY += if (relY != 0f) relY else event.y

                        val historySize = event.historySize
                        for (i in 0 until historySize) {
                            deltaX += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, i)
                            deltaY += event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, i)
                        }

                        currentOnMouse()
                        currentOnMouseMove(Offset(deltaX, deltaY))
                        true
                    }
                    MotionEvent.ACTION_SCROLL -> {
                        currentOnMouseScroll(
                            Offset(
                                event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                                event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                            )
                        )
                        true
                    }
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_BUTTON_PRESS -> {
                        currentOnMouseButton(event.actionButton, true)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_BUTTON_RELEASE -> {
                        currentOnMouseButton(event.actionButton, false)
                        true
                    }
                    else -> false
                }
            }

            view.setOnCapturedPointerListener(pointerListener)
        } else {
            view.setOnCapturedPointerListener(null)
        }

        onDispose {
            view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            view.setOnCapturedPointerListener(null)
        }
    }
}

/**
 * Physical mouse pointer event listener
 * @param disabled whether it's disabled
 */
private fun Modifier.mouseEventModifier(
    disabled: Boolean,
    inputChange: Array<out Any> = arrayOf(Unit),
    onMouse: () -> Unit = {},
    onMouseMove: (Offset) -> Unit = {},
    onMouseScroll: (Offset) -> Unit = {},
    onMouseButton: (Int, Boolean) -> Unit = { _, _ -> },
) = composed(
    inspectorInfo = {
        name = "mouseEventModifier"
        properties["keys"] = inputChange
    }
) {
    val currentDisabled by rememberUpdatedState(disabled)
    val currentOnMouse by rememberUpdatedState(onMouse)
    val currentOnMouseMove by rememberUpdatedState(onMouseMove)
    val currentOnMouseScroll by rememberUpdatedState(onMouseScroll)
    val currentOnMouseButton by rememberUpdatedState(onMouseButton)

    var lastButtons by remember(*inputChange) {
        //Mouse button states stored as a bitmask
        mutableIntStateOf(0)
    }

    pointerInteropFilter { event ->
        if (currentDisabled) {
            return@pointerInteropFilter false
        }

        val isMouse = event.isFromSource(InputDevice.SOURCE_MOUSE)
        val isStylus = event.isFromSource(InputDevice.SOURCE_STYLUS)
        //Filter out anything that isn't mouse or stylus
        //Stylus (Chromebook, Samsung, etc.)
        if (!isMouse && !isStylus) {
            return@pointerInteropFilter false
        }

        currentOnMouse()

        val buttons = event.buttonState
        val changed = lastButtons xor buttons

        fun dispatchButton(button: Int) {
            if (changed and button != 0) {
                val pressed = buttons and button != 0
                currentOnMouseButton(button, pressed)
            }
        }

        dispatchButton(MotionEvent.BUTTON_PRIMARY)
        dispatchButton(MotionEvent.BUTTON_SECONDARY)
        dispatchButton(MotionEvent.BUTTON_TERTIARY)
        dispatchButton(MotionEvent.BUTTON_BACK)
        dispatchButton(MotionEvent.BUTTON_FORWARD)
        dispatchButton(MotionEvent.BUTTON_STYLUS_SECONDARY)

        lastButtons = buttons

        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_MOVE -> {
                currentOnMouseMove(
                    Offset(x = event.x, y = event.y)
                )
            }

            //Check and handle stylus press
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (isStylus) {
                    currentOnMouseButton(MotionEvent.BUTTON_PRIMARY, true)
                }
            }
            //Check and handle stylus release
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                if (isStylus) {
                    currentOnMouseButton(MotionEvent.BUTTON_PRIMARY, false)
                }
            }

            MotionEvent.ACTION_SCROLL -> {
                currentOnMouseScroll(
                    Offset(
                        x = event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                        y = event.getAxisValue(MotionEvent.AXIS_VSCROLL),
                    )
                )
            }
        }

        true
    }
}