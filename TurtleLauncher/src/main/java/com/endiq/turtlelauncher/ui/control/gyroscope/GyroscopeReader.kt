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

package com.endiq.turtlelauncher.ui.control.gyroscope

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

/**
 * Checks whether the gyroscope is usable; some devices lack a gyroscope sensor
 */
fun isGyroscopeAvailable(context: Context): Boolean {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val gyroscopeSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    return gyroscopeSensor != null
}

/**
 * Reads gyroscope sensor data
 *
 * @param xEvent x-axis offset callback
 * @param yEvent y-axis offset callback
 * @param zEvent z-axis offset callback
 * @param sampleRate the sampling rate
 * @param smoothing whether to smooth data
 * @param smoothingWindow smoothing window size; larger = smoother
 * @param threshold low-pass threshold, suppressing tiny jitters
 * @param sensitivity multiplier, the scaling amplification factor
 */
@Composable
fun GyroscopeReader(
    xEvent: (Float) -> Unit = {},
    yEvent: (Float) -> Unit = {},
    zEvent: (Float) -> Unit = {},
    sampleRate: Int = SensorManager.SENSOR_DELAY_GAME,
    smoothing: Boolean = true,
    smoothingWindow: Int = 4,
    threshold: Float = 0.02f,
    sensitivity: Float = 1f
) {
    key(
        xEvent, yEvent, zEvent,
        sampleRate, smoothing, smoothingWindow, threshold, sensitivity
    ) {
        GyroscopeReaderMain(
            xEvent = xEvent,
            yEvent = yEvent,
            zEvent = zEvent,
            sampleRate = sampleRate,
            smoothing = smoothing,
            smoothingWindow = smoothingWindow,
            threshold = threshold,
            sensitivity = sensitivity
        )
    }
}

@Composable
private fun GyroscopeReaderMain(
    xEvent: (Float) -> Unit = {},
    yEvent: (Float) -> Unit = {},
    zEvent: (Float) -> Unit = {},
    sampleRate: Int = SensorManager.SENSOR_DELAY_GAME,
    smoothing: Boolean = true,
    smoothingWindow: Int = 4,
    threshold: Float = 0.02f,
    sensitivity: Float = 1f
) {
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    //Gyroscope sensor
    val gyroscopeSensor = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    val angleBuffer = remember { Array(smoothingWindow) { FloatArray(3) } }
    var historyIndex = -1
    var xTotal = 0f
    var yTotal = 0f
    var zTotal = 0f

    fun resetBuffer() {
        historyIndex = -1
        xTotal = 0f
        yTotal = 0f
        zTotal = 0f
        angleBuffer.forEach { it.fill(0f) }
    }

    DisposableEffect(Unit) {
        resetBuffer()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                var x = event.values[0] * sensitivity
                var y = event.values[1] * sensitivity
                var z = event.values[2] * sensitivity

                if (smoothing) {
                    historyIndex++
                    if (historyIndex >= angleBuffer.size) historyIndex = 0

                    xTotal -= angleBuffer[historyIndex][0]
                    yTotal -= angleBuffer[historyIndex][1]
                    zTotal -= angleBuffer[historyIndex][2]

                    angleBuffer[historyIndex][0] = x
                    angleBuffer[historyIndex][1] = y
                    angleBuffer[historyIndex][2] = z

                    xTotal += x
                    yTotal += y
                    zTotal += z

                    x = xTotal / angleBuffer.size
                    y = yTotal / angleBuffer.size
                    z = zTotal / angleBuffer.size
                }

                //Threshold filtering to ignore tiny jitters
                if (abs(x) > threshold) xEvent(x)
                if (abs(y) > threshold) yEvent(y)
                if (abs(z) > threshold) zEvent(z)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, gyroscopeSensor, sampleRate)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }
}
