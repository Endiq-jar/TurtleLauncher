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

package com.endiq.turtlelauncher.feature.recorder

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.content.getSystemService
import com.endiq.turtlelauncher.utils.logging.Logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Built-in game screen recorder. Uses a MediaProjection VirtualDisplay backed
 * by MediaRecorder (H.264 / MP4). Inspired by Turtle-Launcher's recording
 * feature; videos are stored in `Android/data/<pkg>/files/Movies` so they are
 * browsable without storage permissions.
 */
object ScreenRecorder {
    private const val TAG = "ScreenRecorder"
    private const val VIDEO_BITRATE = 12_000_000
    private const val VIDEO_FPS = 60

    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var projectionCallback: MediaProjection.Callback? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _currentFile = MutableStateFlow<File?>(null)
    val currentFile = _currentFile.asStateFlow()

    private val _lastFinishedFile = MutableStateFlow<File?>(null)
    val lastFinishedFile = _lastFinishedFile.asStateFlow()

    /** Intent to ask the user for the capture permission */
    fun createCaptureIntent(context: Context): Intent {
        val manager = context.getSystemService<MediaProjectionManager>()!!
        return manager.createScreenCaptureIntent()
    }

    /**
     * Starts a recording session. [resultCode]/[data] must come from the
     * permission dialog produced by [createCaptureIntent].
     */
    @Synchronized
    fun start(context: Context, resultCode: Int, data: Intent): Boolean {
        if (_isRecording.value) return true
        return runCatching {
            ScreenRecorderService.start(context)
            val manager = context.getSystemService<MediaProjectionManager>()!!
            val proj = manager.getMediaProjection(resultCode, data)
                ?: throw IllegalStateException("MediaProjectionManager returned null projection")
            projection = proj

            val cb = object : MediaProjection.Callback() {
                override fun onStop() {
                    Logger.warning(TAG, "MediaProjection stopped by the system, ending recording")
                    stop(context)
                }
            }
            proj.registerCallback(cb, null)
            projectionCallback = cb

            val metrics = getMetrics(context)
            val file = nextFile(context)

            @Suppress("DEPRECATION")
            val rec = (
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
                else MediaRecorder()
            ).apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(VIDEO_BITRATE)
                setVideoFrameRate(VIDEO_FPS)
                setVideoSize(metrics.widthPixels, metrics.heightPixels)
                setOutputFile(file.absolutePath)
                prepare()
            }
            recorder = rec

            virtualDisplay = proj.createVirtualDisplay(
                "TurtleScreenRecorder",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                rec.surface,
                null,
                null
            )
            rec.start()

            _currentFile.value = file
            _isRecording.value = true
            Logger.info(TAG, "Recording started -> ${file.absolutePath}")
            true
        }.onFailure {
            Logger.error(TAG, "Failed to start screen recording", it)
            cleanup(context, deletePartialFile = true)
        }.getOrDefault(false)
    }

    /**
     * Stops the session; returns the saved file (or null if recording failed).
     */
    @Synchronized
    fun stop(context: Context): File? {
        val file = _currentFile.value
        val saved = cleanup(context, deletePartialFile = false) && file != null && file.exists() && file.length() > 0
        if (saved) {
            _lastFinishedFile.value = file
            Logger.info(TAG, "Recording saved -> ${file?.absolutePath} (${file?.length()} bytes)")
        }
        return if (saved) file else null
    }

    @Synchronized
    private fun cleanup(context: Context, deletePartialFile: Boolean): Boolean {
        var ok = true
        var dropPartial = deletePartialFile
        runCatching { recorder?.stop() }
            .onFailure { ok = false; dropPartial = true }
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        runCatching { virtualDisplay?.release() }
        projectionCallback?.let { cb -> runCatching { projection?.unregisterCallback(cb) } }
        runCatching { projection?.stop() }
        runCatching { ScreenRecorderService.stop(context) }

        if (dropPartial) {
            _currentFile.value?.let { partial ->
                runCatching { partial.delete() }
                Logger.warning(TAG, "Discarded incomplete recording ${partial.name}")
            }
        }
        recorder = null
        virtualDisplay = null
        projection = null
        projectionCallback = null
        _isRecording.value = false
        _currentFile.value = null
        return ok
    }

    private fun getMetrics(context: Context): DisplayMetrics {
        val metrics = DisplayMetrics()
        val wm = context.getSystemService<WindowManager>()
        @Suppress("DEPRECATION")
        wm?.defaultDisplay?.getRealMetrics(metrics)
        return metrics
    }

    private fun nextFile(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.getDir(Environment.DIRECTORY_MOVIES, Context.MODE_PRIVATE)
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(dir, "turtle_rec_$stamp.mp4")
    }
}
