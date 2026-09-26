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

import com.endiq.turtlelauncher.ui.AndroidStringText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class Task private constructor(
    val id: String,
    val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    val task: suspend CoroutineScope.(Task) -> Unit,
    val onError: suspend (Throwable) -> Unit = {},
    val onFinally: () -> Unit = {},
    val onCancel: () -> Unit = {}
) {
    private val _stage = MutableStateFlow(TaskStage.PREPARING)
    /**
     * Task phase (TaskSystem may not use it; mainly for GameInstaller)
     */
    val stage = _stage.asStateFlow()

    private val _progress = MutableStateFlow(-1f)
    /** Task progress state */
    val progress = _progress.asStateFlow()

    private val _message = MutableStateFlow<AndroidStringText?>(null)
    /** Task message state */
    val message = _message.asStateFlow()

    private val _rateBytesPerSec = MutableStateFlow<Long?>(null)
    /** Current rate in bytes */
    val rateBytesPerSec = _rateBytesPerSec.asStateFlow()

    /**
     * Updates the task phase
     */
    fun updateStage(state: TaskStage) {
        this._stage.update { state }
    }

    /**
     * Updates the progress, automatically handling NaN/isInfinite edge cases
     * @param percentage progress percentage; -1f means indeterminate
     */
    fun updateProgress(percentage: Float) {
        this._progress.update {
            (percentage.takeIf { it.isFinite() } ?: 0f).coerceIn(-1f, 1f)
        }
    }

    /**
     * Updates the task description message
     * @param text the task description message
     */
    fun updateMessage(text: AndroidStringText?) {
        this._message.update { text }
    }

    /**
     * Updates the task bit rate
     */
    fun updateSpeed(bytes: Long) {
        this._rateBytesPerSec.update { bytes.takeIf { it >= 0L } }
    }

    /**
     * Clears the task bit rate
     */
    fun clearSpeed() {
        this._rateBytesPerSec.update { null }
    }

    override fun equals(other: Any?): Boolean = other is Task && other.id == this.id

    override fun hashCode(): Int = id.hashCode()

    companion object {
        fun runTask(
            id: String? = null,
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
            task: suspend CoroutineScope.(Task) -> Unit,
            onError: suspend (Throwable) -> Unit = {},
            onFinally: () -> Unit = {},
            onCancel: () -> Unit = {}
        ): Task =
            Task(
                id = id ?: getRandomID(),
                dispatcher = dispatcher,
                task = task,
                onError = onError,
                onFinally = onFinally,
                onCancel = onCancel
            )

        private fun getRandomID(): String = UUID.randomUUID().toString()
    }
}