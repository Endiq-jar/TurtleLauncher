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

package com.endiq.turtlelauncher.setting

import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.PaletteStyle
import com.endiq.layer_controller.utils.snap.SnapMode
import com.endiq.turtlelauncher.BuildKeys
import com.endiq.turtlelauncher.game.download.assets.platform.Platform
import com.endiq.turtlelauncher.game.path.GamePathManager
import com.endiq.turtlelauncher.game.version.installed.GraphicsApi
import com.endiq.turtlelauncher.setting.enums.ActionMenuSide
import com.endiq.turtlelauncher.setting.enums.AppLanguage
import com.endiq.turtlelauncher.setting.enums.BackgroundBlur
import com.endiq.turtlelauncher.setting.enums.DarkMode
import com.endiq.turtlelauncher.setting.enums.GamepadInputMode
import com.endiq.turtlelauncher.setting.enums.GestureActionType
import com.endiq.turtlelauncher.setting.enums.MirrorSourceType
import com.endiq.turtlelauncher.setting.enums.MouseControlMode
import com.endiq.turtlelauncher.setting.enums.ResolutionRule
import com.endiq.turtlelauncher.ui.control.HotbarRule
import com.endiq.turtlelauncher.ui.control.gamepad.JoystickMode
import com.endiq.turtlelauncher.ui.control.mouse.CENTER_HOTSPOT
import com.endiq.turtlelauncher.ui.control.mouse.CursorHotspot
import com.endiq.turtlelauncher.ui.control.mouse.LEFT_TOP_HOTSPOT
import com.endiq.turtlelauncher.ui.theme.ColorThemeType
import com.endiq.turtlelauncher.utils.animation.TransitionAnimationType

object AllSettings : SettingsRegistry() {
    //Renderer
    /**
     * Global renderer
     */
    val renderer = stringSetting("renderer", "")

    /**
     * Vulkan driver
     */
    val vulkanDriver = stringSetting("vulkanDriver", "default turnip")

    /**
     * Graphics API (Minecraft 26.2+)
     */
    val graphicsApi = enumSetting("graphicsApi", GraphicsApi.DEFAULT_OPENGL)

    /**
     * Resolution scale
     */
    val resolutionRatio = intSetting("resolutionRatio", 100, 25..300)

    /**
     * Resolution rule
     */
    val resolutionRule = enumSetting("resolutionRule", ResolutionRule.PERCENTAGE)

    /**
     * Custom resolution width; 0 means not initialized yet
     */
    val customResolutionWidth = intSetting("customResolutionWidth", 0)

    /**
     * Custom resolution height; 0 means not initialized yet
     */
    val customResolutionHeight = intSetting("customResolutionHeight", 0)

    /**
     * Fullscreen game surface
     */
    val gameFullScreen = boolSetting("gameFullScreen", true)

    /**
     * Render using a SurfaceView
     */
    val useSurfaceView = boolSetting("useSurfaceView", false)

    /**
     * Sustained performance mode
     */
    val sustainedPerformance = boolSetting("sustainedPerformance", false)

    /**
     * Use the system Vulkan driver
     */
    val zinkPreferSystemDriver = boolSetting("zinkPreferSystemDriver", false)

    /**
     * Zink VSync
     */
    val vsyncInZink = boolSetting("vsyncInZink", false)

    /**
     * Enable shader log output
     */
    val dumpShaders = boolSetting("dumpShaders", false)

    //Game
    /**
     * Version isolation
     */
    val versionIsolation = boolSetting("versionIsolation", true)

    /**
     * Do not check game integrity
     */
    val skipGameIntegrityCheck = boolSetting("skipGameIntegrityCheck", false)

    /**
     * Version custom information
     */
    val versionCustomInfo = stringSetting("versionCustomInfo", "${BuildKeys.LAUNCHER_IDENTIFIER}[zl_version]")

    /**
     * The launcher's Java runtime
     */
    val javaRuntime = stringSetting("javaRuntime", "")

    /**
     * Automatically select a Java runtime
     */
    val autoPickJavaRuntime = boolSetting("autoPickJavaRuntime", true)

    /**
     * Memory allocation size for the game
     */
    val ramAllocation = intSetting("ramAllocation", null, min = 256)

    /**
     * Custom JVM launch arguments
     */
    val jvmArgs = stringSetting("jvmArgs", "")

    /**
     * List of disabled native library plugins
     */
    val disableNativeLibPlugins = stringListSetting("nativeLibPlugins", emptyList())

    /**
     * Automatically show the log when launching a game, until the game starts rendering
     */
    val showLogAutomatic = boolSetting("showLogAutomatic", false)

    /**
     * Log font size
     */
    val logTextSize = intSetting("logTextSize", 15, 5..20)

