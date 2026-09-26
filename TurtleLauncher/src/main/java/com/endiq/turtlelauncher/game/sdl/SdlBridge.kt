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

package com.endiq.turtlelauncher.game.sdl

import android.app.Activity
import android.view.Surface
import android.view.ViewGroup
import androidx.annotation.Keep
import androidx.annotation.MainThread
import com.endiq.turtlelauncher.setting.AllSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.libsdl.app.SDL
import org.libsdl.app.SDLActivity
import org.libsdl.app.SDLSurface
import org.lwjgl.glfw.CallbackBridge
import java.lang.ref.WeakReference

/**
 * Owns the SDL integration state shared by the launcher and game JVM.
 */
@Keep
object SdlBridge {
    private val _enabled = MutableStateFlow(false)
    val enabled = _enabled.asStateFlow()

    private val _composeFocus = MutableStateFlow(0)
    val composeFocus = _composeFocus.asStateFlow()

    private var activityRef: WeakReference<Activity>? = null
    private var layoutRef: WeakReference<ViewGroup>? = null
    private var currentSurface: Surface? = null


    /** Source of the currently registered Surface */
    private var currentSource: Any? = null

    /** Increments each time a new Surface is registered, for lifecycle observation */
    private var surfaceGeneration = 0L
    private var jniReady = false
    private var sdlInitialized = false

    @JvmStatic
    @Synchronized
    fun setupJNI(): Boolean {
        if (jniReady) {
            return true
        }
        SDL.setupJNI()
        jniReady = true
        return true
    }

    @JvmStatic
    @Synchronized
    fun markSdlInitialized(): Boolean {
        if (sdlInitialized) return false
        sdlInitialized = true
        return true
    }

    @JvmStatic
    @Synchronized
    fun clearSdlInitialized() {
        sdlInitialized = false
    }

    @JvmStatic
    @Volatile
    var sdlEnabled: Boolean = false
        set(value) {
            field = value
            _enabled.value = value
        }

    /**
     * Whether the launcher responds when SDL requests the input method
     */
    @JvmStatic
    fun getSdlImeAutoShowEnabled(): Boolean = AllSettings.sdlAutoShowIme.state

    @JvmStatic
    @MainThread
    fun prepareSurface(activity: Activity, surface: Surface, layout: ViewGroup?, source: Any? = null) {
        activityRef = WeakReference(activity)
        layoutRef = WeakReference(layout)
        currentSurface = surface
        currentSource = source
        surfaceGeneration++

        if (SDLActivity.getSDLSurface() == null) {
            SDL.initialize()
            SDL.setContext(activity)
            SDLActivity.externalInitialize(SDLSurface(activity), layout, surface)
        } else {
            SDLSurface.setNativeSurface(surface)
        }
    }

    @JvmStatic
    @MainThread
    fun registerSurface(activity: Activity, surface: Surface, layout: ViewGroup?) {
        activityRef = WeakReference(activity)
        layoutRef = WeakReference(layout)
        currentSurface = surface
    }

    @JvmStatic
    fun requestComposeFocus() {
        _composeFocus.update { it + 1 }
    }

    @JvmStatic
    @MainThread
    fun beginSurfaceDestroy(source: Any?, surface: Surface?): Boolean {
        return source != null && currentSource === source && surface != null && currentSurface === surface
    }

    @JvmStatic
    @MainThread
    fun unregisterSurface(surface: Surface?) {
        if (surface != null && currentSurface === surface) {
            currentSurface = null
            currentSource = null
        }
    }

    @JvmStatic
    @MainThread
    @Synchronized
    fun reset() {
        currentSurface = null
        currentSource = null
        surfaceGeneration = 0L
        activityRef = null
        layoutRef = null
        jniReady = false
        sdlInitialized = false
        sdlEnabled = false
        CallbackBridge.clearSdlBridgeState()
        SDLSurface.clearNativeSurface()
        SDL.initialize()
    }

    @JvmStatic
    external fun initializeControllerSubsystems()

    /**
     * Whether the game runs on the SDL render path (SDL window created, MC 26.3+)
     * Returns false when only the gamepad subsystem uses SDL (e.g. MC 26.2 with Controlify),
     * since game input still flows through the GLFW bridge and keyboard switching shouldn't be delegated to the SDL input channel
     */
    @JvmStatic
    external fun isSdlRenderActive(): Boolean

    /**
     * Activates/deactivates the native-side SDL text input channel
     * When a mod (with a self-drawn input UI) closed the game-side channel, an explicit IME request must activate the launcher's own,
     * otherwise IME-committed text gets dropped at the native layer
     */
    @JvmStatic
    external fun setNativeTextInputActive(active: Boolean): Boolean
}