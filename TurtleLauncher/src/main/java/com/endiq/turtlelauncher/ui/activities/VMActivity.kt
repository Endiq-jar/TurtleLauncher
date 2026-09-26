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

package com.endiq.turtlelauncher.ui.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.Keep
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jakewharton.processphoenix.ProcessPhoenix
import com.endiq.inputmap.keycodes.LwjglGlfwKeycode
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.bridge.CURSOR_DISABLED
import com.endiq.turtlelauncher.bridge.FliteTts
import com.endiq.turtlelauncher.bridge.LoggerBridge
import com.endiq.turtlelauncher.bridge.TLBridge
import com.endiq.turtlelauncher.feature.recorder.ScreenRecorder
import com.endiq.turtlelauncher.bridge.TLBridgeStates
import com.endiq.turtlelauncher.coroutine.DataBridge
import com.endiq.turtlelauncher.game.account.Account
import com.endiq.turtlelauncher.game.input.AWTCharSender
import com.endiq.turtlelauncher.game.input.CharacterSenderStrategy
import com.endiq.turtlelauncher.game.input.LWJGLCharSender
import com.endiq.turtlelauncher.game.launch.GameLauncher
import com.endiq.turtlelauncher.game.launch.GameService
import com.endiq.turtlelauncher.game.launch.JvmLaunchInfo
import com.endiq.turtlelauncher.game.launch.JvmLauncher
import com.endiq.turtlelauncher.game.launch.LaunchConfig
import com.endiq.turtlelauncher.game.launch.Launcher
import com.endiq.turtlelauncher.game.launch.handler.AbstractHandler
import com.endiq.turtlelauncher.game.launch.handler.GameHandler
import com.endiq.turtlelauncher.game.launch.handler.HandlerType
import com.endiq.turtlelauncher.game.launch.handler.JVMHandler
import com.endiq.turtlelauncher.game.multirt.RuntimesManager
import com.endiq.turtlelauncher.game.plugin.PluginLoader
import com.endiq.turtlelauncher.game.renderer.Renderers
import com.endiq.turtlelauncher.game.sdl.SdlBridge
import com.endiq.turtlelauncher.game.version.installed.Version
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.setting.enums.ResolutionRule
import com.endiq.turtlelauncher.terracotta.TerracottaVPNService
import com.endiq.turtlelauncher.ui.base.BaseAppCompatActivity
import com.endiq.turtlelauncher.ui.base.ObserveFullScreenSetting
import com.endiq.turtlelauncher.ui.components.rememberBoxSize
import com.endiq.turtlelauncher.ui.control.input.HidableInputLayout
import com.endiq.turtlelauncher.ui.control.input.TextInputMode
import com.endiq.turtlelauncher.ui.screens.game.elements.OpenFolderLayer
import com.endiq.turtlelauncher.ui.screens.game.elements.OpenFolderOperation
import com.endiq.turtlelauncher.ui.theme.TurtleLauncherTheme
import com.endiq.turtlelauncher.ui.toAndroidString
import com.endiq.turtlelauncher.utils.computeGameDisplayLayout
import com.endiq.turtlelauncher.utils.computeGameRenderSize
import com.endiq.turtlelauncher.utils.device.PhysicalMouseChecker
import com.endiq.turtlelauncher.utils.getDisplayFriendlyRes
import com.endiq.turtlelauncher.utils.getParcelableSafely
import com.endiq.turtlelauncher.utils.rememberGameRenderSize
import com.endiq.turtlelauncher.viewmodel.ErrorViewModel
import com.endiq.turtlelauncher.viewmodel.EventViewModel
import com.endiq.turtlelauncher.viewmodel.GamepadViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.libsdl.app.SDLActivity
import org.libsdl.app.SDLSurface
import org.lwjgl.glfw.CallbackBridge
import java.io.File
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import android.graphics.Color as NativeColor


private const val INTENT_RUN_GAME = "BUNDLE_RUN_GAME"
private const val INTENT_RUN_JAR = "INTENT_RUN_JAR"
private const val INTENT_GAME_CONFIG = "INTENT_GAME_CONFIG"
private const val INTENT_JAR_INFO = "INTENT_JAR_INFO"

data class LaunchSession(
    val activityTitle: String,
    val launcher: Launcher,
    val handler: AbstractHandler,
    val inputSender: CharacterSenderStrategy
)

