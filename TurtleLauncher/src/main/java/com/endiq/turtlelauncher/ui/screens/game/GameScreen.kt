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

package com.endiq.turtlelauncher.ui.screens.game

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.endiq.inputmap.keycodes.ControlEventKeycode
import com.endiq.inputmap.keycodes.LwjglGlfwKeycode
import com.endiq.inputmap.keycodes.OPEN_CHAT
import com.endiq.inputmap.keycodes.OPEN_CHAT_VALUE
import com.endiq.layer_controller.ControlBoxLayout
import com.endiq.layer_controller.data.HideLayerWhen
import com.endiq.layer_controller.event.ClickEvent
import com.endiq.layer_controller.event.EventHandler
import com.endiq.layer_controller.layout.ControlLayout
import com.endiq.layer_controller.layout.EmptyControlLayout
import com.endiq.layer_controller.layout.loadLayoutFromFile
import com.endiq.layer_controller.observable.ObservableControlLayout
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.bridge.CURSOR_DISABLED
import com.endiq.turtlelauncher.bridge.TLBridgeStates
import com.endiq.turtlelauncher.bridge.TLNativeInvoker
import com.endiq.turtlelauncher.feature.recorder.ScreenRecorder
import com.endiq.turtlelauncher.game.input.LWJGLCharSender
import com.endiq.turtlelauncher.game.keycodes.mapToKeycode
import com.endiq.turtlelauncher.game.launch.handler.GameHandler
import com.endiq.turtlelauncher.game.sdl.SdlBridge
import com.endiq.turtlelauncher.game.sdl.SdlTextSender
import com.endiq.turtlelauncher.game.support.touch_controller.touchControllerInputModifier
import com.endiq.turtlelauncher.game.support.touch_controller.touchControllerTouchModifier
import com.endiq.turtlelauncher.game.version.installed.Version
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.setting.enums.isLauncherInDarkTheme
import com.endiq.turtlelauncher.setting.enums.toAction
import com.endiq.turtlelauncher.terracotta.Terracotta
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.ui.components.BackgroundCard
import com.endiq.turtlelauncher.ui.components.MenuState
import com.endiq.turtlelauncher.ui.components.rememberBoxSize
import com.endiq.turtlelauncher.ui.control.MinecraftHotbar
import com.endiq.turtlelauncher.ui.control.event.launcherEvent
import com.endiq.turtlelauncher.ui.control.event.lwjglEvent
import com.endiq.turtlelauncher.ui.control.gamepad.GamepadKeyListener
import com.endiq.turtlelauncher.ui.control.gamepad.GamepadOnActionListener
import com.endiq.turtlelauncher.ui.control.gamepad.GamepadStickMovementListener
import com.endiq.turtlelauncher.ui.control.gamepad.SimpleGamepadCapture
import com.endiq.turtlelauncher.ui.control.gyroscope.GyroscopeReader
import com.endiq.turtlelauncher.ui.control.gyroscope.isGyroscopeAvailable
import com.endiq.turtlelauncher.ui.control.hotbarPercentage
import com.endiq.turtlelauncher.ui.control.input.TextInputMode
import com.endiq.turtlelauncher.ui.control.mouse.SwitchableMouseLayout
import com.endiq.turtlelauncher.ui.screens.game.elements.DraggableGameBall
import com.endiq.turtlelauncher.ui.screens.game.elements.ForceCloseOperation
import com.endiq.turtlelauncher.ui.screens.game.elements.GameMenuSubscreen
import com.endiq.turtlelauncher.ui.screens.game.elements.GamepadModePromptDialog
import com.endiq.turtlelauncher.ui.screens.game.elements.LogBox
import com.endiq.turtlelauncher.ui.screens.game.elements.LogState
import com.endiq.turtlelauncher.ui.screens.game.elements.ReplacementControlOperation
import com.endiq.turtlelauncher.ui.screens.game.elements.ReplacementControlState
import com.endiq.turtlelauncher.ui.screens.game.elements.SendKeycodeOperation
import com.endiq.turtlelauncher.ui.screens.game.elements.SendKeycodeState
import com.endiq.turtlelauncher.ui.screens.game.multiplayer.TerracottaOperation
import com.endiq.turtlelauncher.ui.screens.game.multiplayer.rememberTerracottaViewModel
import com.endiq.turtlelauncher.ui.screens.main.control_editor.ControlEditor
import com.endiq.turtlelauncher.utils.currentGameDisplayLayout
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.viewmodel.EditorViewModel
import com.endiq.turtlelauncher.viewmodel.EventViewModel
import com.endiq.turtlelauncher.viewmodel.GamepadViewModel
import com.endiq.turtlelauncher.viewmodel.sendToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.lwjgl.glfw.CallbackBridge
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "GameScreen"