    /**
     * Log buffer flush interval
     */
    val logBufferFlushInterval = intSetting("logBufferFlushInterval", 200, 100..1000)

    //Control
    /**
     * Physical mouse control
     */
    val physicalMouseMode = boolSetting("physicalMouseMode", true)

    /**
     * The key value; pressing it summons the IME
     */
    val physicalKeyImeCode = intSetting("physicalKeyImeCode", null)

    /**
     * Hides the virtual mouse
     */
    val hideMouse = boolSetting("hideMouse", false)

    /**
     * Virtual mouse size (Dp)
     */
    val mouseSize = intSetting("mouseSize", 24, 5..50)

    /**
     * Virtual mouse arrow hotspot coordinates
     */
    val arrowMouseHotspot = parcelableSetting("arrowMouseHotspot", LEFT_TOP_HOTSPOT)

    /**
     * Virtual mouse link-select hotspot coordinates
     */
    val linkMouseHotspot = parcelableSetting("linkMouseHotspot", CursorHotspot(xPercent = 23, yPercent = 0))

    /**
     * Virtual mouse input-select hotspot coordinates
     */
    val iBeamMouseHotspot = parcelableSetting("iBeamMouseHotspot", CENTER_HOTSPOT)

    /**
     * Virtual mouse cross hotspot coordinates
     */
    val crossHairMouseHotspot = parcelableSetting("crossHairMouseHotspot", CENTER_HOTSPOT)

    /**
     * Virtual mouse vertical-resize hotspot coordinates
     */
    val resizeNSMouseHotspot = parcelableSetting("resizeNSMouseHotspot", CENTER_HOTSPOT)

    /**
     * Virtual mouse horizontal-resize hotspot coordinates
     */
    val resizeEWMouseHotspot = parcelableSetting("resizeEWMouseHotspot", CENTER_HOTSPOT)

    /**
     * Virtual mouse all-directions-resize hotspot coordinates
     */
    val resizeAllMouseHotspot = parcelableSetting("resizeAllMouseHotspot", CENTER_HOTSPOT)

    /**
     * Virtual mouse not-allowed hotspot coordinates
     */
    val notAllowedMouseHotspot = parcelableSetting("notAllowedMouseHotspot", CENTER_HOTSPOT)

    /**
     * Virtual mouse sensitivity
     */
    val cursorSensitivity = intSetting("cursorSensitivity", 100, 25..300)

    /**
     * Captured-pointer move sensitivity
     */
    val mouseCaptureSensitivity = intSetting("mouseCaptureSensitivity", 100, 25..300)

    /**
     * Virtual mouse control mode
     */
    val mouseControlMode = enumSetting("mouseControlMode", MouseControlMode.SLIDE)

    /**
     * Mouse-control long-press delay
     */
    val mouseLongPressDelay = intSetting("mouseLongPressDelay", 300, 100..1000)

    /**
     * Whether virtual mouse click actions are enabled
     */
    val enableMouseClick = boolSetting("enableMouseClick", true)

    /**
     * Whether gamepad control is enabled
     */
    val gamepadControl = boolSetting("gamepadControl", true)

    /**
     * Whether auto-summoning the IME is allowed under SDL
     */
    val sdlAutoShowIme = boolSetting("sdlAutoShowIme", true)

    /**
     * Gamepad input mode (map virtual keys / SDL passthrough)
     */
    val gamepadInputMode = enumSetting("gamepadInputMode", GamepadInputMode.Mapped)

    /**
     * Whether the gamepad input mode prompt has been answered
     */
    val gamepadInputModePrompted = boolSetting("gamepadInputModePrompted", false)

    /**
     * Joystick deadzone scaling
     */
    val gamepadDeadZoneScale = intSetting("gamepadDeadZoneScale", 100, 50..200)

    /**
     * Gamepad mapping config
     */
    val gamepadMappingConfig = stringSetting("gamepadMappingConfig", "default")

    /**
     * Joystick control mode
     */
    val joystickControlMode = enumSetting("joystickControlMode", JoystickMode.LeftMovement)

    /**
     * Gamepad joystick sensitivity when steering the mouse cursor
     */
    val gamepadCursorSensitivity = intSetting("gamepadCursorSensitivity", 100, 25..300)

    /**
     * Gamepad joystick sensitivity when steering the game camera
     */
    val gamepadCameraSensitivity = intSetting("gamepadCameraSensitivity", 100, 25..300)

    /**
     * Gesture control
     */
    val gestureControl = boolSetting("gestureControl", false)

    /**
     * The mouse button fires on a gesture-control tap
     */
    val gestureTapMouseAction = enumSetting("gestureTapMouseAction", GestureActionType.MOUSE_RIGHT)