/**
 * Some critical states must be kept here
 */
class VMViewModel : ViewModel() {
    var isRunning = false

    /**
     * Whether VMActivity is allowed to handle key events
     */
    var keyHandle = true

    val screenSizeBridge = DataBridge<IntSize>()
    var screenSize: IntSize = IntSize.Zero

    private val _onConfigurationChanged = MutableStateFlow(false)
    val onConfigurationChanged = _onConfigurationChanged.asStateFlow()

    fun onConfigurationChanged(value: Boolean = true) {
        _onConfigurationChanged.update { value }
    }

    var sender: CharacterSenderStrategy = LWJGLCharSender
        private set

    private val _openFolderOperation = MutableStateFlow<OpenFolderOperation>(OpenFolderOperation.None)
    /** Directory browsing inside the launcher (imports files into that directory) */
    val openFolderOperation = _openFolderOperation.asStateFlow()

    /** Close directory browsing */
    fun clearFolder() {
        _openFolderOperation.update {
            OpenFolderOperation.None
        }
    }

    private var _session: LaunchSession? = null
    val session: LaunchSession
        get() = _session ?: error("LaunchSession not initialized")

    fun initSession(
        activity: VMActivity,
        bundle: Bundle,
        errorViewModel: ErrorViewModel,
        eventViewModel: EventViewModel,
        gamepadViewModel: GamepadViewModel,
        exitListener: (Int, Boolean) -> Unit,
    ) {
        if (_session != null) return

        _session = when {
            bundle.getBoolean(INTENT_RUN_GAME) -> {
                val config: LaunchConfig = bundle.getParcelableSafely(INTENT_GAME_CONFIG, LaunchConfig::class.java)
                    ?: throw IllegalStateException("No launch config has been set.")

                val launcher = GameLauncher(
                    activity = activity,
                    config = config,
                    onExit = { code, isSignal ->
                        if (code == 0) {
                            val finishedCount = AllSettings.finishedGame.getValue()
                            if (finishedCount < Int.MAX_VALUE)  {
                                AllSettings.finishedGame.save(finishedCount + 1)
                            }
                        }
                        exitListener(code, isSignal)
                    },
                    openPath = { folder ->
                        _openFolderOperation.update {
                            OpenFolderOperation.OpenFolder(folder)
                        }
                    }
                )

                sender = LWJGLCharSender

                LaunchSession(
                    activityTitle = config.version.getVersionName(),
                    launcher = launcher,
                    handler = GameHandler(
                        activity = activity,
                        config = config,
                        errorViewModel = errorViewModel,
                        eventViewModel = eventViewModel,
                        gamepadViewModel = gamepadViewModel,
                        gameLauncher = launcher
                    ) { code ->
                        exitListener(code, false)
                    },
                    inputSender = LWJGLCharSender
                )
            }
            bundle.getBoolean(INTENT_RUN_JAR) -> {
                val jvmLaunchInfo: JvmLaunchInfo = bundle.getParcelableSafely(INTENT_JAR_INFO, JvmLaunchInfo::class.java)
                    ?: throw IllegalStateException("No launch jar info has been set.")

                val launcher = JvmLauncher(
                    context = activity,
                    jvmLaunchInfo = jvmLaunchInfo,
                    onExit = exitListener,
                    openPath = { folder ->
                        _openFolderOperation.update {
                            OpenFolderOperation.OpenFolder(folder)
                        }
                    }
                )

                sender = AWTCharSender

                LaunchSession(
                    activityTitle = activity.getString(R.string.execute_jar_title),
                    launcher = launcher,
                    handler = JVMHandler(
                        jvmLauncher = launcher,
                        errorViewModel = errorViewModel,
                        eventViewModel = eventViewModel,
                    ) { code ->
                        exitListener(code, false)
                    },
                    inputSender = AWTCharSender
                )
            }
            else -> error("Unknown VM launch mode")
        }
    }

    /**
     * Current input method enabled state
     */
    var textInputMode by mutableStateOf(TextInputMode.DISABLE)

    fun disableInputMode() {
        if (textInputMode == TextInputMode.ENABLE) textInputMode = TextInputMode.DISABLE
    }


    /**
     * Sends text directly to the game
     */
    private fun String.sendText() {
        forEach { char ->
            sender.sendChar(char)
        }
    }