private class GameViewModel(
    private val version: Version,
    private val onChangeTextInputMode: (TextInputMode?) -> Unit
) : ViewModel() {
    /** Game menu operation state */
    var gameMenuState by mutableStateOf(MenuState.NONE)
    /** Selected tab index in the game menu control settings area */
    var controlMenuTabIndex by mutableIntStateOf(0)
    /** Force-close dialog operation state */
    var forceCloseState by mutableStateOf<ForceCloseOperation>(ForceCloseOperation.None)
    /** Send-keycode operation state */
    var sendKeycodeState by mutableStateOf<SendKeycodeState>(SendKeycodeState.None)
    /** Switch control layout operation state */
    var replacementControlState by mutableStateOf<ReplacementControlState>(ReplacementControlState.None)
    /** Pointers marked as move-only by the control layout layer */
    var moveOnlyPointers = mutableSetOf<PointerId>()
    /** Pointers occupied by the mouse touch handler layer */
    var occupiedPointers = mutableSetOf<PointerId>()

    /** In-game frame rate state */
    var gameFps by mutableIntStateOf(0)
        private set
    private var fpsJob: Job? = null
    /** Starts frame rate capture */
    fun startFpsCapture() {
        //Starts a new coroutine that updates the frame rate data once per second
        fpsJob = viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                runCatching {
                    ensureActive()
                }.onFailure {
                    break
                }
                gameFps = CallbackBridge.getCurrentFps()
                delay(1000L.milliseconds)
            }
        }
    }
    /** Stops frame rate capture */
    fun stopFpsCapture() {
        fpsJob?.cancel()
        fpsJob = null
    }

    var editorRefresh by mutableIntStateOf(0)
        private set
    /** Observable control layout */
    var observableLayout by mutableStateOf<ObservableControlLayout?>(null)
        private set
    /** Current control layout file */
    var currentControlFile by mutableStateOf<File?>(null)
        private set
    /** Control layout: widget layer visibility state */
    var controlLayerHideState by mutableStateOf(HideLayerWhen.None)
        private set

    /** Whether the layout is currently being edited */
    var isEditingLayout by mutableStateOf(false)
        private set

    fun switchControlLayer(hideWhen: HideLayerWhen) {
        if (controlLayerHideState != hideWhen) controlLayerHideState = hideWhen
    }

    /** Virtual mouse scroll event handler */
    val mouseScrollUpEvent = MouseScrollEvent(viewModelScope, 1.0)
    val mouseScrollDownEvent = MouseScrollEvent(viewModelScope, -1.0)

    /** In-game message sender */
    val gameTextSender = GameTextSender(viewModelScope)

    /** Control layout widget click event handler */
    val eventHandler = EventHandler { event, pressed ->
        onKeyEvent(event, pressed)
    }

    /** Handles control layout click events */
    fun onKeyEvent(event: ClickEvent, pressed: Boolean) {
        val key = event.key
        when (event.type) {
            ClickEvent.Type.Key -> {
                lwjglEvent(
                    eventKey = key,
                    isMouse = key.startsWith("GLFW_MOUSE_", false),
                    isPressed = pressed
                )
            }
            ClickEvent.Type.LauncherEvent -> {
                launcherEvent(
                    eventKey = key,
                    isPressed = pressed,
                    onSwitchIME = { onChangeTextInputMode(null) },
                    onSwitchMenu = { switchMenu() },
                    onSingleScrollUp = { mouseScrollUpEvent.scrollSingle() },
                    onSingleScrollDown = { mouseScrollDownEvent.scrollSingle() },
                    onLongScrollUp = { mouseScrollUpEvent.scrollLongPress() },
                    onLongScrollUpCancel = { mouseScrollUpEvent.cancel() },
                    onLongScrollDown = { mouseScrollDownEvent.scrollLongPress() },
                    onLongScrollDownCancel = { mouseScrollDownEvent.cancel() }
                )
            }
            ClickEvent.Type.SendText -> {
                //In-game text sending event
                if (pressed) {
                    val text = event.key
                    val inGame = TLBridgeStates.cursorMode.value == CURSOR_DISABLED
                    gameTextSender.send(GameTextSender.Data(text, inGame))
                }
                return
            }
            else -> return
        }
    }

    fun replaceControlLayout(layoutFile: File) {
        viewModelScope.launch(Dispatchers.Main) {
            loadControlLayout(layoutFile)
        }
    }

    private val layoutMutex = Mutex()
    suspend fun loadControlLayout(layoutFile: File? = version.getControlPath()) {
        layoutMutex.withLock {
            withContext(Dispatchers.Main) {
                observableLayout = null
                val layout = withContext(Dispatchers.IO) {
                    delay(10L.milliseconds) //deliberately wait a moment before loading
                    currentControlFile = layoutFile
                    getLayout(layoutFile)
                }
                //Load the control layout into a form Compose can consume
                observableLayout = ObservableControlLayout(layout)
            }
        }
    }

    private fun getLayout(layoutFile: File? = currentControlFile): ControlLayout {
        return layoutFile?.let {
            try {
                loadLayoutFromFile(it)
            } catch (e: Exception) {
                Logger.warning(TAG, "Failed to load control layout: $it", e)
                null
            }
        } ?: EmptyControlLayout
    }

    /**
     * Enters control layout editing mode
     */
    fun startControlEditor(editorVM: EditorViewModel) {
        if (!isEditingLayout) {
            clearState()
            editorVM.initLayout(getLayout())
            isEditingLayout = true
        }
    }

    /**
     * Exits control layout editing mode (if actually editing right now)
     */
    fun exitControlEditor() {
        viewModelScope.launch(Dispatchers.Main) {
            if (isEditingLayout) {
                isEditingLayout = false
                loadControlLayout(currentControlFile)
                editorRefresh++
            }
        }
    }

    /**
     * Toggles the game menu
     */
    fun switchMenu() {
        this.gameMenuState = this.gameMenuState.next()
    }

    /**
     * Clears all game state
     */
    fun clearState() {
        mouseScrollUpEvent.cancel()
        mouseScrollDownEvent.cancel()
        gameTextSender.cancel()
        onChangeTextInputMode(TextInputMode.DISABLE)
        moveOnlyPointers.clear()
        occupiedPointers.clear()
    }

    init {
        viewModelScope.launch(Dispatchers.Main) {
            loadControlLayout()
        }
    }

    override fun onCleared() {
        clearState()
    }
}