    /**
     * The mouse button fires on a gesture-control long-press
     */
    val gestureLongPressMouseAction = enumSetting("gestureLongPressMouseAction", GestureActionType.MOUSE_LEFT)

    /**
     * Gesture-control long-press delay
     */
    val gestureLongPressDelay = intSetting("gestureLongPressDelay", 300, 100..1000)

    /**
     * Gyroscope control
     */
    val gyroscopeControl = boolSetting("gyroscopeControl", false)

    /**
     * Gyroscope control sensitivity
     */
    val gyroscopeSensitivity = intSetting("gyroscopeSensitivity", 100, 25..300)

    /**
     * Gyroscope sampling rate
     */
    val gyroscopeSampleRate = intSetting("gyroscopeSampleRate", 16, 5..50)

    /**
     * Gyroscope value smoothing
     */
    val gyroscopeSmoothing = boolSetting("gyroscopeSmoothing", true)

    /**
     * Window size for gyroscope smoothing
     */
    val gyroscopeSmoothingWindow = intSetting("gyroscopeSmoothingWindow", 4, 2..10)

    /**
     * Invert the X axis
     */
    val gyroscopeInvertX = boolSetting("gyroscopeInvertX", false)

    /**
     * Invert the Y axis
     */
    val gyroscopeInvertY = boolSetting("gyroscopeInvertY", false)

    //Launcher
    /**
     * Color theme hue
     * Android 12+ dynamic theme color by default
     */
    val launcherColorTheme = enumSetting(
        "launcherColorTheme",
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ColorThemeType.DYNAMIC
        else ColorThemeType.EMBERMIRE
    )

    /**
     * Custom color theme hue
     */
    val launcherCustomColor = intSetting("launcherCustomColor", Color.Blue.toArgb())

    /**
     * Custom color palette style
     */
    val launcherCustomPaletteStyle = enumSetting("launcherCustomPaletteStyle", PaletteStyle.TonalSpot)

    /**
     * Launcher UI dark theme
     */
    val launcherDarkMode = enumSetting("launcherDarkMode", DarkMode.FollowSystem)

    /**
     * Launcher language
     */
    val launcherLanguage = enumSetting("launcherLanguage", AppLanguage.ENGLISH)

    /**
     * Launcher partial-screen fullscreen
     */
    val launcherFullScreen = boolSetting("launcherFullScreen", true)

    /**
     * Access Android/data and other restricted dirs via Shizuku with ADB privileges
     */
    val shizukuEnabled = boolSetting("shizukuEnabled", false)

    /**
     * Prefer Shizuku over SAF directory grants when accessing Android/data
     */
    val shizukuPreferOverSaf = boolSetting("shizukuPreferOverSaf", true)

    /**
     * Persistent holiday easter-egg effects
     */
    val launcherFestivalEffects = boolSetting("launcherFestivalEffects", true)

    /**
     * Animation speed factor
     */
    val launcherAnimateSpeed = intSetting("launcherAnimateSpeed", 5, 0..10)

    /**
     * Animation intensity
     */
    val launcherAnimateExtent = intSetting("launcherAnimateExtent", 5, 0..10)

    /**
     * Launcher page transition animation type
     */
    val launcherSwapAnimateType = enumSetting("launcherSwapAnimateType", TransitionAnimationType.JELLY_BOUNCE)

    /**
     * Analyze the game log after a crash and show probable causes and fixes
     * on the error screen
     */
    val crashAnalyzer = boolSetting("crashAnalyzer", true)

    /**
     * Built-in screen recorder button on the in-game overlay
     */
    val screenRecorder = boolSetting("screenRecorder", false)

    /**
     * Hide the on-screen touch controls while the built-in recorder captures,
     * so they do not appear in the resulting video
     */
    val recorderHideControls = boolSetting("recorderHideControls", true)

    /**
     * Which side the home action menu docks to
     */
    val launcherActionMenuSide = enumSetting("launcherActionMenuSide", ActionMenuSide.END)

    /**
     * Launcher background element opacity
     */
    val launcherBackgroundOpacity = intSetting("launcherBackgroundOpacity", 80, 20..100)

    /**
     * Launcher video background volume
     */
    val videoBackgroundVolume = intSetting("videoBackgroundVolume", 0, 0..100)

    /**
     * Launcher background blur effect
     */
    val backgroundBlur = intSetting("backgroundBlur", 0, 0..40)

    /**
     * Launcher background blur type
     */
    val backgroundBlurType = enumSetting("backgroundBlurType", BackgroundBlur.Background)

    /**
     * The version the user chose to skip at the last update check
     */
    val lastIgnoredVersion = intSetting("lastIgnoredVersion", null)

    /**
     * Launcher log retention days
     */
    val launcherLogRetentionDays = intSetting("launcherLogRetentionDays", 7, 1..14)