    fun sendInputText(text: String) {
        text.sendText()
    }

    fun sendBackspace() {
        sender.sendBackspace()
    }

    fun sendEnder() {
        sender.sendEnter()
    }

    /**
     * Handles special keys only
     */
    fun handleSpecialKey(keyEvent: KeyEvent) {
        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ENTER -> {
                //Ignore delete events to avoid state desynchronization
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> sender.sendLeft()
            KeyEvent.KEYCODE_DPAD_RIGHT -> sender.sendRight()
            KeyEvent.KEYCODE_DPAD_UP -> sender.sendUp()
            KeyEvent.KEYCODE_DPAD_DOWN -> sender.sendDown()

            KeyEvent.KEYCODE_TAB -> sender.sendTab()

            else -> sender.sendOther(keyEvent)
        }
    }

    /**
     * Returns whether this key event is allowed to be handled
     */
    fun keyCanHandle(keyEvent: KeyEvent): Boolean {
        val keyCode = keyEvent.keyCode
        //Because the IME emits Shift key events when selecting text, while synchronizing selection into the in-game text would be complicated,
        //for example there is no way to know which text the input box has selected, which easily causes state differences between the input box and the in-game text,
        //so such expectation-breaking situations should be avoided; Shift should be ignored
        val isShift = keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT
        //Avoid handling Ctrl: most IMEs cannot handle it, and in-game it could affect the pointer position
        val isCtrl = keyCode == KeyEvent.KEYCODE_CTRL_LEFT || keyCode == KeyEvent.KEYCODE_CTRL_RIGHT
        return !isShift && !isCtrl
    }
}

class VMActivity : BaseAppCompatActivity(), SurfaceTextureListener, SurfaceHolder.Callback {
    override fun isIgnoreNotch(): Boolean = AllSettings.gameFullScreen.getValue()

    private val errorViewModel: ErrorViewModel by viewModels()

    private val eventViewModel: EventViewModel by viewModels()
    /**
     * ViewModel that stores gamepad state
     */
    private val gamepadViewModel: GamepadViewModel by viewModels()

    private val vmViewModel: VMViewModel by viewModels()

    /** View used to locate the layout hosting SDL's text input bridge. */
    private var gameSurfaceView: View? = null

    private var applySizeToSurface: ((width: Int, height: Int) -> Unit)? = null

    private inline fun <T> withHandler(block: AbstractHandler.() -> T): T {
        return vmViewModel.session.handler.block()
    }

