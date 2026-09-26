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

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.ui.components.MarqueeText
import com.endiq.turtlelauncher.ui.components.SimpleAlertDialog
import com.endiq.turtlelauncher.ui.components.fadeEdge
import com.endiq.turtlelauncher.ui.components.verticalScrollWithBar
import com.endiq.turtlelauncher.ui.theme.cardColor
import com.endiq.turtlelauncher.ui.theme.onCardColor
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.viewmodel.GamepadRemapperViewModel
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "GamepadRemapper"

/**
 * Builds all steps needing remapping
 */
fun buildRemapperSteps(
    a: Boolean = false,
    b: Boolean = false,
    x: Boolean = false,
    y: Boolean = false,
    start: Boolean = false,
    select: Boolean = false,
    leftJoystick: Boolean = false,
    leftJoystickButton: Boolean = false,
    rightJoystick: Boolean = false,
    rightJoystickButton: Boolean = false,
    leftShoulder: Boolean = false,
    rightShoulder: Boolean = false,
    leftTrigger: Boolean = false,
    rightTrigger: Boolean = false,
    dpad: Boolean = false
): Set<GamepadRemap> {
    return buildSet {
        fun addIf(enabled: Boolean, remap: GamepadRemap) {
            if (enabled) add(remap)
            else remove(remap)
        }
        addIf(a, GamepadRemap.ButtonA)
        addIf(b, GamepadRemap.ButtonB)
        addIf(x, GamepadRemap.ButtonX)
        addIf(y, GamepadRemap.ButtonY)
        addIf(start, GamepadRemap.ButtonStart)
        addIf(select, GamepadRemap.ButtonSelect)
        addIf(leftJoystick, GamepadRemap.MotionX)
        addIf(leftJoystick, GamepadRemap.MotionY)
        addIf(leftJoystickButton, GamepadRemap.ButtonLeftStick)
        addIf(rightJoystick, GamepadRemap.MotionZ)
        addIf(rightJoystick, GamepadRemap.MotionRZ)
        addIf(rightJoystickButton, GamepadRemap.ButtonRightStick)
        addIf(leftShoulder, GamepadRemap.ButtonL1)
        addIf(rightShoulder, GamepadRemap.ButtonR1)
        addIf(leftTrigger, GamepadRemap.MotionLeftTrigger)
        addIf(rightTrigger, GamepadRemap.MotionRightTrigger)
        addIf(dpad, GamepadRemap.MotionHatX)
        addIf(dpad, GamepadRemap.MotionHatY)
    }
}

sealed interface GamepadRemapOperation {
    data object None: GamepadRemapOperation
    data class Tip(val deviceName: String): GamepadRemapOperation
    data class Remapping(val deviceName: String): GamepadRemapOperation
    data object Finished: GamepadRemapOperation
}

/**
 * Remaps every gamepad button and stick event; the visual operation flow
 * @param remapperViewModel bound from the View, which binds the MotionEvent/KeyEvent listeners
 *                          and sends the events here; the flow receives and processes them, completing the binding
 * @param steps             all binding steps; customize which ones to include
 */
