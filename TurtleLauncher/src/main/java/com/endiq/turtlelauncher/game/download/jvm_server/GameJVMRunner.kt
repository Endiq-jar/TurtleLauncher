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

package com.endiq.turtlelauncher.game.download.jvm_server

import android.content.Intent
import com.endiq.turtlelauncher.components.jre.Jre
import com.endiq.turtlelauncher.context.GlobalContext
import com.endiq.turtlelauncher.coroutine.TaskLogOutput
import com.endiq.turtlelauncher.notification.NoticeProgress
import com.endiq.turtlelauncher.utils.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

private const val TAG = "GameJVMRunner"

/** How long to wait for the mutex process to exit on its own before killing it by pid */
private val WAIT_BEFORE_FORCE_KILL: Duration = 15.seconds
/** Grace period after the forced kill; if the field still isn't clear, end with a visible error */
private val KILL_GRACE: Duration = 5.seconds
/** Interval between waiting-log prints */
private val WAIT_LOG_INTERVAL: Duration = 2.seconds
/** Total time limit for one JVM run to wait for an exit code */
private val JVM_EXIT_TIMEOUT: Duration = 15.minutes
/** Max retries after a foreground-service start failure */
private const val SERVICE_START_RETRY_MAX = 4
/** Backoff base interval for foreground-service start retries */
private val SERVICE_START_RETRY_DELAY: Duration = 500.milliseconds
/** Cooldown after the mutex process vanishes, before relaunching */
private val POST_PROCESS_EXIT_COOLDOWN: Duration = 1.seconds

/**
 * Runs a simple JVM environment to install the ModLoader, retrying with other Java runtimes when the JVM exits
 * @param logId logging tag
 * @param start callback invoked right when it starts
 * @param logOutput live log output of the JVM install
 */
suspend fun runJvmRetryRuntimes(
    logId: String,
    jvmArgs: String,
    prefixArgs: (Jre) -> String?,
    jre: Jre,
    userHome: String,
    postSummary: String? = null,
    postProgress: NoticeProgress? = null,
    start: () -> Unit = {},
    logOutput: TaskLogOutput? = null
): Unit = withContext(Dispatchers.Default) {
    waitForJvmExclusiveProcessesStopped(logId)

    start()

    val finalArgs = prefixArgs(jre)?.let {
        "$it $jvmArgs"
    } ?: jvmArgs

    val exitCode = startJvmServiceAndWaitExit(
        jvmArgs = finalArgs,
        jreName = jre.jreName,
        userHome = userHome,
        postSummary = postSummary,
        postProgress = postProgress,
        logOutput = logOutput
    )

    if (exitCode != 0) {
        val nextJava: Jre? = when (jre) {
            Jre.JRE_8 -> Jre.JRE_17
            Jre.JRE_17 -> Jre.JRE_21
            else -> null
        }

        nextJava?.let { jre ->
            Logger.info(TAG, "Retry with jre ${jre.name}...")
            runJvmRetryRuntimes(
                logId = logId,
                jvmArgs = jvmArgs,
                prefixArgs = prefixArgs,
                jre = jre,
                userHome = userHome,
                postSummary = postSummary,
                postProgress = postProgress,
                logOutput = logOutput
            )
        } ?: throw JvmCrashException(exitCode)
    }
}

/**
 * Waits for the mutex processes (:jvm, :game) to exit before running
 */
private suspend fun waitForJvmExclusiveProcessesStopped(logId: String) {
    val startNanos = System.nanoTime()
    var lastLog = -WAIT_LOG_INTERVAL
    var forceKilled = false
    var sawBlocking = false

    while (true) {
        val blocking = listBlockingProcesses(GlobalContext)
        if (blocking.isEmpty()) {
            if (!sawBlocking) {
                // If no mutex process was ever seen, no waiting is needed; go ahead
                return
            }
            // Right after waiting out a mutex process, give system_server a moment to handle the death
            delay(POST_PROCESS_EXIT_COOLDOWN)
            if (listBlockingProcesses(GlobalContext).isEmpty()) return
            continue
        }

        sawBlocking = true

        val elapsed = (System.nanoTime() - startNanos).nanoseconds
        when {
            elapsed >= WAIT_BEFORE_FORCE_KILL + KILL_GRACE ->
                throw IOException(
                    "Timed out waiting for exclusive processes to stop: ${blocking.joinToString()}"
                )

            elapsed >= WAIT_BEFORE_FORCE_KILL -> {
                if (!forceKilled) {
                    forceKilled = true
                    Logger.warning(TAG, "$logId Force stopping blocking processes: $blocking")
                }
                stopAllNonMainProcesses(GlobalContext)
            }

            elapsed - lastLog >= WAIT_LOG_INTERVAL -> {
                lastLog = elapsed
                Logger.info(TAG, "$logId Waiting for other processes stop... [$blocking]")
            }
        }
        delay(100L.milliseconds)
    }
}

suspend fun startJvmServiceAndWaitExit(
    jvmArgs: String,
    jreName: String? = null,
    userHome: String? = null,
    postSummary: String? = null,
    postProgress: NoticeProgress? = null,
    logOutput: TaskLogOutput? = null,
): Int = withContext(Dispatchers.IO) {
    val doneSignal = CompletableDeferred<Unit>()

    val tailerJob = logOutput?.let {
        ProcessLogTailer(LATEST_PROCESS_LOG_FILE, it).launch(this)
    }

    try {
        // Start the receiver before launching the service
        // so a JVM exiting instantly doesn't lose its exit code to an unbound socket
        JVMSocketServer.start { receiveMsg ->
            Logger.info(TAG, "receive msg: $receiveMsg, stopping server...")
            if (!doneSignal.isCompleted) {
                doneSignal.complete(Unit)
            }
            JVMSocketServer.stop()
        }

        var attempt = 0
        while (true) {
            try {
                startJvmService(
                    context = GlobalContext,
                    jvmArgs = jvmArgs,
                    userHome = userHome,
                    jreName = jreName,
                    postSummary = postSummary,
                    postProgress = postProgress
                )
                break
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                attempt++
                if (attempt > SERVICE_START_RETRY_MAX) {
                    Logger.error(TAG, "Failed to start JvmService after $attempt attempts", e)
                    throw e
                }
                val retryDelay = SERVICE_START_RETRY_DELAY * (1 shl (attempt - 1))
                Logger.warning(
                    TAG,
                    "Failed to start JvmService (attempt $attempt/$SERVICE_START_RETRY_MAX): " +
                        "${e.message}. Retrying in $retryDelay..."
                )
                delay(retryDelay)
            }
        }

        if (withTimeoutOrNull(JVM_EXIT_TIMEOUT) { doneSignal.await() } == null) {
            // On timeout, stop the service and receiver so the install ends with a visible error instead of hanging forever
            Logger.error(TAG, "Timed out ($JVM_EXIT_TIMEOUT) waiting for JVM exit code, stopping service...")
            runCatching {
                GlobalContext.stopService(Intent(GlobalContext, JvmService::class.java))
            }
            throw IOException("Timed out waiting for the JVM process to exit.")
        }
    } finally {
        // Tear down the receiver on success, failure, cancellation or timeout
        // Singleton state left across runs would poison the next one
        JVMSocketServer.stop()
        tailerJob?.cancel()
    }

    val code = JVMSocketServer.receiveMsg?.toIntOrNull()
    Logger.info(TAG, "receive exit code: ${code ?: "unknown, default 0"}")
    code ?: 0
}