    private inline fun <T> withLauncher(block: Launcher.() -> T): T {
        return vmViewModel.session.launcher.block()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //Load the renderer
        Renderers.init()
        //Load plugins
        PluginLoader.loadAllPlugins(this, false)
        refreshData()

        //Initialize the physical mouse connection checker
        PhysicalMouseChecker.initChecker(this)

        //Start the foreground service to prevent the network from being cut in the background
        runCatching {
            //The system refuses to start it while the app is in a restricted state like the background; keep-alive is unnecessary then, ignore it
            startService(Intent(this, GameService::class.java))
        }

        val bundle = intent.extras ?: throw IllegalStateException("Unknown VM launch state!")

        vmViewModel.initSession(
            activity = this,
            bundle = bundle,
            errorViewModel = errorViewModel,
            eventViewModel = eventViewModel,
            gamepadViewModel = gamepadViewModel,
            exitListener = { exitCode: Int, isSignal: Boolean ->
                stopAllService()
                if (exitCode != 0) {
                    val logPath = withLauncher {
                        getLogFile().absolutePath
                    }
                    showExitMessage(this@VMActivity, exitCode, isSignal, logPath)
                } else {
                    //Restart the launcher
                    ProcessPhoenix.triggerRebirth(this@VMActivity)
                }
            }
        )

        //Set the frame render output callback
        CallbackBridge.setGraphicOutputListener {
            withHandler { onGraphicOutput() }
        }

        window?.apply {
            setBackgroundDrawable(NativeColor.BLACK.toDrawable())
            if (AllSettings.sustainedPerformance.getValue()) {
                setSustainedPerformanceMode(true)
            }
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // prevent the system from sleeping
        }

        val logFile = withLauncher { getLogFile() }
        logFile.parentFile?.mkdirs() // the log file path changed once, so creating the parent directory here is necessary
        if (!logFile.exists() && !logFile.createNewFile()) throw IOException("Failed to create a new log file")
        LoggerBridge.start(logFile.absolutePath)

        //Show error information
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                errorViewModel.errorEvents.collect { tm ->
                    errorViewModel.showErrorDialog(
                        context = this@VMActivity,
                        tm = tm
                    )
                }
            }
        }

        lifecycleScope.launch {
            //Start receiving events
            eventViewModel.events.collect { event ->
                when (event) {
                    is EventViewModel.Event.Game.RefreshSize -> {
                        vmViewModel.onConfigurationChanged()
                    }
                    is EventViewModel.Event.Game.SwitchIme -> {
                        vmViewModel.textInputMode = event.mode ?: vmViewModel.textInputMode.switch()
                    }
                    is EventViewModel.Event.Game.KeyHandle -> {
                        vmViewModel.keyHandle = event.handle
                    }
                    is EventViewModel.Event.ShowToast -> {
                        Toast.makeText(
                            this@VMActivity,
                            event.text.toAndroidString(this@VMActivity),
                            event.duration
                        ).show()
                    }
                    else -> { /* Ignore */ }
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (vmViewModel.textInputMode == TextInputMode.ENABLE) {
                    //They probably want to leave the input box
                    vmViewModel.disableInputMode()
                    return
                }
                if (!vmViewModel.keyHandle) return

                eventViewModel.sendEvent(EventViewModel.Event.Game.OnBack)
            }
        })

        //After closing the menu, remind on every game launch, since some users tap by mistake and get stuck >:(
        if (!AllSettings.showMenuBall.getValue()) {
            Toast.makeText(
                this@VMActivity,
                getString(R.string.game_menu_option_show_menu_hided),
                Toast.LENGTH_LONG
            ).show()
        }

        setContent {
            TurtleLauncherTheme {
                ObserveFullScreenSetting(AllSettings.gameFullScreen.state)
                Screen {
                    withHandler {
                        ComposableLayout(vmViewModel.textInputMode)
                    }

                    if (vmViewModel.textInputMode == TextInputMode.ENABLE) {
                        //Input bar control area
                        HidableInputLayout(
                            onSend = { text ->
                                vmViewModel.sendInputText(text)
                            },
                            onBackspace = {
                                vmViewModel.sendBackspace()
                            },
                            onEnter = {
                                vmViewModel.sendEnder()
                            },
                            onClose = {
                                vmViewModel.textInputMode = TextInputMode.DISABLE
                            }
                        )
                    }

                    //Close the input box when the mouse switches to captured mode
                    val cursorMode by TLBridgeStates.cursorMode.collectAsStateWithLifecycle()
                    LaunchedEffect(cursorMode) {
                        if (cursorMode == CURSOR_DISABLED) vmViewModel.disableInputMode()
                    }

                    val operation by vmViewModel.openFolderOperation.collectAsStateWithLifecycle()
                    OpenFolderLayer(
                        modifier = Modifier.fillMaxSize(),
                        operation = operation,
                        requestClose = {
                            vmViewModel.clearFolder()
                        },
                        lifecycleScope = lifecycleScope
                    )
                }
            }
        }
    }

    override fun getTaskDescriptionTitle(): String? {
        return runCatching {
            vmViewModel.session.activityTitle
        }.getOrNull()
    }

    override fun onResume() {
        super.onResume()
        withHandler { onResume() }
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_FOCUSED, 1)
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 1)
    }

    override fun onPause() {
        super.onPause()
        withHandler { onPause() }
        CallbackBridge.resetInputState()
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_FOCUSED, 0)
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 0)
    }

    override fun onStart() {
        super.onStart()
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 1)
    }

    override fun onStop() {
        super.onStop()
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_HOVERED, 0)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            CallbackBridge.resetInputState()
        }
        CallbackBridge.nativeSetWindowAttrib(LwjglGlfwKeycode.GLFW_FOCUSED, if (hasFocus) 1 else 0)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        vmViewModel.onConfigurationChanged()
    }

    /**
     * SDL dynamically requests orientation from window size at creation; missing this can set it to portrait,
     * override here to force-lock sensorLandscape
     */
    override fun setRequestedOrientation(requestedOrientation: Int) {
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
    }

    override fun onPostResume() {
        super.onPostResume()
        if (vmViewModel.isRunning) {
            requestRefreshWindowSize(screenSize = vmViewModel.screenSize)
        }
    }

    /**
     * Reference screen size for size refresh
     * With a custom resolution the game Surface no longer fills the screen; its size is no reference, so the fullscreen layout size wins
     */
    private fun referenceScreenSize(fallback: IntSize): IntSize {
        return vmViewModel.screenSize.takeIf { it.width > 0 && it.height > 0 } ?: fallback
    }

    private var lastWindowSize: IntSize? = null
    private var refreshSizeJob: Job? = null
    private var pendingRefreshSize: IntSize? = null
    /**
     * Event-driven entry for window size refresh
     */
    private fun requestRefreshWindowSize(screenSize: IntSize) {
        if (screenSize.width <= 0 || screenSize.height <= 0) return
        pendingRefreshSize = screenSize
        refreshSizeJob?.cancel()
        refreshSizeJob = lifecycleScope.launch {
            delay(50L.milliseconds)
            val size = pendingRefreshSize ?: return@launch
            pendingRefreshSize = null
            withContext(Dispatchers.Main) {
                refreshWindowSize(screenSize = size)
            }
        }
    }

    private fun refreshWindowSize(
        screenSize: IntSize
    ): IntSize {
        val newSize = withHandler {
            when (type) {
                HandlerType.GAME -> computeGameRenderSize(screenSize)
                HandlerType.JVM -> IntSize(
                    getDisplayFriendlyRes(screenSize.width, 0.8f),
                    getDisplayFriendlyRes(screenSize.height, 0.8f)
                )
            }
        }
        lastWindowSize = newSize

        applySizeToSurface?.invoke(newSize.width, newSize.height)
        TLBridgeStates.onWindowChange()
        CallbackBridge.sendUpdateWindowSize(newSize.width, newSize.height)
        if (SdlBridge.sdlEnabled) {
            SDLActivity.getSDLSurface()?.let { surface ->
                surface.surfaceChanged()
                surface.nativeResize(newSize.width, newSize.height)
            }
        }

        return newSize
    }

    override fun onDestroy() {
        runCatching { ScreenRecorder.stop(this) }
        stopAllService()
        withHandler { onDestroy() }
        SdlBridge.reset()
        FliteTts.shutdown()
        super.onDestroy()
    }

    private fun stopAllService() {
        stopService(Intent(this, GameService::class.java))
        if (TerracottaVPNService.isRunning()) {
            //Stop commands must go through stopService
            stopService(Intent(this, TerracottaVPNService::class.java))
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!vmViewModel.keyHandle) return super.dispatchKeyEvent(event)

        val isPressed = event.action == KeyEvent.ACTION_DOWN

        val code = AllSettings.physicalKeyImeCode.state
        if (isPressed && code != null && event.keyCode == code) {
            //The user pressed the key bound to summon the IME
            //Toggle the IME
            vmViewModel.textInputMode = vmViewModel.textInputMode.switch()
            return true
        }
        if (vmViewModel.textInputMode == TextInputMode.ENABLE) {
            if (isPressed && !vmViewModel.keyCanHandle(event)) {
                return super.dispatchKeyEvent(event)
            }

            if (isPressed) {
                vmViewModel.handleSpecialKey(event)
            }

            if (event.keyCode == KeyEvent.KEYCODE_TAB) {
                //For the Tab key, intercept directly here to avoid selecting other components
                return true
            }
            //While typing, stop processing key events any further
            //otherwise some IME function keys stop working
            return super.dispatchKeyEvent(event)
        }
        event.device?.let {
            val source = event.source
            if (source and InputDevice.SOURCE_MOUSE_RELATIVE == InputDevice.SOURCE_MOUSE_RELATIVE ||
                source and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE) {

                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    //Some systems report right-click as KEYCODE_BACK, so intercept it here
                    //then send the real right mouse button
                    withHandler { sendMouseRight(isPressed) }
                    return false
                }
            }
        }
        withHandler {
            if (shouldIgnoreKeyEvent(event)) {
                return super.dispatchKeyEvent(event)
            }
        }
        return true
    }

    @Keep
    fun messageboxShowMessageBox(
        flags: Int,
        title: String?,
        message: String?,
        buttonFlags: IntArray,
        buttonIds: IntArray,
        buttonTexts: Array<String?>,
        colors: IntArray?
    ): Int = SDLActivity.messageboxShowMessageBox(
        this, flags, title, message, buttonFlags, buttonIds, buttonTexts, colors
    )

    /**
     * Asks the system to switch the screen to the highest supported refresh rate, keeping the game uncapped by a user-set lower tier
     *
     * See MinecraftGLSurface (https://github.com/AngelAuraMC/Amethyst-Android/blob/v3_openjdk/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/MinecraftGLSurface.java)
     */
    private fun voteMaxDisplayRefreshRate(surface: Surface) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val maxRefreshRate = maxOf(120f, *display.mode.alternativeRefreshRates)
        surface.setFrameRate(
            maxRefreshRate,
            Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
            Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS
        )
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        val nativeSurface = Surface(surface)
        voteMaxDisplayRefreshRate(nativeSurface)
        SdlBridge.prepareSurface(this, nativeSurface, gameSurfaceView?.parent as? ViewGroup, surface)
        //Notification receiver when the game requests GLFW direct gamepad
        CallbackBridge.setDirectGamepadEnableHandler {
            LoggerBridge.append("TurtleLauncher: Direct gamepad handler enabled")
        }
        if (vmViewModel.isRunning) {
            TLBridge.setupBridgeWindow(nativeSurface)
            return
        }
        vmViewModel.isRunning = true

        withHandler { mIsSurfaceDestroyed = false }
        lifecycleScope.launch(Dispatchers.Default) {
            val screenSize = vmViewModel.screenSizeBridge.awaitData()
            val currentSize = refreshWindowSize(screenSize = screenSize)
            withHandler {
                execute(
                    surface = Surface(surface),
                    screenSize = currentSize,
                    scope = lifecycleScope
                )
            }
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        if (withHandler { mIsSurfaceDestroyed }) return
        requestRefreshWindowSize(screenSize = referenceScreenSize(fallback = IntSize(width, height)))
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        val nativeSurface = SDLSurface.getNativeSurface()
        if (SdlBridge.beginSurfaceDestroy(surface, nativeSurface)) {
            if (SdlBridge.sdlEnabled) {
                SDLActivity.getSDLSurface()?.surfaceDestroyed()
            }
            SdlBridge.unregisterSurface(nativeSurface)
        }
        withHandler { mIsSurfaceDestroyed = true }
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (withHandler { mIsSurfaceDestroyed }) return
        val viewWidth = gameSurfaceView?.width ?: 0
        val viewHeight = gameSurfaceView?.height ?: 0
        if (viewWidth <= 0 || viewHeight <= 0) return
        requestRefreshWindowSize(screenSize = referenceScreenSize(fallback = IntSize(viewWidth, viewHeight)))
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val surface = holder.surface
        voteMaxDisplayRefreshRate(surface)
        SdlBridge.prepareSurface(this, surface, gameSurfaceView?.parent as? ViewGroup, holder)
        if (vmViewModel.isRunning) {
            TLBridge.setupBridgeWindow(surface)
            return
        }
        vmViewModel.isRunning = true

        withHandler { mIsSurfaceDestroyed = false }
        lifecycleScope.launch(Dispatchers.Default) {
            val screenSize = vmViewModel.screenSizeBridge.awaitData()
            val currentSize = refreshWindowSize(screenSize = screenSize)
            withHandler {
                execute(
                    surface = surface,
                    screenSize = currentSize,
                    scope = lifecycleScope
                )
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        val nativeSurface = holder.surface
        if (SdlBridge.beginSurfaceDestroy(holder, nativeSurface)) {
            if (SdlBridge.sdlEnabled) {
                SDLActivity.getSDLSurface()?.surfaceDestroyed(holder)
            }
            SdlBridge.unregisterSurface(nativeSurface)
        }
        withHandler { mIsSurfaceDestroyed = true }
    }

    @Composable
    private fun Screen(
        content: @Composable () -> Unit = {}
    ) {
        val imeInsets = WindowInsets.ime
        val inputArea by withHandler { inputArea }.collectAsStateWithLifecycle()
        val density = LocalDensity.current

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val screenSize = rememberBoxSize()

            val changed by vmViewModel.onConfigurationChanged.collectAsStateWithLifecycle()
            LaunchedEffect(screenSize, changed) {
                vmViewModel.screenSize = screenSize
                vmViewModel.screenSizeBridge.provideData(screenSize)
                if (changed) {
                    requestRefreshWindowSize(screenSize = screenSize)
                    vmViewModel.onConfigurationChanged(false)
                }
            }

            //Game mode with a custom resolution: the view scales aspect-fit, centered (letterboxed)
            val letterboxed = withHandler { type } == HandlerType.GAME &&
                    AllSettings.resolutionRule.state == ResolutionRule.CUSTOM
            val renderSize = rememberGameRenderSize(screenSize)
            val displaySize = if (letterboxed) computeGameDisplayLayout(screenSize, renderSize).displaySize else screenSize

            AndroidView(
                modifier = Modifier
                    .then(
                        if (displaySize == screenSize) Modifier.fillMaxSize()
                        else Modifier
                            .align(Alignment.Center)
                            .size(
                                width = with(density) { displaySize.width.toDp() },
                                height = with(density) { displaySize.height.toDp() }
                            )
                    )
                    .absoluteOffset {
                        val area = inputArea ?: return@absoluteOffset IntOffset.Zero
                        val imeHeight = imeInsets.getBottom(this@absoluteOffset)
                        val bottomDistance = screenSize.height - area.bottom
                        val bottomPadding = (imeHeight - bottomDistance).coerceAtLeast(0)
                        IntOffset(0, -bottomPadding)
                    },
                factory = { context ->
                    val view = if (AllSettings.useSurfaceView.getValue()) {
                        //Renders with a SurfaceView
                        SurfaceView(context).apply {
                            holder.addCallback(this@VMActivity)
                            // SDL mode needs the parent ViewGroup (for the IME EditText)
                            gameSurfaceView = this
                        }.also { surface ->
                            applySizeToSurface = { width, height ->
                                surface.holder.setFixedSize(width, height)
                            }
                        }
                    } else {
                        TextureView(context).apply {
                            isOpaque = true
                            alpha = 1.0f

                            surfaceTextureListener = this@VMActivity
                        }.also { texture ->
                            gameSurfaceView = texture
                            applySizeToSurface = { width, height ->
                                texture.surfaceTexture?.setDefaultBufferSize(width, height)
                            }
                        }
                    }
                    view.setOnApplyWindowInsetsListener { v, insets ->
                        if (SdlBridge.sdlEnabled && android.os.Build.VERSION.SDK_INT >= 30) {
                            SDLActivity.notifyImeVisibilityChanged(
                                insets.isVisible(android.view.WindowInsets.Type.ime())
                            )
                        }
                        v.onApplyWindowInsets(insets)
                    }
                    view
                }
            )

            content()
        }
    }
}

