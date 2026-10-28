package com.endiq.turtlelauncher.feature.benchmark

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import com.endiq.turtlelauncher.feature.log.Logging
import kotlin.math.max

/**
 * A short, self-contained GPU benchmark used to decide graphics settings.
 *
 * Why an offscreen EGL probe instead of timing the game: the launcher needs a
 * number *before* a world loads (the auto optimizer runs at launch), and a real
 * in-game FPS sample would need a running Minecraft instance plus user patience.
 * A 1-2 second fill-rate test in a pbuffer measures the same thing the settings
 * care about - how many shaded pixels per second this GPU can push - without
 * touching the game, any renderer, or the user's world.
 *
 * What it does: renders a full-screen quad with a deliberately expensive fragment
 * shader (several sin/pow/mod operations per pixel) for a fixed number of frames
 * at a fixed, non-native size, and reports the achieved throughput. The work per
 * pixel is fixed, so the score is comparable across devices and resolutions.
 *
 * The score is intentionally coarse (roughly 0-1000+). It is only ever compared
 * against [com.endiq.turtlelauncher.launch.GraphicsTuning]'s tier boundaries, so a
 * slightly different score on a different driver cannot flip a decision on its own.
 *
 * Call [run] from a background thread: it blocks, and it creates its own EGL
 * context (never touching whatever context the caller may be using).
 */
object GraphicsBenchmark {
    private const val TAG = "GraphicsBenchmark"

    /** Keep the probe resolution fixed so scores compare across phones. */
    private const val WIDTH = 480
    private const val HEIGHT = 270
    private const val WARMUP_FRAMES = 3
    private const val TIMED_FRAMES = 24

    data class Result(
        /** Higher is faster; normalised to roughly 1000 on a 2020-era flagship. */
        val score: Int,
        /** Raw megapixels per second, for the log/UI detail line. */
        val megapixelsPerSecond: Int,
        /** Milliseconds the timed section took. */
        val durationMs: Long,
        val glRenderer: String?,
        val glVersion: String?,
        /** True when the probe could not run at all (no GLES2 context, driver refusal). */
        val failed: Boolean = false,
        val failureReason: String? = null
    ) {
        val tier: String
            get() = when {
                failed -> "unavailable"
                score < 150 -> "low"
                score < 600 -> "mid"
                else -> "high"
            }
    }

    private const val VERTEX_SHADER = """
        attribute vec2 position;
        void main() {
            gl_Position = vec4(position, 0.0, 1.0);
        }
    """

    // Fixed, resolution-independent per-pixel cost. Kept in GLSL ES 1.00 (GLES2) so it
    // runs on every device this launcher supports, including old GL4ES devices.
    private const val FRAGMENT_SHADER = """
        precision mediump float;
        uniform float uSeed;
        void main() {
            vec2 uv = gl_FragCoord.xy * 0.01 + uSeed;
            float acc = 0.0;
            for (int i = 0; i < 8; i++) {
                float fi = float(i) + 1.0;
                acc += sin(uv.x * fi) * cos(uv.y * fi) / fi;
                acc += pow(abs(sin(uv.x * uv.y * fi)), 0.5) * 0.05;
                acc += mod(uv.x * 97.0 + uv.y * 31.0 + acc, 1.0) * 0.01;
            }
            gl_FragColor = vec4(acc, acc * 0.5, 1.0 - acc, 1.0);
        }
    """