    /**
     * Game content download source
     */
    val gameDownloadSource = enumSetting(
        "gameDownloadSource",
        MirrorSourceType.AUTO,
        MirrorSourceType.LEGACY_NAMES
    )

    /**
     * Resource platform download source
     */
    val assetPlatformSource = enumSetting(
        "assetPlatformSource",
        MirrorSourceType.AUTO,
        MirrorSourceType.LEGACY_NAMES
    )

    //Control
    /**
     * Global default control layout file name
     */
    val controlLayout = stringSetting("controlLayout", "")

    //Other
    /**
     * Currently selected account
     */
    val currentAccount = stringSetting("currentAccount", "")

    /**
     * Currently selected game directoryid
     */
    val currentGamePathId = stringSetting("currentGamePathId", GamePathManager.DEFAULT_ID)

    /**
     * Whether the launcher task menu is expanded
     */
    val launcherTaskMenuExpanded = boolSetting("launcherTaskMenuExpanded", true)

    /**
     * Show FPS on the in-game menu overlay
     */
    val showFPS = boolSetting("showFPS", true)

    /**
     * Show memory on the in-game menu overlay
     */
    val showMemory = boolSetting("showMemory", false)

    /**
     * Show the menu overlay on the game surface
     */
    val showMenuBall = boolSetting("showMenuBall", true)

    /**
     * In-game menu overlay position
     */
    val menuBallPos = offsetSetting("menuBallPos", Offset.Zero)

    /**
     * In-game menu overlay opacity
     */
    val menuBallOpacity = intSetting("menuBallOpacity", 100, 20..100)

    /**
     * Hotbar hitbox computation rule
     */
    val hotbarRule = enumSetting("hotbarRule", HotbarRule.Auto)

    /**
     * Hotbar width percentage
     */
    val hotbarWidth = intSetting("hotbarWidth", 500, 0..1000)

    /**
     * Hotbar height percentage
     */
    val hotbarHeight = intSetting("hotbarHeight", 100, 0..1000)

    /**
     * Hotbar double-tap swaps with the off-hand
     */
    val hotbarDoubleClick = boolSetting("hotbarDoubleClick", true)

    /**
     * Hotbar long-press drops the selected item
     */
    val hotbarLongClick = boolSetting("hotbarLongClick", true)

    /**
     * Hotbar long-press repeat trigger delay
     */
    val hotbarLongClickDelay = intSetting("hotbarLongClickDelay", 300, 100..1000)

    /**
     * Overall opacity of the in-game control layout
     */
    val controlsOpacity = intSetting("controlsOpacity", 100, 0..100)

    /**
     * Control layout editor: whether widget snapping is enabled
     */
    val editorEnableWidgetSnap = boolSetting("editorEnableWidgetSnap", true)

    /**
     * Control layout editor: whether snapping spans all widget layers
     */
    val editorSnapInAllLayers = boolSetting("editorSnapInAllLayers", false)

    /**
     * Control layout editor: widget snap mode
     */
    val editorWidgetSnapMode = enumSetting("editorWidgetSnapMode", SnapMode.FullScreen)

    /**
     * Whether Terracotta multiplayer is enabled
     */
    val enableTerracotta = boolSetting("enableTerracotta", false)

    /**
     * Whether to use a custom EasyTier server node
     */
    val enableTerracottaNodes = boolSetting("enableTerracottaNodes", false)

    /**
     * Terracotta: custom EasyTier server node
     */
    val terracottaNodes = stringSetting("terracottaNodes", "")

    /**
     * Terracotta announcement version number
     */
    val terracottaNoticeVer = intSetting("terracottaNoticeVer", -1)

    /**
     * Timestamp of the last update check
     */
    val lastUpgradeCheck = longSetting("lastUpgradeCheck", 0L)

    /**
     * How many times the player has closed the game
     */
    val finishedGame = intSetting("finishedGame", 0)

    /**
     * Whether opening the launcher shows the sponsorship dialog at given game-run counts
     */
    val showSponsorship = boolSetting("showSponsorship", true)

    /**
     * Initial search platform when searching mods
     */
    val searchModPlatform = enumSetting("searchModPlatform", Platform.CURSEFORGE)

    /**
     * Initial search platform when searching packs
     */
    val searchModpackPlatform = enumSetting("searchModpackPlatform", Platform.CURSEFORGE)

    /**
     * Initial search platform when searching resource packs
     */
    val searchResourcePackPlatform = enumSetting("searchResourcePackPlatform", Platform.CURSEFORGE)

    /**
     * Initial search platform when searching shaders
     */
    val searchShadersPlatform = enumSetting("searchShadersPlatform", Platform.CURSEFORGE)
}