/**
 * Puts VMActivity into game-running mode
 * @param version the version to launch
 */
fun runGame(
    context: Context,
    version: Version,
    account: Account,
) {
    startGameService(context)
    val intent = Intent(context, VMActivity::class.java).apply {
        putExtra(INTENT_RUN_GAME, true)
        putExtra(INTENT_GAME_CONFIG, LaunchConfig(version, account))
    }
    context.startActivity(intent)
}

/**
 * Puts VMActivity into jar-running mode
 * @param jarFile the jar file to run
 * @param jreName the Java runtime to use; null picks automatically
 * @param customArgs custom JVM arguments
 */
fun runJar(
    context: Context,
    jarFile: File,
    jreName: String? = null,
    customArgs: String? = null
) {
    RuntimesManager.getExactJreName(8) ?: run {
        Toast.makeText(context, R.string.multirt_no_java_8, Toast.LENGTH_SHORT).show()
        return
    }

    val jvmArgsPrefix = customArgs?.let { "$it " } ?: ""
    val jvmArgs = "$jvmArgsPrefix-jar ${jarFile.absolutePath}"

    val jvmLaunchInfo = JvmLaunchInfo(
        jvmArgs = jvmArgs,
        jreName = jreName
    )

    startGameService(context)

    val intent = Intent(context, VMActivity::class.java).apply {
        putExtra(INTENT_RUN_JAR, true)
        putExtra(INTENT_JAR_INFO, jvmLaunchInfo)
    }
    context.startActivity(intent)
}

private fun startGameService(context: Context) {
    runCatching {
        //The system refuses to start it while the app is in a restricted state like the background; keep-alive is unnecessary then, ignore it
        context.startService(Intent(context, GameService::class.java))
    }
}
