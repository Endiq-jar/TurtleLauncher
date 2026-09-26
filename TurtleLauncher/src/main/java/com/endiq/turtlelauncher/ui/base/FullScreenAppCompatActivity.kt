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

package com.endiq.turtlelauncher.ui.base

import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.View.OnSystemUiVisibilityChangeListener
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.annotation.CallSuper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlin.math.abs

abstract class FullScreenAppCompatActivity : AbstractAppCompatActivity() {
    /** Whether the current gesture's raw and local coordinates use different spaces, requiring event reconstruction */
    private var correctTouchCoordinates = false

    /**
     * @return whether to ignore the front-camera region
     */
    protected open fun isIgnoreNotch(): Boolean = true

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyFullscreen()
    }

    @CallSuper
    override fun onPostResume() {
        super.onPostResume()
        applyFullscreen()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN)) {
            return super.dispatchTouchEvent(event)
        }

        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            correctTouchCoordinates = hasCoordinateSpaceMismatch(event)
        } else if (!correctTouchCoordinates &&
            action == MotionEvent.ACTION_POINTER_DOWN && hasCoordinateSpaceMismatch(event)) {
            // The window position may stabilize only after the first touch-down: re-check then.
            correctTouchCoordinates = true
        }

        val handled = if (correctTouchCoordinates) {
            val corrected = event.copyWithConsistentCoordinates()
            try {
                super.dispatchTouchEvent(corrected)
            } finally {
                corrected.recycle()
            }
        } else {
            super.dispatchTouchEvent(event)
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            correctTouchCoordinates = false
        }
        return handled
    }

    /**
     * Detects whether each pointer's raw coordinates depart from "window-local + window screen origin".
     * They are normally equal; OEMs scaling the window without syncing raw coordinates break that.
     */
    private fun hasCoordinateSpaceMismatch(event: MotionEvent): Boolean {
        val origin = IntArray(2)
        window.decorView.getLocationOnScreen(origin)
        if (abs(event.rawX - (event.x + origin[0])) > 1f) return true
        if (abs(event.rawY - (event.y + origin[1])) > 1f) return true
        if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
            // getRawX(index)/getRawY(index) requires Q+; the first pointer already used the no-arg check
            for (index in 1 until event.pointerCount) {
                if (abs(event.getRawX(index) - (event.getX(index) + origin[0])) > 1f) return true
                if (abs(event.getRawY(index) - (event.getY(index) + origin[1])) > 1f) return true
            }
        }
        return false
    }

    /**
     * Rebuilds touch events from "window-local + window screen origin", unifying raw and local spaces.
     */
    private fun MotionEvent.copyWithConsistentCoordinates(): MotionEvent {
        val origin = IntArray(2)
        window.decorView.getLocationOnScreen(origin)
        val properties = Array(pointerCount) { MotionEvent.PointerProperties() }
        val coordinates = Array(pointerCount) { MotionEvent.PointerCoords() }
        for (index in 0 until pointerCount) {
            getPointerProperties(index, properties[index])
            getPointerCoords(index, coordinates[index])
            // obtain-created events always have raw == local;
            // offsetLocation translates local only, leaving raw untouched: pre-shift first
            coordinates[index].x += origin[0]
            coordinates[index].y += origin[1]
        }
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            pointerCount,
            properties,
            coordinates,
            metaState,
            buttonState,
            xPrecision,
            yPrecision,
            deviceId,
            edgeFlags,
            source,
            flags,
        )
    }

    /**
     * Fullscreen / ignore-camera-region code adapted from [Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android/blob/9c83fc6/app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/BaseActivity.java)
     *
     * Note: targetSdk must stay 34; from 35 on, Activities are force-enrolled into enableEdgeToEdge, breaking this
     */
    private fun applyFullscreen() {
        val decorView = window.decorView
        val visibilityChangeListener = OnSystemUiVisibilityChangeListener { visibility: Int ->
            if (!isInMultiWindowMode) {
                if ((visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    decorView.systemUiVisibility = (
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            )
                }
            } else {
                decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
        decorView.setOnSystemUiVisibilityChangeListener(visibilityChangeListener)
        visibilityChangeListener.onSystemUiVisibilityChange(decorView.systemUiVisibility) //call it once since the UI state may not change after the call, so the activity wont become fullscreen

        refreshIgnoreNotch()
    }

    fun refreshIgnoreNotch() {
        if (Build.VERSION.SDK_INT >= VERSION_CODES.P) {
            val mode = if (isIgnoreNotch()) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }

            val params = window.attributes

            if (params.layoutInDisplayCutoutMode != mode) {
                params.layoutInDisplayCutoutMode = mode

                window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
                window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)

                window.attributes = params
            }
        }
    }
}

/**
 * Watches fullscreen setting changes and updates the notch mode in real time
 */
@Composable
fun ObserveFullScreenSetting(fullScreen: Boolean) {
    val activity = LocalActivity.current as? FullScreenAppCompatActivity ?: return
    LaunchedEffect(fullScreen) {
        activity.refreshIgnoreNotch()
    }
}
