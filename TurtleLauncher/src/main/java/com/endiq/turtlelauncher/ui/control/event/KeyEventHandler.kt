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

package com.endiq.turtlelauncher.ui.control.event

import java.util.concurrent.ConcurrentHashMap

/**
 * Handles the launcher key event identifiers
 */
@Deprecated("Suspected of scrambling key events and causing erratic in-game input; temporarily removed, see #894")
class KeyEventHandler(
    private val handle: (key: String, pressed: Boolean) -> Unit
) {
    /**
     * Total number of keys currently held down (the same key may be pressed multiple times simultaneously)
     */
    private val keyEvents = ConcurrentHashMap<String, Int>()

    /**
     * Key pressed
     */
    fun pressKey(key: String) {
        var shouldPress = false
        keyEvents.compute(key) { _, old ->
            val oldCount = old ?: 0
            shouldPress = oldCount == 0
            oldCount + 1
        }
        if (shouldPress) {
            handle(key, true)
        }
    }

    fun releaseKey(key: String) {
        var shouldRelease = false
        keyEvents.compute(key) { _, old ->
            when {
                old == null || old <= 0 -> {
                    shouldRelease = false
                    null
                }
                old == 1 -> {
                    shouldRelease = true
                    null
                }
                else -> {
                    shouldRelease = false
                    old - 1
                }
            }
        }
        if (shouldRelease) {
            handle(key, false)
        }
    }

    fun clearEvent() {
        val allKeys = keyEvents.keys.toSet()
        keyEvents.clear()
        allKeys.forEach {
            handle(it, false)
        }
    }
}