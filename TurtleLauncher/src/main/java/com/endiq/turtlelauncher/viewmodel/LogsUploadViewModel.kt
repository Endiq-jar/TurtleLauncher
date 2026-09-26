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

package com.endiq.turtlelauncher.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.endiq.turtlelauncher.crashlogs.AbstractAPI
import com.endiq.turtlelauncher.crashlogs.LinkNotFoundException
import com.endiq.turtlelauncher.crashlogs.MCLogsResponse
import com.endiq.turtlelauncher.crashlogs.platform.MCLogsAPI
import com.endiq.turtlelauncher.crashlogs.platform.MirroredAPI
import com.endiq.turtlelauncher.ui.screens.main.crashlogs.ShareLinkOperation
import com.endiq.turtlelauncher.utils.isChinaMainland
import com.endiq.turtlelauncher.utils.network.isInterruptedIOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Game log upload logic ViewModel
 */
class LogsUploadViewModel: ViewModel() {
    private var uploadJob: Job? = null
    private var checkJob: Job? = null

    /**
     * Log upload operation flow
     */
    var operation by mutableStateOf<ShareLinkOperation>(ShareLinkOperation.None)

    /**
     * Whether the log file is suitable for upload and share
     */
    var canUpload by mutableStateOf(false)
        private set
    
    /**
     * Checks whether the log file is suitable for upload
     */
    fun check(logFile: File) {
        checkJob?.cancel()
        checkJob = viewModelScope.launch(Dispatchers.IO) {
            //2MB already risks upload timeouts
            //Such logs often mean a missing evaluation library
            val canUpload0 = !logFile.exceeds2MB()
            withContext(Dispatchers.Main) {
                canUpload = canUpload0
            }
        }
    }

    /**
     * Checks whether the file exceeds 2MB
     */
    private fun File.exceeds2MB(): Boolean {
        return !exists() && !isFile && length() > 2 * 1024 * 1024
    }

    private suspend fun <E: AbstractAPI> mirroredAPI(
        list: List<E>,
        content: String
    ): MCLogsResponse {
        require(list.isNotEmpty()) { "API list must not be empty." }

        val errors = mutableListOf<Exception>()
        var lastException: Exception? = null

        for (api in list) {
            try {
                withContext(Dispatchers.Main) {
                    operation = ShareLinkOperation.Uploading(api.root)
                }

                return api.onUpload(content)
            } catch (e: Exception) {
                lastException = e

                if (e.isInterruptedIOException()) {
                    throw e
                } else {
                    errors.add(e)
                }
            }
        }

        throw IOException("All sources have failed to attempt", lastException).apply {
            errors.forEachIndexed { i, e ->
                addSuppressed(Exception("Mirror error #${i + 1}: ${e.message}"))
            }
        }
    }

    /**
     * Starts uploading the log
     */
    fun upload(
        logFile: File,
        onSuccess: (link: String) -> Unit
    ) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch(Dispatchers.IO) {
            if (logFile.exceeds2MB()) return@launch

            //Read the content and try uploading
            val content = logFile.readText()

            val apiList = if (isChinaMainland()) {
                listOf(MirroredAPI, MCLogsAPI)
            } else {
                listOf(MCLogsAPI)
            }

            try {
                val response = mirroredAPI(
                    list = apiList,
                    content = content
                )
                withContext(Dispatchers.Main) {
                    val link = response.url
                    if (link == null) {
                        //The remote data carries no usable link
                        operation = ShareLinkOperation.Error(
                            LinkNotFoundException()
                        )
                    } else {
                        onSuccess(link.replace("\\/", "/"))
                        operation = ShareLinkOperation.None
                    }
                }
            } catch (e: Exception) {
                if (e.isInterruptedIOException()) {
                    return@launch
                } else {
                    withContext(Dispatchers.Main) {
                        operation = ShareLinkOperation.Error(e)
                    }
                }
            }
        }
    }

    /**
     * Cancels the log upload
     */
    fun cancel() {
        uploadJob?.cancel()
        uploadJob = null
        operation = ShareLinkOperation.None
    }

    override fun onCleared() {
        checkJob?.cancel()
        checkJob = null
        cancel()
    }
}