@Composable
fun GamepadRemapperDialog(
    operation: GamepadRemapOperation,
    changeOperation: (GamepadRemapOperation) -> Unit,
    remapperViewModel: GamepadRemapperViewModel,
    steps: Set<GamepadRemap>
) {
    when (operation) {
        is GamepadRemapOperation.None -> {}
        is GamepadRemapOperation.Tip -> {
            SimpleAlertDialog(
                title = stringResource(R.string.settings_gamepad_remapping_tip_title),
                text = stringResource(R.string.settings_gamepad_remapping_tip_summary)
            ) {
                changeOperation(GamepadRemapOperation.Remapping(operation.deviceName))
            }
        }
        is GamepadRemapOperation.Remapping -> {
            val deviceName = operation.deviceName

            val motionMapping = remember(steps) { mutableMapOf<Int, Int>() }
            val keyMapping = remember(steps) { mutableMapOf<Int, Int>() }

            var progress by remember(steps) { mutableIntStateOf(0) }
            var isListening by remember { mutableStateOf(false) }

            fun nextOrFinish() {
                if (progress < steps.size - 1) {
                    progress++
                } else {
                    val motionLog = motionMapping.entries.joinToString("\n") { entry ->
                        "Key: ${MotionEvent.axisToString(entry.key)} (${entry.key}), Mapping to: ${MotionEvent.axisToString(entry.value)} (${entry.value})"
                    }
                    val keyLog = keyMapping.entries.joinToString("\n") { entry ->
                        "Key: ${KeyEvent.keyCodeToString(entry.key)} (${entry.key}), Mapping to: ${KeyEvent.keyCodeToString(entry.value)} (${entry.value})"
                    }
                    Logger.info(TAG, "=============================")
                    Logger.info(TAG, "Gamepad Motion Remapping:")
                    Logger.info(TAG, motionLog)
                    Logger.info(TAG, "=============================")
                    Logger.info(TAG, "Gamepad Key Remapping:")
                    Logger.info(TAG, keyLog)

                    remapperViewModel.applyMapping(deviceName, motionMapping, keyMapping)
                    remapperViewModel.save()

                    changeOperation(GamepadRemapOperation.Finished)
                }
            }

            fun backToPrevious() {
                if (progress > 0) {
                    progress--
                } else {
                    //If already at the first step, show the hint screen
                    changeOperation(GamepadRemapOperation.Tip(deviceName))
                }
            }

            //After progress changes, wait a fixed grace period before listening for events again
            LaunchedEffect(progress) {
                isListening = false
                delay(700L.milliseconds)
                isListening = true
            }

            val isListening1 by rememberUpdatedState(isListening)
            LaunchedEffect(remapperViewModel) {
                remapperViewModel.events.collect { event ->
                    when (event) {
                        is GamepadRemapperViewModel.Event.Button -> {
                            val keyEvent = event.event
                            val keyCode = event.code
                            val currentRemap = steps.elementAtOrNull(progress) ?: return@collect

                            if (
                                isListening1 &&
                                currentRemap.isMotion.not() &&
                                keyEvent.repeatCount <= 0 &&
                                keyCode != KeyEvent.KEYCODE_UNKNOWN &&
                                (keyEvent.device.isGamepadDevice() || keyEvent.isGamepadKeyEvent())
                            ) {
                                isListening = false
                                keyMapping[keyCode] = currentRemap.code
                                nextOrFinish()
                            }
                        }
                        is GamepadRemapperViewModel.Event.Axis -> {
                            val motionEvent = event.event
                            val currentRemap = steps.elementAtOrNull(progress) ?: return@collect

                            if (
                                isListening1 &&
                                currentRemap.isMotion &&
                                (motionEvent.device.isGamepadDevice() || motionEvent.isJoystickMoving())
                            ) {
                                val axis = motionEvent.findTriggeredAxis() ?: return@collect
                                isListening = false
                                motionMapping[axis] = currentRemap.code
                                nextOrFinish()
                            }
                        }
                    }
                }
            }

            //----------------------
            //UI interaction page: no Dialog here, since this should never take focus
            //----------------------

            AnimatedVisibility(
                modifier = Modifier.fillMaxSize(),
                visible = true
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent()
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .fillMaxHeight(0.85f)
                            .padding(all = 6.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = cardColor(false),
                        contentColor = onCardColor(),
                        shadowElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_gamepad_remapping_tip_title),
                                style = MaterialTheme.typography.titleMedium
                            )

                            steps.elementAtOrNull(progress)?.let { currentRemap ->
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Image(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f),
                                        painter = painterResource(id = currentRemap.getIconRes()),
                                        contentDescription = currentRemap.getText(),
                                        contentScale = ContentScale.Fit
                                    )

                                    Text(
                                        text = currentRemap.getText(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            val progressValue by animateFloatAsState(
                                targetValue = progress.toFloat() / steps.size.toFloat()
                            )

                            //Current progress display
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LinearWavyProgressIndicator(
                                    modifier = Modifier.weight(1f),
                                    progress = { progressValue }
                                )

                                Text(
                                    text = "${progress + 1} / ${steps.size}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                FilledTonalButton(
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusProperties { canFocus = false },
                                    onClick = {
                                        backToPrevious()
                                    }
                                ) {
                                    MarqueeText(text = stringResource(R.string.settings_gamepad_remapping_previous))
                                }
                                Button(
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusProperties { canFocus = false },
                                    onClick = {
                                        nextOrFinish()
                                    }
                                ) {
                                    MarqueeText(text = stringResource(R.string.settings_gamepad_remapping_skip))
                                }
                            }
                        }
                    }
                }
            }
        }
        is GamepadRemapOperation.Finished -> {
            val isSaving = remapperViewModel.isSavingMapping
            val text = if (isSaving) {
                stringResource(R.string.settings_gamepad_remapping_finished_saving)
            } else {
                stringResource(R.string.settings_gamepad_remapping_finished_saved)
            }

            AlertDialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                ),
                title = {
                    Text(
                        text = stringResource(R.string.settings_gamepad_remapping_finished_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fadeEdge(state = scrollState)
                            .verticalScrollWithBar(state = scrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = text)
                        if (isSaving) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            changeOperation(GamepadRemapOperation.None)
                        },
                        enabled = isSaving.not()
                    ) {
                        MarqueeText(text = stringResource(R.string.generic_close))
                    }
                }
            )
        }
    }
}