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

import androidx.annotation.Keep
import com.endiq.turtlelauncher.ui.AndroidStringText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Log output interface for task flows, holding the log lines to display
 * @param title the log title
 * @param maxLines log line cap; the oldest lines are dropped beyond it
 */
@Keep
class TaskLogOutput(
    val title: AndroidStringText,
    private val maxLines: Int = DEFAULT_MAX_LINES
) {
    private val _lines = MutableStateFlow<List<String>>(emptyList())

    /** Currently displayed log lines */
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private val _active = MutableStateFlow(false)

    /** Whether a log output session is active */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /**
     * Starts a log output session: clears existing content and becomes active
     */
    fun start() {
        _lines.update { emptyList() }
        _active.update { true }
    }

    /**
     * Stops the log output session, keeping displayed content
     */
    fun stop() {
        _active.update { false }
    }

    /**
     * Appends one log line incrementally
     */
    fun appendLine(line: String) {
        appendLines(listOf(line))
    }

    /**
     * Appends multiple log lines incrementally
     */
    fun appendLines(lines: List<String>) {
        if (lines.isEmpty()) return
        _lines.update { current -> (current + lines).takeLast(maxLines) }
    }

    companion object {
        /** Default log line cap */
        const val DEFAULT_MAX_LINES = 1000
    }
}

/**
 * Creates and starts a log output session: immediately builds a [TaskLogOutput] and writes it into [holder];
 * when [block] finishes (normally, exceptionally, or cancelled) the session stops and [holder] is cleared
 */
suspend fun <R> withTaskLogOutput(
    holder: MutableStateFlow<TaskLogOutput?>,
    title: AndroidStringText,
    block: suspend (TaskLogOutput) -> R
): R {
    val output = TaskLogOutput(title).also { it.start() }
    holder.value = output
    try {
        return block(output)
    } finally {
        output.stop()
        holder.value = null
    }
}
