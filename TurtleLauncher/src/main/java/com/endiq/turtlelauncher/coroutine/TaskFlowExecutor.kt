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

import com.endiq.turtlelauncher.coroutine.TaskFlowExecutor.TaskPhase
import com.endiq.turtlelauncher.keepalive.TaskKeepAlive
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.network.isInterruptedIOException
import com.endiq.turtlelauncher.utils.string.getMessageOrToString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "TaskFlowExecutor"

/**
 * Dynamic task flow executor that runs phases in order
 */
class TaskFlowExecutor(
    private val scope: CoroutineScope
) {
    /**
     * A task flow phase, holding all tasks of one phase
     */
    data class TaskPhase(
        val tasks: List<TitledTask>,
        val onComplete: (suspend () -> Unit)? = null
    )

    /**
     * All current task flow phases
     */
    private val phases: MutableList<TaskPhase> = mutableListOf()

    private val _tasksFlow: MutableStateFlow<List<TitledTask>> = MutableStateFlow(emptyList())
    val tasksFlow = _tasksFlow.asStateFlow()

    private var job: Job? = null
    /** Job of the currently running task */
    private var currentTaskJob: Job? = null
    /** Current task flow phase index */
    private var currentPhaseIndex: Int = -1

    /**
     * Returns the next phase
     */
    private fun getNextPhase(): TaskPhase? {
        currentPhaseIndex++
        return phases.getOrNull(currentPhaseIndex)
    }

    /**
     * Appends a phase to the end
     */
    fun addPhase(phase: TaskPhase) {
        phases.add(phase)
    }

    /**
     * Appends a phase list to the end
     */
    fun addPhases(phases: List<TaskPhase>) {
        this.phases.addAll(phases)
    }

    /**
     * Synchronously runs a multi-phase task flow
     */
    suspend fun executePhases(
        onComplete: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
        onCancel: () -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        currentPhaseIndex = -1

        while (true) {
            try {
                ensureActive()
                val phase = getNextPhase() ?: break
                //Update the current task list
                _tasksFlow.update { phase.tasks }

                //Run all tasks of the phase
                for (task in phase.tasks) {
                    ensureActive()
                    task.task.updateStage(TaskStage.RUNNING)

                    //Give each task its own Job so it can be cancelled instantly
                    //SupervisorJob keeps child-coroutine failures from affecting other tasks
                    val parentJob = coroutineContext[Job]
                    val taskJob = SupervisorJob(parentJob)
                    currentTaskJob = taskJob
                    
                    try {
                        withContext(task.task.dispatcher + taskJob) {
                            //coroutineScope keeps all child coroutines of the task inside this scope
                            //Cancelling taskJob cancels every child coroutine in the coroutineScope
                            coroutineScope {
                                try {
                                    //Pass the current coroutineScope to the task so its launches stay in this scope
                                    task.task.task(this@coroutineScope, task.task)
                                } catch (e: CancellationException) {
                                    task.task.onCancel()
                                    throw e
                                } catch (e: Throwable) {
                                    task.task.onError(e)
                                    throw e
                                } finally {
                                    task.task.onFinally()
                                }
                            }
                        }
                        task.task.updateStage(TaskStage.COMPLETED)
                    } finally {
                        //Ensure taskJob is cancelled and cleaned up whether the task succeeds or fails
                        taskJob.cancel()
                        currentTaskJob = null
                    }
                }

                //Run the phase completion callback
                phase.onComplete?.invoke()
            } catch (th: Throwable) {
                if (th is CancellationException || th.isInterruptedIOException()) {
                    Logger.debug(TAG, "The current task flow has been cancelled. ${th.getMessageOrToString()}")
                    onCancel()
                } else {
                    Logger.warning(TAG, "An exception occurred while executing the task flow.", th)
                    onError(th)
                }
                return@withContext
            }
        }

        onComplete()
    }

    /**
     * Asynchronously runs a multi-phase task flow
     */
    fun executePhasesAsync(
        onStart: suspend () -> Unit = {},
        onComplete: () -> Unit = {},
        onError: (Throwable) -> Unit = {},
        onCancel: () -> Unit = {}
    ) {
        job = scope.launch(Dispatchers.IO) {
            //Hold keep-alive so the system cannot interrupt the flow in the background
            TaskKeepAlive.acquire()
            try {
                onStart()
                executePhases(onComplete, onError, onCancel)
            } finally {
                TaskKeepAlive.release()
            }
        }
    }

    fun isRunning(): Boolean = job != null

    fun cancel() {
        //Cancel the currently running task and all its child coroutines first
        currentTaskJob?.cancel()
        currentTaskJob = null
        
        job?.cancel()
        job = null

        _tasksFlow.update { emptyList() }
        currentPhaseIndex = -1
    }
}

/**
 * Builds a task flow phase
 */
fun buildPhase(
    onComplete: (suspend () -> Unit)? = null,
    builderAction: MutableList<TitledTask>.() -> Unit
): TaskPhase {
    val tasks: List<TitledTask> = buildList(builderAction = builderAction)
    return TaskPhase(
        tasks = tasks,
        onComplete = onComplete
    )
}