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

import com.endiq.turtlelauncher.coroutine.TaskLogOutput
import com.endiq.turtlelauncher.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "ProcessLogTailer"

/**
 * Incrementally polls the install JVM process's log file, pushing new complete lines to [TaskLogOutput]
 */
class ProcessLogTailer(
    private val logFile: File,
    private val output: TaskLogOutput,
    private val pollInterval: Duration = DEFAULT_POLL_INTERVAL
) {
    /**
     * Starts polling within the given scope; the caller owns cancelling the returned Job
     */
    fun launch(scope: CoroutineScope): Job = scope.launch(Dispatchers.IO) {
        //Read incrementally from the file's current end, so stale logs from the last JVM run don't leak into this session
        var offset = logFile.takeIf { it.exists() }?.length() ?: 0L
        var remainder = ByteArray(0)

        while (isActive) {
            delay(pollInterval)
            try {
                if (!logFile.exists()) continue
                val length = logFile.length()
                //File truncated (the log file gets recreated on JRE retry): reread from the start
                if (length < offset) {
                    offset = 0
                    remainder = ByteArray(0)
                }
                if (length <= offset) continue

                val bytes = RandomAccessFile(logFile, "r").use { raf ->
                    raf.seek(offset)
                    val buffer = ByteArray((length - offset).toInt())
                    raf.readFully(buffer)
                    buffer
                }
                offset = length

                val (completeLines, newRemainder) = splitCompleteLines(remainder + bytes)
                remainder = newRemainder
                if (remainder.size > MAX_PENDING_BYTES) remainder = ByteArray(0)
                if (completeLines.isNotEmpty()) output.appendLines(completeLines)
            } catch (e: Exception) {
                Logger.warning(TAG, "Failed to tail the installer process log", e)
            }
        }
    }

    /**
     * Splits complete lines at the last newline byte, keeping an incomplete tail for the next round,
     * so multi-byte UTF-8 characters never get cut into mojibake
     */
    private fun splitCompleteLines(bytes: ByteArray): Pair<List<String>, ByteArray> {
        val lastNewline = bytes.lastIndexOf('\n'.code.toByte())
        if (lastNewline < 0) return emptyList<String>() to bytes

        val completeText = String(bytes, 0, lastNewline + 1, StandardCharsets.UTF_8)
        val remainderBytes = bytes.copyOfRange(lastNewline + 1, bytes.size)

        val lines = completeText.split('\n')
            .map { it.removeSuffix("\r") }
            .filter { it.isNotEmpty() }
        return lines to remainderBytes
    }

    companion object {
        /** Default polling interval */
        val DEFAULT_POLL_INTERVAL = 250.milliseconds
        /** Max bytes buffered without a newline; further bytes are dropped, preventing unbounded growth */
        private const val MAX_PENDING_BYTES = 1024 * 1024
    }
}