/**
 * Mouse wheel event management
 * @param offset the wheel scroll distance
 */
private class MouseScrollEvent(
    private val scope: CoroutineScope,
    private val offset: Double
) {
    private var mouseScrollJob: Job? = null

    /**
     * Cancels scroll events and resets state
     */
    fun cancel() {
        mouseScrollJob?.cancel()
        mouseScrollJob = null
    }

    /**
     * A tap fires one wheel scroll event
     */
    fun scrollSingle() {
        CallbackBridge.sendScroll(0.0, offset)
    }

    /**
     * Long-press keeps firing wheel scroll events
     */
    fun scrollLongPress() {
        mouseScrollJob?.cancel()
        mouseScrollJob = scope.launch {
            while (true) {
                try {
                    ensureActive()
                    CallbackBridge.sendScroll(0.0, offset)
                    delay(50L.milliseconds)
                } catch (_: Exception) {
                    break
                }
            }
            mouseScrollJob = null
        }
    }
}

/**
 * In-game message sender
 */
private class GameTextSender(private val scope: CoroutineScope) {
    /**
     * @param text the text to send
     * @param inGame whether we're in-game; if so, the game chat bar gets opened first
     */
    data class Data(
        val text: String,
        val inGame: Boolean
    )

    private var messageChannel: Channel<Data>? = null
    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        messageChannel?.close()
        messageChannel = null
        job = null
    }

    /**
     * Tries sending text into the game (queued)
     */
    fun send(data: Data) {
        if (job?.isActive != true || messageChannel == null) {
            job?.cancel()
            messageChannel?.close()

            messageChannel = Channel(Channel.UNLIMITED)
            job = scope.launch {
                messageChannel?.let { channel ->
                    for ((text, inGame) in channel) {
                        sendMessage(text, inGame)
                    }
                }
            }
        }

        messageChannel?.trySend(data)
    }

    private suspend fun sendMessage(text: String, inGame: Boolean) {
        withContext(Dispatchers.Main) {
            fun sendText() {
                for (ch in text) {
                    if (SdlBridge.sdlEnabled) {
                        SdlTextSender.sendChar(ch)
                    } else {
                        LWJGLCharSender.sendChar(ch)
                    }
                }
            }

            if (inGame) {
                //Find the chat-bar key from options.txt
                //Not found: ignore this event
                mapToKeycode(OPEN_CHAT, OPEN_CHAT_VALUE)?.let { openChat ->
                    if (SdlBridge.sdlEnabled) {
                        SdlTextSender.sendKey(openChat)
                        delay(50L.milliseconds)
                        sendText()
                        delay(50L.milliseconds)
                        SdlTextSender.sendEnter()
                    } else {
                        CallbackBridge.sendKeyPress(openChat)
                        delay(50L.milliseconds)
                        sendText()
                        delay(50L.milliseconds)
                        LWJGLCharSender.sendEnter()
                    }
                }
            } else {
                //Outside the game: send the text directly
                sendText()
            }
        }
    }
}

