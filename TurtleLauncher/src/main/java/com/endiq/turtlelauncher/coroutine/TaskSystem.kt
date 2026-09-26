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

import com.endiq.turtlelauncher.keepalive.TaskKeepAlive
import com.endiq.turtlelauncher.utils.network.isInterruptedIOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object TaskSystem {
    private val scope = CoroutineScope(Dispatchers.Default)
    private val _tasksFlow: MutableStateFlow<List<Task>> = MutableStateFlow(emptyList())
    val tasksFlow = _tasksFlow.asStateFlow()

    private val allJobs = ConcurrentHashMap<String, Job>()
    private val allListeners = ConcurrentHashMap<String, () -> Unit>()

    /**
     * Submits and runs a task immediately
     */
    fun submitTask(task: Task) {
        if (containsTask(task)) return
        addTask(task)
        //Hold keep-alive so the system cannot interrupt the task in the background
        TaskKeepAlive.acquire()

        allJobs[task.id] = scope.launch(task.dispatcher) {
            try {
                task.updateStage(TaskStage.RUNNING)
                task.task(this@launch, task)
                task.updateStage(TaskStage.COMPLETED)
            } catch (th: Throwable) {
                if (th is CancellationException || th.isInterruptedIOException()) return@launch
                task.onError(th)
            } finally {
                task.onFinally()
            }
        }.also { job ->
            job.invokeOnCompletion {
                try {
                    onTaskEnded(task)
                } finally {
                    //Ensure keep-alive is always released so a failing task cannot jam the foreground service
                    TaskKeepAlive.release()
                }
            }
        }
    }

    /**
     * Submits and runs a task immediately
     * If the task already exists it is ignored, but its listener is replaced
     * @param onEnded listener called when the task ends
     */
    fun submitTask(task: Task, onEnded: () -> Unit) {
        putTaskEndedListener(taskId = task.id, onEnded = onEnded)
        submitTask(task)
    }

    /**
     * Adds a task-finish listener, invoked when every flow of the task ends
     * Exceptions thrown while running the task listener are ignored
     * @param taskId the task the listener applies to
     */
    fun putTaskEndedListener(taskId: String, onEnded: () -> Unit) {
        allListeners[taskId] = onEnded
    }

    /**
     * Removes a task-finish listener
     */
    fun removeTaskEndedListener(taskId: String) =
        allListeners.remove(taskId)

    private fun onTaskEnded(task: Task) {
        removeTask(task)
        runCatching {
            allListeners[task.id]?.invoke()
        }
        allJobs.remove(task.id)
        removeTaskEndedListener(task.id)
    }

    private fun onTaskCanceled(task: Task) {
        scope.launch {
            task.onCancel()
        }
        onTaskEnded(task)
    }

    /**
     * Cancels a task
     */
    fun cancelTask(task: Task) {
        allJobs[task.id]?.cancel()
        onTaskCanceled(task)
    }

    /**
     * Cancels a task
     */
    fun cancelTask(id: String) {
        allJobs[id]?.cancel()
        _tasksFlow.value.find { it.id == id }?.let { onTaskCanceled(it) }
    }

    /**
     * @return whether it contains the task
     */
    fun containsTask(task: Task) = _tasksFlow.value.contains(task)

    /**
     * @return whether it contains the task
     */
    fun containsTask(id: String) = _tasksFlow.value.any { it.id == id }

    /**
     * Stops all tasks
     */
    fun stopAll() {
        scope.cancel()
        _tasksFlow.update { emptyList() }
        allJobs.clear()
        allListeners.clear()
        TaskKeepAlive.reset()
    }

    private fun addTask(task: Task) {
        _tasksFlow.update { it + task }
    }

    private fun removeTask(task: Task) {
        _tasksFlow.update { it - task }
    }
}