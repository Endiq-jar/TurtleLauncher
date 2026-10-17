package com.endiq.turtlelauncher.feature.turtle

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ResultReceiver
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.task.TaskExecutors
import net.endiq.launcher.services.ScreenRecorderAudioService
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object ScreenRecorder {
    private const val TAG = "ScreenRecorder"
    private const val MIME_TYPE = "video/avc" // H.264 - broadest device/player compatibility
    private const val AUDIO_MIME_TYPE = "audio/mp4a-latm" // AAC-LC
    private const val AUDIO_SAMPLE_RATE = 44100
    private const val AUDIO_CHANNEL_COUNT = 2
    private const val AUDIO_BIT_RATE = 128_000

    /** Request code for the MediaProjection consent activity, handled in onActivityResult(). */
    const val REQUEST_CODE_AUDIO_CAPTURE = 8422

    // Not exposed on EGL14; value is stable across the NDK/platform (see EGL/eglext.h).
    private const val EGL_RECORDABLE_ANDROID = 0x3142

    private const val QUAD_VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """

    private const val QUAD_FRAGMENT_SHADER = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        void main() {
            gl_FragColor = texture2D(uTexture, vTexCoord);
        }
    """

    private const val QUAD_STRIDE_BYTES = 4 * 4 // x, y, u, v per vertex, 4 bytes per float

    /** Full-screen triangle strip (x, y, u, v). v=0 samples the bitmap's first (top) row, so
     *  top-left/top-right carry v=0 and the capture isn't flipped vertically. */
    private val quadVertices: FloatBuffer = run {
        val coords = floatArrayOf(
            -1f,  1f, 0f, 0f, // top-left
             1f,  1f, 1f, 0f, // top-right
            -1f, -1f, 0f, 1f, // bottom-left
             1f, -1f, 1f, 1f  // bottom-right
        )
        ByteBuffer.allocateDirect(coords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(coords)
            position(0)
        }
    }

    private val nameFormat = SimpleDateFormat("yyyy-MM-dd_HH.mm.ss.SSS", Locale.US)

    private val isRecordingFlag = AtomicBoolean(false)
    private val stopInProgress = AtomicBoolean(false)
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var inputSurface: Surface? = null
    private var muxerStarted = false
    private val muxerLock = Any()
    private var videoTrackIndex = -1
    private val videoBufferInfo = MediaCodec.BufferInfo()
    private var addedTrackCount = 0
    private var expectedTrackCount = 1
    private var encodedVideoSampleCount = 0L

    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private val captureLock = Any()
    private var pixelCopyInFlight = false
    private var pixelCopyThread: HandlerThread? = null
    private var frameBitmap: Bitmap? = null
    private var startTimeNanos = 0L
    private var frameIntervalMs = 1000L / 30L
    private var outputFile: File? = null
    private var recordingContext: Context? = null
    private var targetSurfaceView: SurfaceView? = null
    private var targetTextureView: TextureView? = null
    private var maxDurationMs = 0L

    // GL frame submission state - lazily initialized on captureThread in drawFrame(), torn down
    // in releaseQuietly(). Drawing the captured bitmap via GLES into the encoder's input surface
    // (instead of Surface.lockCanvas()/Canvas.drawBitmap) is required for the RGB->YUV conversion
    // the hardware encoder performs to use the correct color matrix - on several GPUs, the
    // software canvas path left the buffer's colorspace untagged, which showed up as a blue tint
    // on foliage and blocky artifacts at high-contrast edges (e.g. the sky/terrain horizon) in the
    // saved recording only, never in the live on-screen render.
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var glProgram = 0
    private var glTextureId = 0
    private var glInitialized = false

    // Audio capture state - all null/unused whenever recording video-only.
    private var mediaProjection: MediaProjection? = null
    private var audioServiceContext: Context? = null
    private var audioRecord: AudioRecord? = null
    private var audioEncoder: MediaCodec? = null
    private var audioTrackIndex = -1
    private val audioBufferInfo = MediaCodec.BufferInfo()
    private var audioCaptureThread: HandlerThread? = null
    private var audioCaptureHandler: Handler? = null
    private var pendingConsentActivity: WeakReference<Activity>? = null
    @Volatile private var audioCaptureFailed = false

    fun isRecording(): Boolean = isRecordingFlag.get()

    /** @return elapsed recording time in whole seconds, or 0 if not currently recording. */
    fun getElapsedSeconds(): Long {
        if (!isRecordingFlag.get() || startTimeNanos == 0L) return 0L
        return (System.nanoTime() - startTimeNanos) / 1_000_000_000L
    }

    fun toggle(activity: Activity) {
        if (isRecordingFlag.get()) stop(activity) else start(activity)
    }

    /**
     * Public entry point. If audio capture is enabled in settings and available on this API
     * level, this launches the MediaProjection consent dialog first and actually starts
     * recording from onActivityResult() once that returns - the caller (a click listener) does
     * not need to know about this indirection, same as before audio existed.
     */
    fun start(activity: Activity) {
        if (isRecordingFlag.get() || stopInProgress.get()) return

        val wantsAudio = AllSettings.recordingCaptureAudio.getValue() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        if (wantsAudio) {
            val projectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                    as? MediaProjectionManager
            if (projectionManager != null) {
                try {
                    pendingConsentActivity = WeakReference(activity)
                    activity.startActivityForResult(
                        projectionManager.createScreenCaptureIntent(),
                        REQUEST_CODE_AUDIO_CAPTURE
                    )
                    return
                } catch (t: Throwable) {
                    Logging.e(TAG, "Couldn't request audio-capture consent, recording video-only", t)
                    pendingConsentActivity = null
                }
            }
        }
        startInternal(activity, null)
    }

    fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_CODE_AUDIO_CAPTURE) return

        val target = pendingConsentActivity?.get() ?: activity
        pendingConsentActivity = null

        if (resultCode == Activity.RESULT_OK && data != null) {
            // Starting a foreground service is asynchronous. Android 14 throws a SecurityException
            // if getMediaProjection() wins that race and runs before startForeground(). Wait for
            // the service's explicit ready callback, then obtain/use the one-shot consent token.
            val targetRef = WeakReference(target)
            val appContext = target.applicationContext
            val readyReceiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(serviceResult: Int, resultData: Bundle?) {
                    val readyTarget = targetRef.get()
                    if (readyTarget == null || readyTarget.isFinishing ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && readyTarget.isDestroyed)
                    ) {
                        ScreenRecorderAudioService.stop(appContext)
                        return
                    }
                    if (serviceResult != ScreenRecorderAudioService.RESULT_READY) {
                        Toast.makeText(
                            readyTarget,
                            "Game audio capture unavailable - recording video only",
                            Toast.LENGTH_SHORT
                        ).show()
                        startInternal(readyTarget, null)
                        return
                    }
                    try {
                        val projectionManager = readyTarget.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
                                as MediaProjectionManager
                        val projection = projectionManager.getMediaProjection(resultCode, data)
                        startInternal(readyTarget, projection)
                    } catch (t: Throwable) {
                        Logging.e(TAG, "Failed to obtain MediaProjection, recording video-only", t)
                        ScreenRecorderAudioService.stop(readyTarget.applicationContext)
                        startInternal(readyTarget, null)
                    }
                }
            }
            try {
                ScreenRecorderAudioService.start(appContext, readyReceiver)
                return
            } catch (t: Throwable) {
                Logging.e(TAG, "Failed to start audio-capture service, recording video-only", t)
                ScreenRecorderAudioService.stop(appContext)
            }
        } else {
            Toast.makeText(target, "Audio capture declined - recording video only", Toast.LENGTH_SHORT).show()
        }
        startInternal(target, null)
    }

    private fun startInternal(activity: Activity, projection: MediaProjection?) {
        if (isRecordingFlag.get() || stopInProgress.get()) {
            // A second consent round-trip could in theory land here after another start() already
            // went through video-only in the meantime - don't leak an unused projection.
            try { projection?.stop() } catch (t: Throwable) { Logging.e(TAG, "projection.stop() failed", t) }
            if (projection != null) ScreenRecorderAudioService.stop(activity.applicationContext)
            return
        }

        val renderSurfaceView = findRenderSurface(activity.window.decorView)
        if (renderSurfaceView == null) {
            Toast.makeText(activity, "Recording unavailable - couldn't locate the game surface", Toast.LENGTH_SHORT).show()
            try { projection?.stop() } catch (t: Throwable) { Logging.e(TAG, "projection.stop() failed", t) }
            if (projection != null) ScreenRecorderAudioService.stop(activity.applicationContext)
            return
        }

        val width = evenify(scaleDimension(renderSurfaceView.width))
        val height = evenify(scaleDimension(renderSurfaceView.height))
        if (width <= 0 || height <= 0) {
            Toast.makeText(activity, "Recording unavailable - game surface isn't ready yet", Toast.LENGTH_SHORT).show()
            try { projection?.stop() } catch (t: Throwable) { Logging.e(TAG, "projection.stop() failed", t) }
            if (projection != null) ScreenRecorderAudioService.stop(activity.applicationContext)
            return
        }

        try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, AllSettings.recordingBitrateMbps.getValue().coerceIn(2, 20) * 1_000_000)
                val fps = AllSettings.recordingFrameRate.getValue().coerceIn(15, 60)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                frameIntervalMs = 1000L / fps.toLong()
            }

            val codec = MediaCodec.createEncoderByType(MIME_TYPE)
            if (codec == null) {
                throw RuntimeException("No H.264 encoder found on this device")
            }
            encoder = codec
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            if (surface == null) {
                throw RuntimeException("Failed to create input surface for encoder")
            }
            inputSurface = surface
            codec.start()

            // Try to use external movies directory, fall back to cache dir
            val moviesDir = activity.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            val cacheDir = activity.cacheDir
            val baseDir = moviesDir ?: cacheDir

            val dir = File(baseDir, "recordings").apply {
                if (!exists()) {
                    Logging.i(TAG, "Creating recordings directory at: ${absolutePath}")
                    if (!mkdirs()) {
                        Logging.e(TAG, "Failed to create recordings directory")
                    }
                }
            }

            val actualDir = if (dir.exists() && dir.isDirectory) {
                dir
            } else {
                Logging.e(TAG, "Recordings directory does not exist or is not a directory, trying cache")
                // Fallback to cache directory
                val cacheRecordingsDir = File(cacheDir, "recordings").apply { mkdirs() }
                if (cacheRecordingsDir.exists() && cacheRecordingsDir.isDirectory) {
                    Logging.i(TAG, "Using cache directory for recordings")
                    cacheRecordingsDir
                } else {
                    throw IOException("Cannot create recordings directory in either location")
                }
            }
            val file = File(actualDir, "turtle_${nameFormat.format(Date())}.mp4")
            Logging.i(TAG, "Recording to: ${file.absolutePath}")
            outputFile = file
            recordingContext = activity.applicationContext
            val mediaMuxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mediaMuxer

            videoTrackIndex = -1
            audioTrackIndex = -1
            addedTrackCount = 0
            expectedTrackCount = 1
            encodedVideoSampleCount = 0L
            muxerStarted = false
            audioCaptureFailed = false

            when (renderSurfaceView) {
                is SurfaceView -> {
                    targetSurfaceView = renderSurfaceView
                    targetTextureView = null
                }
                is TextureView -> {
                    targetSurfaceView = null
                    targetTextureView = renderSurfaceView
                }
            }
            frameBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            val maxMinutes = AllSettings.recordingMaxDurationMin.getValue()
            maxDurationMs = if (maxMinutes > 0) maxMinutes * 60_000L else 0L

            mediaProjection = projection
            audioServiceContext = if (projection != null) activity.applicationContext else null
            val audioReady = projection != null && setupAudioCapture(projection)
            if (projection != null && !audioReady) {
                // Setup failed - the consent was granted but capture itself couldn't start.
                // Don't hold onto an unused projection/service.
                try { projection.stop() } catch (t: Throwable) { Logging.e(TAG, "projection.stop() failed", t) }
                mediaProjection = null
                audioServiceContext?.let { ScreenRecorderAudioService.stop(it) }
                audioServiceContext = null
            }
            expectedTrackCount = if (audioReady) 2 else 1

            val thread = HandlerThread("ScreenRecorder-Capture").apply { start() }
            captureThread = thread
            captureHandler = Handler(thread.looper)

            startTimeNanos = System.nanoTime()
            isRecordingFlag.set(true)
            captureHandler?.post(captureLoop(activity))

            // Do not start the audio worker from setupAudioCapture(). It checks
            // isRecordingFlag before reading, and could otherwise run before the flag was set.
            // That race left the expected audio track missing, so MediaMuxer never started and
            // the resulting MP4 stayed at 0 bytes.
            if (audioReady && !startAudioCaptureLoop()) {
                onAudioCaptureFailed(IllegalStateException("Could not start audio capture thread"))
            }

            Toast.makeText(
                activity,
                if (audioReady && !audioCaptureFailed) "Recording started (with audio)" else "Recording started",
                Toast.LENGTH_SHORT
            ).show()
        } catch (t: Throwable) {
            Logging.e(TAG, "Failed to start recording", t)
            isRecordingFlag.set(false)
            val incompleteFile = outputFile
            try { projection?.stop() } catch (inner: Throwable) { Logging.e(TAG, "projection.stop() failed", inner) }
            if (projection != null) ScreenRecorderAudioService.stop(activity.applicationContext)
            captureThread?.quitSafely()
            audioCaptureThread?.quitSafely()
            releaseQuietly()
            if (incompleteFile != null && incompleteFile.exists() && !incompleteFile.delete()) {
                Logging.e(TAG, "Could not delete failed recording: ${incompleteFile.absolutePath}")
            }
            Toast.makeText(activity, "Recording failed to start", Toast.LENGTH_SHORT).show()
        }
    }

    fun stop(activity: Activity?) {
        if (!isRecordingFlag.compareAndSet(true, false)) return
        stopInProgress.set(true)

        // onDestroy() and capture failures may stop with no live Activity. Keep the application
        // context captured at start so a finished recording can still be published to Gallery.
        val appContext = activity?.applicationContext ?: recordingContext
        val videoThread = captureThread
        val audioThread = audioCaptureThread

        synchronized(captureLock) {
            // Keep the handler alive until PixelCopy has finished writing into frameBitmap. Its
            // completion callback quits the thread if stop() happened while the copy was pending.
            if (!pixelCopyInFlight) videoThread?.quitSafely()
        }
        captureThread = null
        captureHandler = null

        audioThread?.quitSafely()
        audioCaptureThread = null
        audioCaptureHandler = null

        // Drain and tear down on a background thread. Stop the AudioRecord first to unblock a
        // pending read, then join both workers before touching their codecs from this thread.
        TaskExecutors.getDefault().execute {
            try {
                try { audioRecord?.stop() } catch (t: Throwable) { Logging.e(TAG, "audioRecord.stop() failed", t) }
                val audioWorkerStopped = waitForThread(audioThread, "audio capture")
                val videoWorkerStopped = waitForThread(videoThread, "video capture")

                if (audioEncoder != null && !audioCaptureFailed && audioWorkerStopped) {
                    try {
                        // Drain audio first: the muxer cannot start until both track formats are
                        // registered, and video must not be drained while its samples would be
                        // discarded waiting for the audio format.
                        finishAudioEncoding()
                    } catch (t: Throwable) {
                        Logging.e(TAG, "Error draining audio encoder on stop", t)
                    }
                } else if (audioEncoder != null && !audioWorkerStopped) {
                    audioCaptureFailed = true
                    Logging.e(TAG, "Audio capture worker did not stop cleanly; finalizing video without audio")
                }

                // Audio is optional. If it never produced a track, release the video track rather
                // than leaving the muxer waiting forever (which produced empty, unplayable 0-byte MP4s).
                allowVideoOnlyMuxingIfAudioTrackMissing()

                if (videoWorkerStopped) {
                    try {
                        drainVideoEncoder(endOfStream = true)
                    } catch (t: Throwable) {
                        Logging.e(TAG, "Error draining video encoder on stop", t)
                    }
                } else {
                    Logging.e(TAG, "Video capture worker did not stop cleanly; skipping a concurrent codec drain")
                }

                val savedFile = outputFile
                val videoSamplesWritten = encodedVideoSampleCount
                val muxerWasStarted = synchronized(muxerLock) { muxerStarted }
                if (muxer != null && muxerWasStarted) {
                    try {
                        // Allow the final muxer writes to settle before release.
                        Thread.sleep(100)
                    } catch (t: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }

                releaseQuietly()

                // A nonempty MP4 header alone is not a playable recording; require at least one
                // video sample and remove empty files instead of leaving misleading 0-byte entries.
                var fileValid = false
                var galleryAdded = false
                if (savedFile != null) {
                    val maxAttempts = 5
                    for (attempt in 1..maxAttempts) {
                        try {
                            Thread.sleep(200L * attempt)
                        } catch (t: InterruptedException) {
                            Thread.currentThread().interrupt()
                            break
                        }
                        if (savedFile.exists() && savedFile.length() > 0 && videoSamplesWritten > 0) {
                            fileValid = true
                            if (attempt == 1) {
                                galleryAdded = appContext != null && addVideoToGallery(appContext, savedFile)
                            }
                            break
                        }
                    }
                    Logging.i(
                        TAG,
                        "Recording validation: ${if (fileValid) "success" else "failed"} " +
                            "(video samples=$videoSamplesWritten, file=${savedFile.absolutePath})"
                    )
                    if (fileValid && !galleryAdded) {
                        galleryAdded = appContext != null && addVideoToGallery(appContext, savedFile)
                    } else if (!fileValid && savedFile.exists() && !savedFile.delete()) {
                        Logging.e(TAG, "Could not delete incomplete recording: ${savedFile.absolutePath}")
                    }
                }

                TaskExecutors.getAndroidUI().execute {
                    if (activity != null) {
                        val message = if (savedFile != null && fileValid)
                            "Recording saved: ${savedFile.name}" + if (galleryAdded) " (Added to gallery)" else ""
                        else "Recording failed - no video frames were captured"
                        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (t: Throwable) {
                Logging.e(TAG, "Unexpected error while finalizing recording", t)
                releaseQuietly()
                TaskExecutors.getAndroidUI().execute {
                    if (activity != null) {
                        Toast.makeText(activity, "Recording failed to save", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                stopInProgress.set(false)
            }
        }
    }

    private fun waitForThread(thread: HandlerThread?, label: String): Boolean {
        if (thread == null || thread === Thread.currentThread()) return true
        return try {
            thread.join(5_000L)
            if (thread.isAlive) {
                Logging.e(TAG, "$label worker did not stop within 5 seconds")
                false
            } else {
                true
            }
        } catch (t: InterruptedException) {
            Thread.currentThread().interrupt()
            Logging.e(TAG, "Interrupted while waiting for $label worker", t)
            false
        }
    }

    private fun allowVideoOnlyMuxingIfAudioTrackMissing() {
        synchronized(muxerLock) {
            if (!muxerStarted && audioTrackIndex == -1 && expectedTrackCount > 1) {
                expectedTrackCount = 1
                startMuxerIfReadyLocked()
            }
        }
    }

    // ---- Video capture ----

    private fun captureLoop(activity: Activity): Runnable = object : Runnable {
        private var consecutiveCaptureFailures = 0

        override fun run() {
            if (!isRecordingFlag.get()) return

            if (maxDurationMs > 0 && (System.nanoTime() - startTimeNanos) / 1_000_000L >= maxDurationMs) {
                TaskExecutors.getAndroidUI().execute { stop(activity) }
                return
            }

            try {
                captureFrame { captured ->
                    if (!isRecordingFlag.get()) return@captureFrame
                    if (captured) {
                        consecutiveCaptureFailures = 0
                    } else {
                        consecutiveCaptureFailures++
                        if (consecutiveCaptureFailures == 1 || consecutiveCaptureFailures % 10 == 0) {
                            Logging.e(TAG, "Could not capture game frame ($consecutiveCaptureFailures consecutive failures)")
                        }
                        if (consecutiveCaptureFailures >= 30) {
                            Logging.e(TAG, "Stopping recording after repeated game-frame capture failures")
                            stop(activity)
                            return@captureFrame
                        }
                    }
                    // PixelCopy is asynchronous. Schedule the next frame only after this one has
                    // finished so it cannot overwrite the shared bitmap while the encoder reads it.
                    if (isRecordingFlag.get()) captureHandler?.postDelayed(this, frameIntervalMs)
                }
            } catch (t: Throwable) {
                Logging.e(TAG, "Frame capture failed, stopping recording", t)
                TaskExecutors.getAndroidUI().execute { stop(activity) }
            }
        }
    }

    private fun captureFrame(onComplete: (Boolean) -> Unit) {
        val bitmap = frameBitmap ?: run {
            onComplete(false)
            return
        }
        val surfaceView = targetSurfaceView
        val textureView = targetTextureView

        when {
            surfaceView != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                // The next frame is scheduled from this callback, so only one PixelCopy request
                // can write into the recording bitmap at a time. Coordinate request startup with
                // stop() so the callback thread remains alive until PixelCopy releases the bitmap.
                val callbackHandler = synchronized(captureLock) {
                    if (!isRecordingFlag.get()) {
                        null
                    } else {
                        pixelCopyInFlight = true
                        pixelCopyThread = captureThread
                        captureHandler
                    }
                }
                if (callbackHandler == null) {
                    synchronized(captureLock) {
                        pixelCopyInFlight = false
                        pixelCopyThread = null
                    }
                    onComplete(false)
                    return
                }

                try {
                    PixelCopy.request(surfaceView, bitmap, { result ->
                        val shouldContinue = synchronized(captureLock) {
                            pixelCopyInFlight = false
                            val active = isRecordingFlag.get()
                            if (!active) pixelCopyThread?.quitSafely()
                            pixelCopyThread = null
                            active
                        }
                        if (!shouldContinue) return@request
                        if (result == PixelCopy.SUCCESS) onComplete(drawFrame(bitmap))
                        else onComplete(false)
                    }, callbackHandler)
                } catch (t: Throwable) {
                    synchronized(captureLock) {
                        pixelCopyInFlight = false
                        pixelCopyThread = null
                    }
                    throw t
                }
            }
            textureView != null -> {
                val copied = textureView.getBitmap(bitmap)
                onComplete(copied != null && drawFrame(bitmap))
            }
            else -> onComplete(false)
        }
    }

    private fun drawFrame(bitmap: Bitmap): Boolean {
        val surface = inputSurface ?: return false
        return try {
            if (!glInitialized && !initGl(surface)) {
                throw RuntimeException("GL recorder context could not be initialized")
            }
            if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                throw RuntimeException("eglMakeCurrent failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
            }

            GLES20.glViewport(0, 0, bitmap.width, bitmap.height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(glProgram)

            val positionHandle = GLES20.glGetAttribLocation(glProgram, "aPosition")
            val texCoordHandle = GLES20.glGetAttribLocation(glProgram, "aTexCoord")
            val textureHandle = GLES20.glGetUniformLocation(glProgram, "uTexture")

            quadVertices.position(0)
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, QUAD_STRIDE_BYTES, quadVertices)
            quadVertices.position(2)
            GLES20.glEnableVertexAttribArray(texCoordHandle)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, QUAD_STRIDE_BYTES, quadVertices)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, glTextureId)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glUniform1i(textureHandle, 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            GLES20.glDisableVertexAttribArray(positionHandle)
            GLES20.glDisableVertexAttribArray(texCoordHandle)

            if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
                throw RuntimeException("eglSwapBuffers failed: 0x${Integer.toHexString(EGL14.eglGetError())}")
            }
            // PixelCopy delivers this callback asynchronously, outside captureLoop's try/catch.
            // Keep codec/driver failures from escaping the HandlerThread and killing the process.
            drainVideoEncoder(endOfStream = false)
            true
        } catch (t: Throwable) {
            Logging.e(TAG, "Failed to submit a frame to the video encoder; stopping safely", t)
            stop(null)
            false
        }
    }

    /** Always called on captureThread (the only thread that ever draws a frame), so a single EGL
     *  context made current there on first use, and reused for every later frame, is safe. */
    private fun initGl(surface: Surface): Boolean {
        return try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) throw RuntimeException("eglGetDisplay failed")
            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
                throw RuntimeException("eglInitialize failed")
            }

            val configAttribs = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) ||
                numConfigs[0] <= 0
            ) {
                throw RuntimeException("eglChooseConfig found no matching recordable config")
            }
            val config = configs[0] ?: throw RuntimeException("eglChooseConfig returned a null config")

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (context == EGL14.EGL_NO_CONTEXT) throw RuntimeException("eglCreateContext failed")

            val windowSurface = EGL14.eglCreateWindowSurface(display, config, surface, intArrayOf(EGL14.EGL_NONE), 0)
            if (windowSurface == EGL14.EGL_NO_SURFACE) throw RuntimeException("eglCreateWindowSurface failed")

            if (!EGL14.eglMakeCurrent(display, windowSurface, windowSurface, context)) {
                throw RuntimeException("initial eglMakeCurrent failed")
            }

            val program = buildGlProgram()
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            eglDisplay = display
            eglContext = context
            eglSurface = windowSurface
            glProgram = program
            glTextureId = textures[0]
            glInitialized = true
            true
        } catch (t: Throwable) {
            Logging.e(TAG, "GL recorder init failed", t)
            releaseGl()
            false
        }
    }

    private fun buildGlProgram(): Int {
        val vertexShader = compileGlShader(GLES20.GL_VERTEX_SHADER, QUAD_VERTEX_SHADER)
        val fragmentShader = compileGlShader(GLES20.GL_FRAGMENT_SHADER, QUAD_FRAGMENT_SHADER)
        val program = GLES20.glCreateProgram()
        if (program == 0) throw RuntimeException("glCreateProgram failed")
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException("GL program link failed: $log")
        }
        return program
    }

    private fun compileGlShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) throw RuntimeException("glCreateShader failed")
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("GL shader compile failed: $log")
        }
        return shader
    }

    private fun releaseGl() {
        try {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                if (glTextureId != 0) GLES20.glDeleteTextures(1, intArrayOf(glTextureId), 0)
                if (glProgram != 0) GLES20.glDeleteProgram(glProgram)
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, eglSurface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
        } catch (t: Throwable) {
            Logging.e(TAG, "releaseGl() failed", t)
        } finally {
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE
            glProgram = 0
            glTextureId = 0
            glInitialized = false
        }
    }

    private fun drainVideoEncoder(endOfStream: Boolean) {
        val codec = encoder ?: return
        if (muxer == null) return
        if (endOfStream) {
            try { codec.signalEndOfInputStream() } catch (t: Throwable) { Logging.e(TAG, "signalEndOfInputStream failed", t) }
        }
        drainEncoderCommon(codec, videoBufferInfo, endOfStream, { videoTrackIndex }, { videoTrackIndex = it })
    }

    // ---- Audio capture ----

    /** @return true if audio capture was initialized; false leaves recording video-only. */
    private fun setupAudioCapture(projection: MediaProjection): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false

        var record: AudioRecord? = null
        var codec: MediaCodec? = null
        return try {
            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val channelMask = AudioFormat.CHANNEL_IN_STEREO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, channelMask, encoding)
            if (minBufferSize <= 0) throw IOException("AudioRecord returned an invalid buffer size")

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(AUDIO_SAMPLE_RATE)
                .setChannelMask(channelMask)
                .setEncoding(encoding)
                .build()

            val createdRecord = AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(minBufferSize * 2)
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()
            record = createdRecord
            if (createdRecord.state != AudioRecord.STATE_INITIALIZED) {
                throw IOException("AudioRecord could not be initialized")
            }

            val encodeFormat = MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, AUDIO_SAMPLE_RATE, AUDIO_CHANNEL_COUNT).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
            }
            val createdCodec = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE)
            codec = createdCodec
            createdCodec.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            createdCodec.start()
            createdRecord.startRecording()
            if (createdRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IOException("AudioRecord did not enter the recording state")
            }

            audioRecord = createdRecord
            audioEncoder = createdCodec
            audioTrackIndex = -1
            true
        } catch (t: Throwable) {
            Logging.e(TAG, "Audio capture setup failed, continuing video-only", t)
            try { record?.stop() } catch (inner: Throwable) { Logging.e(TAG, "audioRecord.stop() after setup failure", inner) }
            try { record?.release() } catch (inner: Throwable) { Logging.e(TAG, "audioRecord.release() after setup failure", inner) }
            try { codec?.stop() } catch (inner: Throwable) { Logging.e(TAG, "audioEncoder.stop() after setup failure", inner) }
            try { codec?.release() } catch (inner: Throwable) { Logging.e(TAG, "audioEncoder.release() after setup failure", inner) }
            audioRecord = null
            audioEncoder = null
            false
        }
    }

    /** Starts only after isRecordingFlag is true, so the worker cannot exit before the muxer starts. */
    private fun startAudioCaptureLoop(): Boolean {
        if (audioRecord == null || audioEncoder == null || !isRecordingFlag.get()) return false
        return try {
            val thread = HandlerThread("ScreenRecorder-Audio").apply { start() }
            audioCaptureThread = thread
            val handler = Handler(thread.looper)
            audioCaptureHandler = handler
            if (handler.post(audioCaptureLoop())) {
                true
            } else {
                thread.quitSafely()
                audioCaptureThread = null
                audioCaptureHandler = null
                false
            }
        } catch (t: Throwable) {
            Logging.e(TAG, "Could not start audio capture thread", t)
            false
        }
    }

    private fun audioCaptureLoop(): Runnable = object : Runnable {
        private val pcmBuffer = ByteArray(4096)
        override fun run() {
            if (!isRecordingFlag.get()) return
            try {
                captureAudioChunk(pcmBuffer)
            } catch (t: Throwable) {
                if (isRecordingFlag.get()) onAudioCaptureFailed(t)
                else Logging.i(TAG, "Audio capture stopped while the recording was finishing")
                return
            }
            if (isRecordingFlag.get()) audioCaptureHandler?.post(this)
        }
    }

    private fun onAudioCaptureFailed(error: Throwable) {
        audioCaptureFailed = true
        Logging.e(TAG, "Audio capture failed; continuing with video-only recording", error)
        try { audioRecord?.stop() } catch (t: Throwable) { Logging.e(TAG, "audioRecord.stop() after capture failure", t) }
        audioCaptureThread?.quitSafely()

        synchronized(muxerLock) {
            if (!muxerStarted && audioTrackIndex == -1 && expectedTrackCount > 1) {
                expectedTrackCount = 1
                startMuxerIfReadyLocked()
            }
        }

        try { mediaProjection?.stop() } catch (t: Throwable) { Logging.e(TAG, "mediaProjection.stop() after capture failure", t) }
        mediaProjection = null
        audioServiceContext?.let {
            try { ScreenRecorderAudioService.stop(it) } catch (t: Throwable) { Logging.e(TAG, "Audio service stop after capture failure", t) }
        }
        audioServiceContext = null
    }

    private fun captureAudioChunk(buffer: ByteArray) {
        val record = audioRecord ?: throw IllegalStateException("AudioRecord is unavailable")
        val codec = audioEncoder ?: throw IllegalStateException("Audio encoder is unavailable")
        val read = record.read(buffer, 0, buffer.size)
        if (read < 0) throw IOException("AudioRecord.read failed with code $read")
        if (read == 0) return

        val inputIndex = codec.dequeueInputBuffer(10_000L)
        if (inputIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputIndex)
            if (inputBuffer != null) {
                inputBuffer.clear()
                inputBuffer.put(buffer, 0, read)
                val presentationTimeUs = System.nanoTime() / 1000L
                codec.queueInputBuffer(inputIndex, 0, read, presentationTimeUs, 0)
            }
        }
        drainAudioEncoder(endOfStream = false)
    }

    private fun finishAudioEncoding() {
        val codec = audioEncoder ?: return
        var signaledEndOfStream = false
        val deadlineNanos = System.nanoTime() + 2_000_000_000L
        try {
            while (!signaledEndOfStream && System.nanoTime() < deadlineNanos) {
                val inputIndex = codec.dequeueInputBuffer(10_000L)
                if (inputIndex >= 0) {
                    codec.queueInputBuffer(
                        inputIndex, 0, 0, System.nanoTime() / 1000L,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                    signaledEndOfStream = true
                } else {
                    // Free output buffers while waiting for an input slot. A single dequeue
                    // can return INFO_TRY_AGAIN_LATER even though the encoder is still healthy.
                    drainAudioEncoder(endOfStream = false)
                }
            }
        } catch (t: Throwable) {
            Logging.e(TAG, "Failed to signal audio end-of-stream", t)
        }
        if (signaledEndOfStream) {
            drainAudioEncoder(endOfStream = true)
        } else {
            Logging.e(TAG, "Timed out while signaling audio end-of-stream")
        }
    }

    private fun drainAudioEncoder(endOfStream: Boolean) {
        val codec = audioEncoder ?: return
        if (muxer == null) return
        drainEncoderCommon(codec, audioBufferInfo, endOfStream, { audioTrackIndex }, { audioTrackIndex = it })
    }

    // ---- Shared encoder draining ----

    /** Must be called with muxerLock held. */
    private fun startMuxerIfReadyLocked() {
        val mediaMuxer = muxer ?: return
        if (!muxerStarted && addedTrackCount >= expectedTrackCount) {
            mediaMuxer.start()
            muxerStarted = true
            Logging.i(TAG, "Media muxer started with $addedTrackCount track(s)")
        }
    }

    private fun drainEncoderCommon(
        codec: MediaCodec,
        info: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        getTrackIndex: () -> Int,
        setTrackIndex: (Int) -> Unit
    ) {
        val mediaMuxer = muxer ?: return
        val drainDeadlineNanos = if (endOfStream) System.nanoTime() + 5_000_000_000L else Long.MAX_VALUE
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(info, 10_000L)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                    if (System.nanoTime() >= drainDeadlineNanos) {
                        Logging.e(TAG, "Timed out waiting for encoder end-of-stream")
                        return
                    }
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    synchronized(muxerLock) {
                        if (getTrackIndex() == -1) {
                            val idx = mediaMuxer.addTrack(codec.outputFormat)
                            setTrackIndex(idx)
                            addedTrackCount++
                            startMuxerIfReadyLocked()
                        }
                    }
                }
                outputIndex >= 0 -> {
                    val encodedData = codec.getOutputBuffer(outputIndex)
                    val trackIndex = getTrackIndex()
                    try {
                        if (encodedData != null && info.size > 0 && trackIndex != -1) {
                            encodedData.position(info.offset)
                            encodedData.limit(info.offset + info.size)
                            synchronized(muxerLock) {
                                if (muxerStarted) {
                                    mediaMuxer.writeSampleData(trackIndex, encodedData, info)
                                    if (trackIndex == videoTrackIndex &&
                                        (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                                    ) {
                                        encodedVideoSampleCount++
                                    }
                                }
                            }
                        }
                    } finally {
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
            }
        }
    }

    // ---- Teardown ----

    private fun releaseQuietly() {
        try { encoder?.stop() } catch (t: Throwable) { Logging.e(TAG, "encoder.stop() failed", t) }
        try { encoder?.release() } catch (t: Throwable) { Logging.e(TAG, "encoder.release() failed", t) }
        try { audioEncoder?.stop() } catch (t: Throwable) { Logging.e(TAG, "audioEncoder.stop() failed", t) }
        try { audioEncoder?.release() } catch (t: Throwable) { Logging.e(TAG, "audioEncoder.release() failed", t) }
        try { audioRecord?.release() } catch (t: Throwable) { Logging.e(TAG, "audioRecord.release() failed", t) }
        try { if (muxerStarted) muxer?.stop() } catch (t: Throwable) { Logging.e(TAG, "muxer.stop() failed", t) }
        try { muxer?.release() } catch (t: Throwable) { Logging.e(TAG, "muxer.release() failed", t) }
        try { releaseGl() } catch (t: Throwable) { Logging.e(TAG, "releaseGl() failed", t) }
        try { inputSurface?.release() } catch (t: Throwable) { Logging.e(TAG, "inputSurface.release() failed", t) }
        try { mediaProjection?.stop() } catch (t: Throwable) { Logging.e(TAG, "mediaProjection.stop() failed", t) }
        audioServiceContext?.let {
            try { ScreenRecorderAudioService.stop(it) } catch (t: Throwable) { Logging.e(TAG, "ScreenRecorderAudioService.stop() failed", t) }
        }
        frameBitmap?.recycle()

        isRecordingFlag.set(false)
        encoder = null
        muxer = null
        inputSurface = null
        muxerStarted = false
        videoTrackIndex = -1
        audioTrackIndex = -1
        addedTrackCount = 0
        expectedTrackCount = 1
        encodedVideoSampleCount = 0L
        frameBitmap = null
        targetSurfaceView = null
        targetTextureView = null
        outputFile = null
        recordingContext = null
        startTimeNanos = 0L
        audioRecord = null
        audioEncoder = null
        mediaProjection = null
        audioServiceContext = null
        audioCaptureThread = null
        audioCaptureHandler = null
        synchronized(captureLock) {
            pixelCopyInFlight = false
            pixelCopyThread = null
        }
        audioCaptureFailed = false
    }

    /**
     * Adds the recorded video to the device's MediaStore so it appears in the Gallery app.
     * Uses MediaStore API for Android 10+ (Q) or the legacy approach for older versions.
     */
    private fun addVideoToGallery(context: Context, videoFile: File): Boolean {
        val resolver = context.contentResolver
        var uri: Uri? = null
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, videoFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/TurtleLauncher")
                    // Keep the item hidden until the complete MP4 has been copied. Marking it
                    // published at insert time could leave a visible zero-byte gallery entry.
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val insertedUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IOException("Failed to create MediaStore entry for video")
            uri = insertedUri

            val outputStream = resolver.openOutputStream(insertedUri, "w")
                ?: throw IOException("Could not open MediaStore output stream for $insertedUri")
            outputStream.use { output ->
                FileInputStream(videoFile).use { input -> input.copyTo(output) }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val published = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                if (resolver.update(insertedUri, published, null, null) <= 0) {
                    throw IOException("Could not publish recorded video in MediaStore: $insertedUri")
                }
            }

            Logging.i(TAG, "Video added to gallery: $insertedUri")
            true
        } catch (t: Throwable) {
            uri?.let { insertedUri ->
                try { resolver.delete(insertedUri, null, null) }
                catch (deleteError: Throwable) { Logging.e(TAG, "Could not remove incomplete gallery item", deleteError) }
            }
            Logging.e(TAG, "Error adding video to gallery", t)
            false
        }
    }

    /** Finds the SurfaceView/TextureView Minecraft actually renders into - see MinecraftGLSurface.start(),
     * which adds one or the other as a real sibling child under its own parent depending on the
     * "Alternate Surface" renderer setting. */
    private fun findRenderSurface(root: View): View? {
        if (root is SurfaceView || root is TextureView) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findRenderSurface(root.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    private fun scaleDimension(px: Int): Int {
        val percent = AllSettings.recordingResolutionScale.getValue().coerceIn(50, 100)
        return px * percent / 100
    }

    /** H.264 encoders require even width/height. */
    private fun evenify(px: Int): Int = if (px % 2 == 0) px else px - 1
}