    /**
     * Run the benchmark. Returns a `failed` result instead of throwing when the
     * device cannot create a GLES2 context at all (some runtime-generated renderer
     * plugin combinations do refuse it).
     */
    fun run(): Result {
        val previousDisplay = EGL14.eglGetCurrentDisplay()
        val previousContext = EGL14.eglGetCurrentContext()
        val previousRead = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
        val previousDraw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var context: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        var glRenderer: String? = null
        var glVersion: String? = null
        var initialized = false
        return try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return failure("eglGetDisplay returned no display")
            val version = IntArray(2)
            if (display != previousDisplay) {
                if (!EGL14.eglInitialize(display, version, 0, version, 1)) return failure("eglInitialize failed")
                initialized = true
            }

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 0, EGL14.EGL_STENCIL_SIZE, 0,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) ||
                numConfigs[0] == 0
            ) return failure("no EGL config with a pbuffer surface")
            val config = configs[0] ?: return failure("eglChooseConfig returned a null config")

            val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (context == EGL14.EGL_NO_CONTEXT) return failure("could not create a GLES2 context")

            val surfaceAttribs = intArrayOf(
                EGL14.EGL_WIDTH, WIDTH, EGL14.EGL_HEIGHT, HEIGHT, EGL14.EGL_NONE
            )
            surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttribs, 0)
            if (surface == EGL14.EGL_NO_SURFACE) return failure("could not create a pbuffer surface")
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                return failure("eglMakeCurrent failed")
            }

            glRenderer = GLES20.glGetString(GLES20.GL_RENDERER)
            glVersion = GLES20.glGetString(GLES20.GL_VERSION)
            Logging.i(TAG, "Probe context: $glRenderer / $glVersion")

            val program = buildProgram() ?: return failure("shader compilation failed")
            GLES20.glUseProgram(program)
            val seedLocation = GLES20.glGetUniformLocation(program, "uSeed")

            val quad = java.nio.ByteBuffer.allocateDirect(8 * 4)
                .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
            quad.put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)).position(0)
            val positionLocation = GLES20.glGetAttribLocation(program, "position")
            GLES20.glEnableVertexAttribArray(positionLocation)
            GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 0, quad)

            GLES20.glViewport(0, 0, WIDTH, HEIGHT)
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)

            fun drawFrames(count: Int, seed: Float) {
                for (frame in 0 until count) {
                    GLES20.glUniform1f(seedLocation, seed + frame * 0.01f)
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                }
            }

            // Warm-up: first frames pay shader/pipeline setup and thermal ramp-up.
            drawFrames(WARMUP_FRAMES, 0f)
            GLES20.glFinish()

            val startedNs = System.nanoTime()
            drawFrames(TIMED_FRAMES, 1f)
            GLES20.glFinish() // measure completed work, not queued work
            val durationNs = System.nanoTime() - startedNs

            GLES20.glDisableVertexAttribArray(positionLocation)
            GLES20.glDeleteProgram(program)

            val safeDurationNs = max(1L, durationNs)
            val durationMs = max(1L, safeDurationNs / 1_000_000L)
            val pixels = WIDTH.toLong() * HEIGHT.toLong() * TIMED_FRAMES
            val megapixelsPerSecond = (pixels * 1000L / safeDurationNs).toInt()
            Result(
                // ~100 MPix/s of this shader is a low-end device; a 2020 flagship lands
                // around 1000+. The constant only sets the scale, tier boundaries do the rest.
                score = (megapixelsPerSecond / 10).coerceIn(0, 5000),
                megapixelsPerSecond = megapixelsPerSecond,
                durationMs = durationMs,
                glRenderer = glRenderer,
                glVersion = glVersion
            )
        } catch (e: Throwable) {
            Logging.w(TAG, "Graphics benchmark failed: ${e.message}")
            failure(e.message ?: e.javaClass.simpleName, glRenderer, glVersion)
        } finally {
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
                if (previousDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(previousDisplay, previousDraw, previousRead, previousContext)
                }
                if (initialized) EGL14.eglTerminate(display)
            }
        }
    }

    private fun buildProgram(): Int? {
        fun compile(type: Int, source: String): Int? {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                Logging.w(TAG, "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
                GLES20.glDeleteShader(shader)
                return null
            }
            return shader
        }

        val vertex = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER) ?: return null
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER) ?: run {
            GLES20.glDeleteShader(vertex)
            return null
        }
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        if (status[0] == 0) {
            Logging.w(TAG, "Program link failed: ${GLES20.glGetProgramInfoLog(program)}")
            GLES20.glDeleteProgram(program)
            return null
        }
        return program
    }

    private fun failure(
        reason: String,
        glRenderer: String? = null,
        glVersion: String? = null
    ): Result = Result(
        score = 0,
        megapixelsPerSecond = 0,
        durationMs = 0,
        glRenderer = glRenderer,
        glVersion = glVersion,
        failed = true,
        failureReason = reason
    )

    /** Convenience for the settings UI: "412 (mid) - 137 MPix/s, 1080x720 probe". */
    fun describe(result: Result): String = if (result.failed) {
        "unavailable (${result.failureReason})"
    } else {
        "${result.score}, ${result.tier} - ${result.megapixelsPerSecond} MPix/s in ${result.durationMs} ms"
    }

    /** Rounded score for display. */
    fun displayScore(result: Result): Int = if (result.failed) 0 else result.score
}
