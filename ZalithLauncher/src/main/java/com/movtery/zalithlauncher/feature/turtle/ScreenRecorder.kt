package com.movtery.zalithlauncher.feature.turtle

import android.app.Activity
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.task.TaskExecutors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TurtleLauncher: in-game screen recording (roadmap item 22 - "controls shown live, hidden in
 * the final video").
 *
 * How the "hidden in the video" part actually works: this records ONLY the Minecraft render
 * surface (the SurfaceView or TextureView MinecraftGLSurface hands off to the native
 * renderer - see that class's start()), never the Activity's full window. The touch
 * controls/HUD/record button are separate sibling Views layered on top of that surface in the
 * same window, added by ControlLayout/GameMenuViewWrapper. PixelCopy(SurfaceView) and
 * TextureView.getBitmap() each only ever read the buffer belonging to that one View - they
 * have no way to see sibling views even if they wanted to. So "controls visible on screen, gone
 * from the recording" isn't a filter being applied - it's a direct consequence of which View's
 * pixels are being read, the same property ScreenshotHelper already relies on for the SurfaceView
 * case. This is a real, standard Android technique (not MediaProjection, which mirrors the whole
 * physical display compositor output including overlay windows and can't selectively exclude a
 * View - there's no public API for that).
 *
 * Frame delivery: a background thread requests a frame (PixelCopy or getBitmap) on a timer at
 * the configured frame rate, draws each bitmap onto the MediaCodec encoder's input Surface via
 * Canvas, and periodically drains encoder output into a MediaMuxer writing an .mp4. This is a
 * software blit per frame rather than a zero-copy GPU path (which would mean hooking into
 * MinecraftGLSurface's native EGL rendering directly - a much larger, riskier change to code
 * this project can't rebuild/test on real hardware from here) - fine for a gameplay-recording
 * feature at moderate resolutions/frame rates, not intended to compete with a dedicated
 * screen-recorder app's encoding pipeline.
 *
 * Deliberately silent (no audio track) in this first pass: Android audio capture options here
 * are either (a) MediaProjection's playback-capture API, which requires the same whole-display
 * capture consent flow this class specifically avoids needing, or (b) recording the mic, which
 * isn't what anyone wants for gameplay footage. Getting internal game audio without pulling in
 * MediaProjection needs more investigation than fits this pass - the video-only recorder is a
 * complete, useful feature on its own, and this can be extended with audio later without
 * changing anything about the video path above.
 */
object ScreenRecorder {
    private const val TAG = "ScreenRecorder"
    private const val MIME_TYPE = "video/avc" // H.264 - broadest device/player compatibility
    private val nameFormat = SimpleDateFormat("yyyy-MM-dd_HH.mm.ss", Locale.US)

    private val isRecordingFlag = AtomicBoolean(false)
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var inputSurface: Surface? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private val bufferInfo = MediaCodec.BufferInfo()

    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private var frameBitmap: Bitmap? = null
    private var startTimeNanos = 0L
    private var frameIntervalMs = 1000L / 30L
    private var outputFile: File? = null
    private var targetSurfaceView: SurfaceView? = null
    private var targetTextureView: TextureView? = null
    private var maxDurationMs = 0L

    fun isRecording(): Boolean = isRecordingFlag.get()

    /** @return elapsed recording time in whole seconds, or 0 if not currently recording. */
    fun getElapsedSeconds(): Long {
        if (!isRecordingFlag.get() || startTimeNanos == 0L) return 0L
        return (System.nanoTime() - startTimeNanos) / 1_000_000_000L
    }

    fun toggle(activity: Activity) {
        if (isRecordingFlag.get()) stop(activity) else start(activity)
    }

    fun start(activity: Activity) {
        if (isRecordingFlag.get()) return

        val renderSurfaceView = findRenderSurface(activity.window.decorView)
        if (renderSurfaceView == null) {
            Toast.makeText(activity, "Recording unavailable - couldn't locate the game surface", Toast.LENGTH_SHORT).show()
            return
        }

        val width = evenify(scaleDimension(renderSurfaceView.width))
        val height = evenify(scaleDimension(renderSurfaceView.height))
        if (width <= 0 || height <= 0) {
            Toast.makeText(activity, "Recording unavailable - game surface isn't ready yet", Toast.LENGTH_SHORT).show()
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
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            codec.start()

            val dir = File(activity.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "recordings").apply { mkdirs() }
            val file = File(dir, "turtle_${nameFormat.format(Date())}.mp4")
            val mediaMuxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            encoder = codec
            inputSurface = surface
            muxer = mediaMuxer
            outputFile = file
            trackIndex = -1
            muxerStarted = false

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

            val thread = HandlerThread("ScreenRecorder-Capture").apply { start() }
            captureThread = thread
            captureHandler = Handler(thread.looper)

            startTimeNanos = System.nanoTime()
            isRecordingFlag.set(true)
            captureHandler?.post(captureLoop(activity))

            Toast.makeText(activity, "Recording started", Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Logging.e(TAG, "Failed to start recording", t)
            releaseQuietly()
            Toast.makeText(activity, "Recording failed to start", Toast.LENGTH_SHORT).show()
        }
    }

    fun stop(activity: Activity?) {
        if (!isRecordingFlag.compareAndSet(true, false)) return

        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null

        // Drain and tear down on a background thread - MediaCodec/MediaMuxer teardown can
        // block briefly and this is invoked directly from a UI click listener.
        TaskExecutors.getDefault().execute {
            try {
                drainEncoder(endOfStream = true)
            } catch (t: Throwable) {
                Logging.e(TAG, "Error draining encoder on stop", t)
            } finally {
                val savedFile = outputFile
                releaseQuietly()
                TaskExecutors.getAndroidUI().execute {
                    if (activity != null) {
                        val message = if (savedFile != null && savedFile.length() > 0)
                            "Recording saved: ${savedFile.name}" else "Recording failed"
                        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun captureLoop(activity: Activity): Runnable = object : Runnable {
        override fun run() {
            if (!isRecordingFlag.get()) return

            if (maxDurationMs > 0 && (System.nanoTime() - startTimeNanos) / 1_000_000L >= maxDurationMs) {
                TaskExecutors.getAndroidUI().execute { stop(activity) }
                return
            }

            try {
                captureFrame()
            } catch (t: Throwable) {
                Logging.e(TAG, "Frame capture failed, stopping recording", t)
                TaskExecutors.getAndroidUI().execute { stop(activity) }
                return
            }

            captureHandler?.postDelayed(this, frameIntervalMs)
        }
    }

    private fun captureFrame() {
        val bitmap = frameBitmap ?: return
        val surfaceView = targetSurfaceView
        val textureView = targetTextureView

        when {
            surfaceView != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                // PixelCopy is async but this loop only ever has one request in flight since
                // each tick waits for the previous drawFrame() before scheduling the next.
                PixelCopy.request(surfaceView, bitmap, { result ->
                    if (result == PixelCopy.SUCCESS) drawFrame(bitmap)
                }, captureHandler ?: return)
            }
            textureView != null -> {
                textureView.getBitmap(bitmap)
                drawFrame(bitmap)
            }
            else -> { /* no capturable surface this tick - skip the frame rather than crash */ }
        }
    }

    private fun drawFrame(bitmap: Bitmap) {
        val surface = inputSurface ?: return
        try {
            val canvas = surface.lockCanvas(null)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            surface.unlockCanvasAndPost(canvas)
        } catch (t: Throwable) {
            Logging.e(TAG, "Failed to draw frame to encoder surface", t)
        }
        drainEncoder(endOfStream = false)
    }

    private fun drainEncoder(endOfStream: Boolean) {
        val codec = encoder ?: return
        val mediaMuxer = muxer ?: return

        if (endOfStream) {
            try { codec.signalEndOfInputStream() } catch (t: Throwable) { Logging.e(TAG, "signalEndOfInputStream failed", t) }
        }

        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) throw IllegalStateException("Format changed twice")
                    trackIndex = mediaMuxer.addTrack(codec.outputFormat)
                    mediaMuxer.start()
                    muxerStarted = true
                }
                outputIndex >= 0 -> {
                    val encodedData = codec.getOutputBuffer(outputIndex)
                    if (encodedData != null && bufferInfo.size > 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        mediaMuxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
            }
        }
    }

    private fun releaseQuietly() {
        try { encoder?.stop() } catch (t: Throwable) { Logging.e(TAG, "encoder.stop() failed", t) }
        try { encoder?.release() } catch (t: Throwable) { Logging.e(TAG, "encoder.release() failed", t) }
        try { if (muxerStarted) muxer?.stop() } catch (t: Throwable) { Logging.e(TAG, "muxer.stop() failed", t) }
        try { muxer?.release() } catch (t: Throwable) { Logging.e(TAG, "muxer.release() failed", t) }
        try { inputSurface?.release() } catch (t: Throwable) { Logging.e(TAG, "inputSurface.release() failed", t) }
        frameBitmap?.recycle()

        encoder = null
        muxer = null
        inputSurface = null
        muxerStarted = false
        trackIndex = -1
        frameBitmap = null
        targetSurfaceView = null
        targetTextureView = null
        outputFile = null
        startTimeNanos = 0L
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
