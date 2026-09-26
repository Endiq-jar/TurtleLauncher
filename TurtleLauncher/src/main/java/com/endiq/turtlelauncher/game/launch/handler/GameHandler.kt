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

package com.endiq.turtlelauncher.game.launch.handler

import android.app.Activity
import android.view.KeyEvent
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.endiq.inputmap.keycodes.LwjglGlfwKeycode
import com.endiq.turtlelauncher.bridge.TLBridge
import com.endiq.turtlelauncher.game.control.ControlManager
import com.endiq.turtlelauncher.game.input.EfficientAndroidLWJGLKeycode
import com.endiq.turtlelauncher.game.input.LWJGLCharSender
import com.endiq.turtlelauncher.game.launch.GameLauncher
import com.endiq.turtlelauncher.game.launch.LaunchConfig
import com.endiq.turtlelauncher.game.launch.MCOptions
import com.endiq.turtlelauncher.game.launch.loadLanguage
import com.endiq.turtlelauncher.game.sdl.SdlBridge
import com.endiq.turtlelauncher.game.sdl.handleGamepadKeyEvent
import com.endiq.turtlelauncher.game.version.installed.GraphicsApi
import com.endiq.turtlelauncher.game.version.installed.utils.isLowerVer
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.setting.enums.GamepadInputMode
import com.endiq.turtlelauncher.terracotta.Terracotta
import com.endiq.turtlelauncher.ui.control.gamepad.isGamepadKeyEvent
import com.endiq.turtlelauncher.ui.control.input.TextInputMode
import com.endiq.turtlelauncher.ui.screens.game.GameScreen
import com.endiq.turtlelauncher.ui.screens.game.elements.LogState
import com.endiq.turtlelauncher.ui.screens.game.elements.mutableStateOfLog
import com.endiq.turtlelauncher.viewmodel.ErrorViewModel
import com.endiq.turtlelauncher.viewmodel.EventViewModel
import com.endiq.turtlelauncher.viewmodel.GamepadViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.libsdl.app.SDLActivity
import org.lwjgl.glfw.CallbackBridge

