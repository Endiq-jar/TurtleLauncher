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

package com.endiq.turtlelauncher.coroutine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/**
 * An extended state container that provides both the old and new values on updates
 * @param initial the initial state, allowed to be `null` so the state can be built gradually during initialization
 */
class MutableTransitionStateFlow<T>(initial: T?) {
    /**
     * Represents one state change (old value → new value)
     */
    data class Transition<T>(
        val old: T?,
        val new: T
    )

    private val internal = MutableStateFlow(initial)
    private var lastValue: T? = initial

    val value
        get () = internal.value

    /**
     * State flow exposed to the UI layer
     */
    val stateFlow: StateFlow<T?> = internal.asStateFlow()

    /**
     * Change flow carrying old and new values;
     * every [set] call emits a [Transition] into it
     */
    val changes: Flow<Transition<T>> = internal
        .filterNotNull()
        .map { new ->
            val transition = Transition(lastValue, new)
            lastValue = new
            transition
        }

    /**
     * Sets a new state value
     * @param value the new state value
     */
    fun set(value: T) {
        internal.value = value
    }
}