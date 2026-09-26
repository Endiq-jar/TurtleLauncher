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

package com.endiq.turtlelauncher.utils.network

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.context.COPY_LABEL_LINK
import com.endiq.turtlelauncher.game.download.engine.DownloadEngine
import com.endiq.turtlelauncher.game.download.engine.DownloadRequest
import com.endiq.turtlelauncher.path.DOWNLOAD_OKHTTP_CLIENT
import com.endiq.turtlelauncher.path.TIME_OUT
import com.endiq.turtlelauncher.path.createRequestBuilder
import com.endiq.turtlelauncher.ui.theme.showThemed
import com.endiq.turtlelauncher.utils.copyText
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.string.isEmptyOrBlank
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "NetWorkUtils"

/** Max per-file download time */
private const val DOWNLOAD_PER_FILE_TIMEOUT = 3 * 60 * 1000L

/** Max multi-source download time */
private const val DOWNLOAD_SOURCES_TIMEOUT = 5 * 60 * 1000L

/**
 * @return whether the network is usable
 */
fun isNetworkAvailable(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
}

/**
 * @return whether on mobile data
 */
fun isUsingMobileData(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val activeNetwork = connectivityManager.activeNetwork ?: return false
    val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
    return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
}

/**
 * Downloads a file to disk (dynamic chunking, source failover, degraded retries)
 * @throws TimeoutException on overall timeout
 */
suspend fun downloadFile(
    url: String,
    outputFile: File,
    sha1: String? = null,
    sizeCallback: (Long) -> Unit = {}
): Unit = withTimeout(DOWNLOAD_PER_FILE_TIMEOUT.milliseconds) {
    DownloadEngine.download(DownloadRequest(listOf(url), outputFile, sha1), sizeCallback = sizeCallback)
}

/**
 * Downloads a file from prioritized sources, rotating on failure
 * @throws TimeoutException on overall timeout
 */
suspend fun downloadFileFromSources(
    urls: List<String>,
    outputFile: File,
    sha1: String? = null,
    sizeCallback: (Long) -> Unit = {}
): Unit = withTimeout(DOWNLOAD_SOURCES_TIMEOUT.milliseconds) {
    DownloadEngine.download(DownloadRequest(urls, outputFile, sha1), sizeCallback = sizeCallback)
}

/**
 * Speed monitoring reports
 * @param onSpeedReport reports bytes after a 1-second delay
 */
suspend fun <T> withSpeedReport(
    onSpeedReport: (Long) -> Unit,
    onClear: () -> Unit = {},
    block: suspend (onBytesWritten: (Long) -> Unit) -> T
): T = coroutineScope {
    val bytesWritten = AtomicLong(0L)

    withSpeedReport(
        onTimeReport = {
            val currentBytes = bytesWritten.getAndSet(0L)
            onSpeedReport(currentBytes)
        },
        onClear = {
            bytesWritten.set(0L)
            onClear()
        },
        block = {
            block { bytes ->
                bytesWritten.addAndGet(bytes)
            }
        }
    )
}

/**
 * Speed monitoring reports
 * @param onTimeReport called after a 1-second delay; reports inside
 */
suspend fun <T> withSpeedReport(
    onTimeReport: () -> Unit,
    onClear: () -> Unit,
    block: suspend () -> T
): T = coroutineScope {
    var reportJob: Job? = null

    try {
        onClear()
        reportJob = launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000L.milliseconds)
                onTimeReport()
            }
        }

        block()
    } finally {
        reportJob?.cancelAndJoin()
        onClear()
    }
}

/**
 * Synchronously fetches the string content of a URL
 * @param url the URL to request
 * @return the string returned by the server
 * @throws IllegalArgumentException when the URL is invalid
 * @throws IOException when the network request or response parsing fails
 */
@Throws(IOException::class, IllegalArgumentException::class)
suspend fun fetchStringFromUrl(url: String): String = withContext(Dispatchers.IO) {
    try {
        withTimeout(TIME_OUT.milliseconds) {
            runInterruptible {
                DOWNLOAD_OKHTTP_CLIENT.newCall(createRequestBuilder(url).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} - ${response.message}")
                    }

                    response.body.use { it.string() }
                }
            }
        }
    } catch (_: TimeoutCancellationException) {
        throw TimeoutException("Request timed out after ${TIME_OUT}ms: $url")
    }
}

/**
 * Synchronously fetches the string content of a URL
 * @param urls the source URLs to request
 * @return the string returned by the server
 * @throws IllegalArgumentException when the URL is invalid
 * @throws IOException when the network request or response parsing fails
 */
@Throws(IOException::class, IllegalArgumentException::class)
suspend fun fetchStringFromUrls(urls: List<String>): String = withContext(Dispatchers.IO) {
    var result: String? = null
    var succeed = false
    var lastException: Throwable? = null

    loop@ for (url in urls) {
        runCatching {
            result = fetchStringFromUrl(url)
            succeed = true
            break@loop
        }.onFailure { th ->
            if (th is CancellationException || th.isInterruptedIOException()) throw th
            Logger.debug(TAG, "Source $url failed!", th)
            lastException = th
        }
    }

    if (!succeed || result == null) throw lastException ?: IOException("Failed to retrieve information from the source!")

    result
}

/**
 * Shows a prompt about the link to visit in browser, letting the user opt out
 * @param link the link to visit
 */
fun Activity.openLink(link: String) {
    this.openLink(link, null)
}

/**
 * Shows a prompt about the link to visit in browser, letting the user opt out
 * @param link the link to visit
 * @param dataType sets the intent's data and explicit MIME type
 */
fun Activity.openLink(link: String, dataType: String?) {
    if (link.isEmptyOrBlank()) {
        return
    }

    MaterialAlertDialogBuilder(this)
        .setTitle(R.string.generic_open_link)
        .setMessage(link)
        .setPositiveButton(R.string.generic_confirm) { _, _ ->
            openLinkInternal(link, dataType)
        }
        .setNegativeButton(R.string.generic_cancel) { dialog, _ ->
            dialog.dismiss()
        }
        .setNeutralButton(R.string.generic_copy) { dialog, _ ->
            copyText(COPY_LABEL_LINK, link, this)
            dialog.dismiss()
        }
        .showThemed()
}

/**
 * Opens the given link directly in browser
 */
fun Activity.openLinkInternal(link: String, dataType: String? = null) {
    try {
        val uri = link.toUri()
        val browserIntent = if (dataType != null) {
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, dataType)
            }
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }
        startActivity(browserIntent)
    } catch (e: Exception) {
        Logger.warning(TAG, "Failed to open link: $link", e)
    }
}

/**
 * Checks whether it's a plain interrupt, not a timeout interruption
 */
fun Throwable.isInterruptedIOException(): Boolean {
    return this is InterruptedIOException && this !is SocketTimeoutException
}