@Composable
private fun rememberGameViewModel(
    version: Version,
    onChangeTextInputMode: (TextInputMode?) -> Unit
) = viewModel(
    key = version.toString()
) {
    GameViewModel(version, onChangeTextInputMode)
}

@Composable
private fun rememberEditorViewModel(
    key: String
)= viewModel(
    key = key
) {
    EditorViewModel()
}

@Composable
fun GameScreen(
    version: Version,
    gameHandler: GameHandler,
    showGameInfo: Boolean,
    onInfoBoxClose: () -> Unit,
    logState: LogState,
    onLogStateChange: (LogState) -> Unit,
    textInputMode: TextInputMode,
    isTouchProxyEnabled: Boolean,
    onInputAreaRectUpdated: (IntRect?) -> Unit,
    getAccountName: () -> String?,
    eventViewModel: EventViewModel,
    gamepadViewModel: GamepadViewModel,
) {
    val context = LocalContext.current
    val viewModel = rememberGameViewModel(version) { mode ->
        eventViewModel.sendEvent(EventViewModel.Event.Game.SwitchIme(mode))
    }
    val editorViewModel = rememberEditorViewModel("ControlEditor_Times=${viewModel.editorRefresh}")
    val cursorMode by TLBridgeStates.cursorMode.collectAsStateWithLifecycle()
    val isGrabbing = remember(cursorMode) {
        cursorMode == CURSOR_DISABLED
    }
    val terracottaViewModel = rememberTerracottaViewModel(
        keyTag = gameHandler.toString() + "_Terracotta",
        gameHandler = gameHandler,
        eventViewModel = eventViewModel,
        getUserName = getAccountName
    )

    //Built-in screen recorder state
    val recorderEnabled = AllSettings.screenRecorder.state
    val recorderHideControls = AllSettings.recorderHideControls.state
    val isRecording by ScreenRecorder.isRecording.collectAsStateWithLifecycle()
    val captureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val started = ScreenRecorder.start(context, result.resultCode, result.data!!)
            if (!started) {
                eventViewModel.sendToast(
                    androidText(R.string.recorder_failed),
                    Toast.LENGTH_SHORT
                )
            }
        }
    }

    LaunchedEffect(viewModel.isEditingLayout, viewModel.gameMenuState) {
        //Sync state to VMActivity; editing controls or opening the game menu stops key handling
        val allowKeyHandle = !viewModel.isEditingLayout && viewModel.gameMenuState != MenuState.SHOW
        eventViewModel.sendEvent(EventViewModel.Event.Game.KeyHandle(allowKeyHandle))
    }

    SendKeycodeOperation(
        operation = viewModel.sendKeycodeState,
        onChange = { viewModel.sendKeycodeState = it },
        lifecycleScope = viewModel.viewModelScope
    )

    ForceCloseOperation(
        operation = viewModel.forceCloseState,
        onChange = { viewModel.forceCloseState = it },
        onForceClose = {
            Terracotta.setWaiting(false)
            TLNativeInvoker.jvmExit(0, false)
        },
        text = stringResource(R.string.game_menu_option_force_close_text)
    )

    ReplacementControlOperation(
        operation = viewModel.replacementControlState,
        onChange = { viewModel.replacementControlState = it },
        currentLayout = viewModel.currentControlFile,
        replacementControl = { viewModel.replaceControlLayout(it) }
    )

    TerracottaOperation(
        viewModel = terracottaViewModel,
        onShowToast = { text, duration ->
            eventViewModel.sendToast(text, duration)
        }
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val screenSize = rememberBoxSize()

        if (!viewModel.isEditingLayout) {
            if (AllSettings.gamepadControl.state) {
                GamepadOnActionListener(
                    gamepadViewModel = gamepadViewModel,
                    onAction = {
                        viewModel.switchControlLayer(HideLayerWhen.WhenGamepad)
                    }
                )
            }

            if (AllSettings.gamepadControl.state && gamepadViewModel.gamepadEngaged) {
                //Gamepad event listener
                GamepadKeyListener(
                    gamepadViewModel = gamepadViewModel,
                    isGrabbing = isGrabbing,
                    onKeyEvent = { events, pressed ->
                        events.forEach { event ->
                            viewModel.onKeyEvent(event, pressed)
                        }
                    }
                )

                //Gamepad stick movement listener
                GamepadStickMovementListener(
                    gamepadViewModel = gamepadViewModel,
                    isGrabbing = isGrabbing,
                    onKeyEvent = { event, pressed ->
                        viewModel.onKeyEvent(event, pressed)
                    }
                )
            }

            //Control layout layer. While the screen recorder is capturing (and
            //the "hide controls" option is on) only the button visuals are
            //skipped - the virtual-mouse input layer below stays fully
            //functional, so gameplay continues uninterrupted.
            val hideControlsForRecording = isRecording && recorderHideControls
            if (!hideControlsForRecording) {
                ControlBoxLayout(
                    modifier = Modifier.fillMaxSize(),
                    observedLayout = viewModel.observableLayout,
                    eventHandler = viewModel.eventHandler,
                    checkOccupiedPointers = { viewModel.occupiedPointers.contains(it) },
                    opacity = (AllSettings.controlsOpacity.state.toFloat() / 100f).coerceIn(0f, 1f),
                    markPointerAsMoveOnly = { viewModel.moveOnlyPointers.add(it) },
                    onOccupiedPointer = { viewModel.occupiedPointers.add(it) },
                    onReleasePointer = { viewModel.occupiedPointers.remove(it) },
                    isCursorGrabbing = isGrabbing,
                    hideLayerWhen = viewModel.controlLayerHideState,
                    isDark = isLauncherInDarkTheme()
                ) {
                    //Virtual mouse control layer
                    MouseControlLayout(
                        isTouchProxyEnabled = isTouchProxyEnabled,
                        modifier = Modifier.fillMaxSize(),
                        cursorMode = cursorMode,
                        screenSize = screenSize,
                        onInputAreaRectUpdated = onInputAreaRectUpdated,
                        textInputMode = textInputMode,
                        isMoveOnlyPointer = { viewModel.moveOnlyPointers.contains(it) },
                        onOccupiedPointer = { viewModel.occupiedPointers.add(it) },
                        onReleasePointer = {
                            viewModel.occupiedPointers.remove(it)
                            viewModel.moveOnlyPointers.remove(it)
                        },
                        onMouseMoved = { viewModel.switchControlLayer(HideLayerWhen.WhenMouse) },
                        onTouch = { viewModel.switchControlLayer(HideLayerWhen.None) },
                        gamepadViewModel = gamepadViewModel.takeIf { AllSettings.gamepadControl.state }
                    )
                }
            } else {
                //Recording with hidden controls: input layer only, no button visuals
                MouseControlLayout(
                    isTouchProxyEnabled = isTouchProxyEnabled,
                    modifier = Modifier.fillMaxSize(),
                    cursorMode = cursorMode,
                    screenSize = screenSize,
                    onInputAreaRectUpdated = onInputAreaRectUpdated,
                    textInputMode = textInputMode,
                    isMoveOnlyPointer = { viewModel.moveOnlyPointers.contains(it) },
                    onOccupiedPointer = { viewModel.occupiedPointers.add(it) },
                    onReleasePointer = {
                        viewModel.occupiedPointers.remove(it)
                        viewModel.moveOnlyPointers.remove(it)
                    },
                    onMouseMoved = { viewModel.switchControlLayer(HideLayerWhen.WhenMouse) },
                    onTouch = { viewModel.switchControlLayer(HideLayerWhen.None) },
                    gamepadViewModel = gamepadViewModel.takeIf { AllSettings.gamepadControl.state }
                )
            }

            //Hotbar trigger layer
            val gameDisplayLayout = currentGameDisplayLayout(screenSize)
            MinecraftHotbar(
                screenSize = screenSize,
                rule = AllSettings.hotbarRule.state,
                widthPercentage = AllSettings.hotbarWidth.state.hotbarPercentage(),
                heightPercentage = AllSettings.hotbarHeight.state.hotbarPercentage(),
                sendKeycode = { keycode ->
                    CallbackBridge.sendKeyPress(keycode)
                },
                isGrabbing = isGrabbing,
                displayOffset = gameDisplayLayout.offset,
                onOccupiedPointer = { viewModel.occupiedPointers.add(it) },
                onReleasePointer = { viewModel.occupiedPointers.remove(it) }
            )
        }

        //Gyroscope control
        val isGyroscopeAvailable = remember(context) {
            isGyroscopeAvailable(context = context)
        }
        if (isGrabbing && isGyroscopeAvailable && AllSettings.gyroscopeControl.state) {
            GyroscopeReader(
                xEvent = { delta ->
                    CallbackBridge.sendCursorDelta(if (AllSettings.gyroscopeInvertX.state) -delta else delta, 0f)
                },
                yEvent = { delta ->
                    CallbackBridge.sendCursorDelta(0f, if (AllSettings.gyroscopeInvertY.state) delta else -delta)
                },
                sampleRate = AllSettings.gyroscopeSampleRate.state,
                smoothing = AllSettings.gyroscopeSmoothing.state,
                smoothingWindow = AllSettings.gyroscopeSmoothingWindow.state,
                sensitivity = AllSettings.gyroscopeSensitivity.state / 100f
            )
        }

        GameInfoBox(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(all = 16.dp),
            versionName = version.getVersionName(),
            versionInfo = version.getVersionInfo()?.getInfoString(),
            visible = showGameInfo,
            onClose = onInfoBoxClose
        )

        LogBox(
            enableLog = !viewModel.isEditingLayout && logState.value,
            onClose = {
                onLogStateChange(LogState.CLOSE)
            },
            modifier = Modifier.fillMaxSize()
        )

        GameMenuSubscreen(
            state = viewModel.gameMenuState,
            controlMenuTabIndex = viewModel.controlMenuTabIndex,
            onControlMenuTabChange = { viewModel.controlMenuTabIndex = it },
            gamepadViewModel = gamepadViewModel,
            closeScreen = { viewModel.gameMenuState = MenuState.HIDE },
            onForceClose = { viewModel.forceCloseState = ForceCloseOperation.Show },
            onSwitchLog = { onLogStateChange(logState.next()) },
            enableTerracotta = AllSettings.enableTerracotta.state,
            onOpenTerracottaMenu = { terracottaViewModel.openMenu() },
            onRefreshWindowSize = { eventViewModel.sendEvent(EventViewModel.Event.Game.RefreshSize) },
            onInputMethod = {
                eventViewModel.sendEvent(EventViewModel.Event.Game.SwitchIme(null))
            },
            onSendKeycode = { viewModel.sendKeycodeState = SendKeycodeState.ShowDialog },
            onReplacementControl = { viewModel.replacementControlState = ReplacementControlState.Show },
            onEditLayout = {
                viewModel.startControlEditor(
                    editorVM = editorViewModel
                )
            },
            onShowToast = { text, duration ->
                eventViewModel.sendToast(text, duration)
            }
        )

        //Built-in screen recorder toggle button
        if (recorderEnabled && !viewModel.isEditingLayout) {
            ScreenRecorderButton(
                isRecording = isRecording,
                onStart = { captureLauncher.launch(ScreenRecorder.createCaptureIntent(context)) },
                onStop = {
                    val saved = ScreenRecorder.stop(context)
                    eventViewModel.sendToast(
                        androidText(
                            if (saved != null) R.string.recorder_saved else R.string.recorder_failed
                        ),
                        Toast.LENGTH_LONG
                    )
                },
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        if (AllSettings.gamepadControl.state) {
            //Gamepad event capture layer
            SimpleGamepadCapture(
                gamepadViewModel = gamepadViewModel
            )
        }

        //Gamepad input mode prompt
        GamepadModePromptDialog(
            visible = gamepadViewModel.modePromptVisible,
            onConfirm = { mode ->
                gamepadViewModel.confirmModePrompt(mode)
            }
        )

        if (viewModel.isEditingLayout) {
            viewModel.currentControlFile?.let {
                ControlEditor(
                    viewModel = editorViewModel,
                    targetFile = it,
                    exit = {
                        viewModel.exitControlEditor()
                    },
                    menuExit = {
                        editorViewModel.showExitEditorDialog(
                            context = context,
                            onExit = {
                                viewModel.exitControlEditor()
                            }
                        )
                    }
                )
            }
        } else {
            if (AllSettings.showMenuBall.state) {
                //Settings decide here whether the FPS capture coroutine starts
                val showFps = AllSettings.showFPS.state
                DisposableEffect(showFps) {
                    if (showFps) viewModel.startFpsCapture()
                    onDispose {
                        viewModel.stopFpsCapture()
                    }
                }

                val gameFps: Int? = if (showFps) {
                    viewModel.gameFps
                } else {
                    null
                }

                DraggableGameBall(
                    position = AllSettings.menuBallPos.state,
                    onPositionChanged = {
                        AllSettings.menuBallPos.updateState(it)
                    },
                    onSavePos = {
                        AllSettings.menuBallPos.save()
                    },
                    gameFps = gameFps,
                    showMemory = AllSettings.showMemory.state,
                    opened = viewModel.gameMenuState == MenuState.SHOW,
                    alpha = AllSettings.menuBallOpacity.state / 100f,
                    onClick = {
                        viewModel.switchMenu()
                    }
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        eventViewModel.events
            .filterIsInstance<EventViewModel.Event.Game>()
            .collect { event ->
                when (event) {
                    is EventViewModel.Event.Game.OnBack -> {
                        if (viewModel.isEditingLayout) {
                            //In control layout edit mode
                            editorViewModel.onBackPressed(
                                context = context,
                                onExit = {
                                    viewModel.exitControlEditor()
                                }
                            )
                        } else if (!AllSettings.showMenuBall.getValue()) {
                            viewModel.switchMenu()
                        } else {
                            //Back key pressed
                            val event = ClickEvent(
                                type = ClickEvent.Type.Key,
                                key = ControlEventKeycode.GLFW_KEY_ESCAPE
                            )
                            viewModel.onKeyEvent(event, true)
                            delay(10L.milliseconds)
                            viewModel.onKeyEvent(event, false)
                        }
                    }
                    is EventViewModel.Event.Game.OnResume -> {
                        viewModel.clearState()
                    }
                    else -> { /*ignore*/ }
                }
            }
    }
}

@Composable
private fun ScreenRecorderButton(
    isRecording: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = { if (isRecording) onStop() else onStart() },
        modifier = modifier.padding(16.dp),
        containerColor = if (isRecording) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = stringResource(
                if (isRecording) R.string.recorder_stop_action else R.string.recorder_start_action
            ),
            style = MaterialTheme.typography.labelLarge,
            color = if (isRecording) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GameInfoBox(
    versionName: String,
    versionInfo: String?,
    visible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        BackgroundCard(
            modifier = modifier,
            influencedByBackground = false,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Row {
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(vertical = 16.dp)
                        .padding(start = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LoadingIndicator(
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )

                    //Hint info
                    Column(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.game_loading),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.game_loading_version_name, versionName),
                            style = MaterialTheme.typography.labelLarge
                        )
                        versionInfo?.let { info ->
                            Text(
                                text = stringResource(R.string.game_loading_version_info, info),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                IconButton(
                    modifier = Modifier.padding(top = 4.dp, end = 4.dp),
                    onClick = onClose
                ) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.generic_close)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = false)
@Composable
private fun PreviewGameInfoBox() {
    MaterialExpressiveTheme {
        GameInfoBox(
            versionName = "1.21.11",
            versionInfo = "1.21.11",
            visible = true,
            onClose = {}
        )
    }
}

/**
 * Mouse control layer
 * @param isTouchProxyEnabled whether the control proxy is enabled (TouchController mod support)
 * @param cursorMode the current mouse mode
 * @param textInputMode the IME state
 * @param isMoveOnlyPointer checks whether the pointer is marked move-only
 * @param onOccupiedPointer mark-pointer-occupied callback
 * @param onReleasePointer mark-pointer-released callback
 * @param onMouseMoved callback while a physical mouse is in use
 * @param onTouch callback when fingers touch the mouse layer
 */
@Composable
private fun MouseControlLayout(
    isTouchProxyEnabled: Boolean,
    modifier: Modifier = Modifier,
    cursorMode: Int,
    screenSize: IntSize,
    onInputAreaRectUpdated: (IntRect?) -> Unit,
    textInputMode: TextInputMode,
    isMoveOnlyPointer: (PointerId) -> Boolean,
    onOccupiedPointer: (PointerId) -> Unit,
    onReleasePointer: (PointerId) -> Unit,
    onMouseMoved: () -> Unit,
    onTouch: () -> Unit,
    gamepadViewModel: GamepadViewModel?
) {
    Box(
        modifier = modifier
            .then(
                if (isTouchProxyEnabled) {
                    Modifier
                        .touchControllerTouchModifier(
                            screenSize = screenSize
                        )
                        .touchControllerInputModifier(
                            screenSize = screenSize,
                            onInputAreaRectUpdated = onInputAreaRectUpdated,
                        )
                } else Modifier
            )
    ) {

        val capturedSpeedFactor = AllSettings.mouseCaptureSensitivity.state / 100f
        val capturedTapMouseAction = AllSettings.gestureTapMouseAction.state.toAction()
        val capturedLongPressMouseAction = AllSettings.gestureLongPressMouseAction.state.toAction()

        SwitchableMouseLayout(
            modifier = Modifier.fillMaxSize(),
            screenSize = screenSize,
            cursorMode = cursorMode,
            onTouch = onTouch,
            onMouse = onMouseMoved,
            gamepadViewModel = gamepadViewModel,
            onTap = { position ->
                val gamePosition = currentGameDisplayLayout(screenSize).mapToGame(position)
                CallbackBridge.putMouseEventWithCoords(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT.toInt(), gamePosition.x, gamePosition.y)
            },
            onCapturedTap = {
                if (AllSettings.gestureControl.state) {
                    CallbackBridge.putMouseEvent(capturedTapMouseAction)
                }
            },
            onLongPress = {
                CallbackBridge.putMouseEvent(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT.toInt(), true)
            },
            onLongPressEnd = {
                CallbackBridge.putMouseEvent(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT.toInt(), false)
            },
            onCapturedLongPress = {
                if (AllSettings.gestureControl.state) {
                    CallbackBridge.putMouseEvent(capturedLongPressMouseAction, true)
                }
            },
            onCapturedLongPressEnd = {
                if (AllSettings.gestureControl.state) {
                    CallbackBridge.putMouseEvent(capturedLongPressMouseAction, false)
                }
            },
            onPointerMove = { pos ->
                pos.sendPosition(screenSize)
            },
            onCapturedMove = { delta ->
                CallbackBridge.sendCursorDelta(
                    delta.x * capturedSpeedFactor,
                    delta.y * capturedSpeedFactor
                )
            },
            onMouseScroll = { scroll ->
                CallbackBridge.sendScroll(scroll.x.toDouble(), scroll.y.toDouble())
            },
            onMouseButton = { button, pressed ->
                val code = LWJGLCharSender.getMouseButton(button) ?: return@SwitchableMouseLayout
                CallbackBridge.sendMouseButton(code.toInt(), pressed)
            },
            isMoveOnlyPointer = isMoveOnlyPointer,
            onOccupiedPointer = onOccupiedPointer,
            onReleasePointer = onReleasePointer,
            enableScrollGesture = AllSettings.gestureControl.state,
            onScrollGesture = { scroll ->
                CallbackBridge.sendScroll(scroll.x.toDouble(), scroll.y.toDouble())
            }
        )
    }
}

private fun Offset.sendPosition(screenSize: IntSize) {
    val gamePosition = currentGameDisplayLayout(screenSize).mapToGame(this)
    CallbackBridge.sendCursorPos(gamePosition.x, gamePosition.y)
}
