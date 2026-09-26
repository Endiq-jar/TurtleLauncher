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

package com.endiq.turtlelauncher.utils.platform

import android.app.ActivityManager
import android.content.Context
import androidx.annotation.WorkerThread
import com.endiq.turtlelauncher.utils.device.Architecture
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round

private const val BYTES_PER_MB = 1024L * 1024

private inline val Context.activityManager: ActivityManager
    get() = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

private fun getMemoryInfo(context: Context): ActivityManager.MemoryInfo {
    return ActivityManager.MemoryInfo().apply {
        context.activityManager.getMemoryInfo(this)
    }
}

/**
 * Gets total system memory (bytes)
 */
@WorkerThread
fun getTotalMemory(context: Context) = getMemoryInfo(context).totalMem

/**
 * Gets used memory (bytes)
 */
@WorkerThread
fun getUsedMemory(context: Context): Long {
    val info = getMemoryInfo(context)
    return info.totalMem - info.availMem
}

/**
 * Gets currently available memory (bytes)
 */
@WorkerThread
fun getFreeMemory(context: Context) = getMemoryInfo(context).availMem

/**
 * Gets the max settable memory value, reserving some for the system
 */
@WorkerThread
fun getMaxMemoryForSettings(context: Context): Int {
    val deviceRam = getTotalMemory(context).bytesToMB()
    val maxRam: Int = if (Architecture.is32BitsDevice || deviceRam < 2048) {
        min(1024.0, deviceRam).toInt()
    } else {
        //To have a minimum for the device to breathe
        (deviceRam - (if (deviceRam < 3064) 800 else 1024)).toInt()
    }
    return maxRam
}

/**
 * Converts to MB
 */
fun Long.bytesToMB(decimals: Int = 2, roundDown: Boolean = false): Double {
    val megaBytes = this.toDouble() / BYTES_PER_MB
    return if (decimals == 0) {
        if (roundDown) floor(megaBytes) else round(megaBytes)
    } else {
        val roundingMode = if (roundDown) RoundingMode.DOWN else RoundingMode.HALF_UP
        BigDecimal(megaBytes).setScale(decimals, roundingMode).toDouble()
    }
}