class GameHandler(
    val activity: Activity,
    config: LaunchConfig,
    errorViewModel: ErrorViewModel,
    eventViewModel: EventViewModel,
    private val gamepadViewModel: GamepadViewModel,
    gameLauncher: GameLauncher,
    onExit: (code: Int) -> Unit
) : AbstractHandler(
    type = HandlerType.GAME,
    errorViewModel = errorViewModel,
    eventViewModel = eventViewModel,
    launcher = gameLauncher,
    onExit = onExit
) {
    private val version = config.version
    private val account = config.account

    private val _inputArea = MutableStateFlow<IntRect?>(null)
    override val inputArea = _inputArea.asStateFlow()

    private var isGameRendering = false
    private var showGameInfo by mutableStateOf(true)

    /**
     * Log display state
     */
    private var logState by mutableStateOfLog()

    override suspend fun execute(
        surface: Surface,
        screenSize: IntSize,
        scope: CoroutineScope
    ) {
        TLBridge.setupBridgeWindow(surface)

        MCOptions.setup(activity, version)

        MCOptions.apply {
            set("fullscreen", "false")
            set("touchscreen", "false")

//            //Disable the text-to-speech feature
//            set("options.narrator", "0")
//            set("narrator", "0")

            if (version.getVersionInfo()!!.minecraftVersion.isLowerVer("1.13")) {
                //fix: key events of ancient versions
                //shift + w -> 87 wrongly triggered F11, toggling fullscreen
                set("key_key.fullscreen", "0")
                //typing @ -> 64 wrongly triggered F6, "start/stop streaming"
                set("key_key.streamStartStop", "0")
                set("key_key.streamPauseUnpause", "0")
            }

            set("overrideWidth", screenSize.width.toString())
            set("overrideHeight", screenSize.height.toString())

            val graphicsApi = version.getGraphicsApi()
            val graphicsOption = "preferredGraphicsBackend"
            when (graphicsApi) {
                GraphicsApi.DEFAULT, GraphicsApi.DEFAULT_OPENGL -> {
                    if (!containsKey(graphicsOption)) {
                        set(graphicsOption, graphicsApi.option)
                    }
                }
                else -> set(graphicsOption, graphicsApi.option)
            }

            loadLanguage(version.getVersionInfo()!!.minecraftVersion)
            save()
        }

        super.execute(surface, screenSize, scope)
    }

    override fun onPause() {
    }

    override fun onResume() {
        refreshControls()
        eventViewModel.sendEvent(EventViewModel.Event.Game.OnResume)
    }

    override fun onDestroy() {
        Terracotta.setWaiting(false)
    }

    override fun onGraphicOutput() {
        if (!isGameRendering) {
            isGameRendering = true
            showGameInfo = false
            //The game has started rendering; if the log state is "show before render", close the log here
            if (logState == LogState.SHOW_BEFORE_LOADING) {
                logState = LogState.CLOSE
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun shouldIgnoreKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP && (event.flags and KeyEvent.FLAG_CANCELED) != 0) return false

        if (event.isGamepadKeyEvent()) {
            if (AllSettings.gamepadControl.state && gamepadViewModel.checkModePrompt()) {
                //Before the mode-selection prompt resolves, swallow all gamepad key input
                return false
            }
            if (AllSettings.gamepadControl.state && AllSettings.gamepadInputMode.state == GamepadInputMode.SdlDirect) {
                //In SDL passthrough mode, keys are handed verbatim to SDL native
                if (SdlBridge.sdlEnabled) {
                    //Let control layouts and other registrants sense the gamepad is in use
                    gamepadViewModel.notifyActivity()
                    handleGamepadKeyEvent(event)
                    try {
                        SDLActivity.handleKeyEvent(null, event.keyCode, event, null)
                    } catch (_: UnsatisfiedLinkError) {
                        //Ignored while SDL native isn't ready
                    }
                }
                return false
            }
            return if (AllSettings.gamepadControl.state) {
                //When enabled, send events early for the UI layer to handle (or remap)
                gamepadViewModel.sendKeyEvent(event)
                false
            } else {
                //Gamepad control is disabled; keep it from being treated as a keyboard event further down
                if (AllSettings.showMenuBall.state) {
                    //Fully unresponsive while the game-menu overlay is open
                    false
                } else {
                    true
                }
            }
        }
        //VMActivity already binds onBackPressedDispatcher; don't continue handling here
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return true

        if ((event.flags and KeyEvent.FLAG_SOFT_KEYBOARD) == KeyEvent.FLAG_SOFT_KEYBOARD) {
            if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                LWJGLCharSender.sendEnter()
                return false
            }
        }

        EfficientAndroidLWJGLKeycode.getIndexByKey(event.keyCode).takeIf { it >= 0 }?.let { index ->
            EfficientAndroidLWJGLKeycode.execKey(event, index)
            return false
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_UNKNOWN,
            KeyEvent.ACTION_MULTIPLE,
            KeyEvent.ACTION_UP
                 -> false

            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_UP
                 -> true

            else -> (event.flags and KeyEvent.FLAG_FALLBACK) != KeyEvent.FLAG_FALLBACK
        }
    }

    override fun sendMouseRight(isPressed: Boolean) {
        CallbackBridge.sendMouseButton(LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT.toInt(), isPressed)
    }

    @Composable
    override fun ComposableLayout(
        textInputMode: TextInputMode
    ) {
        GameScreen(
            version = version,
            gameHandler = this,
            showGameInfo = showGameInfo,
            onInfoBoxClose = { showGameInfo = false },
            logState = logState,
            onLogStateChange = { logState = it },
            textInputMode = textInputMode,
            isTouchProxyEnabled = version.enableTouchProxy,
            onInputAreaRectUpdated = { _inputArea.value = it },
            getAccountName = { account.username },
            eventViewModel = eventViewModel,
            gamepadViewModel = gamepadViewModel,
        )
    }

    private fun refreshControls() {
        ControlManager.refresh()
    }

    init {
        refreshControls()
    }
}