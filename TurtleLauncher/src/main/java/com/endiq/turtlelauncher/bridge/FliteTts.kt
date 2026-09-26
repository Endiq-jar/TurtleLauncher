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

package com.endiq.turtlelauncher.bridge

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.content.Context
import com.endiq.turtlelauncher.context.GlobalContext
import com.endiq.turtlelauncher.utils.logging.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Android TTS backend for the game narration feature, replacing the missing flite speech engine
 * Reference from FoldCraftLauncher PR #1821 (FCL/src/main/java/com/mio/flite/FliteTts.kt, GPL-3.0)
 * https://github.com/FCL-Team/FoldCraftLauncher/pull/1821
 */
object FliteTts {
    private const val TAG = "FliteTTS"
    /** Quick-determination window for init: with no engine, onInit(ERROR) arrives almost immediately; a timeout means the engine is still cold-starting */
    private const val FAST_CHECK_SECONDS = 2L

    private const val STATE_UNINIT = 0
    private const val STATE_INITIALIZING = 1
    private const val STATE_READY = 2
    private const val STATE_FAILED = 3

    // TextToSpeech must be built and called back on a thread with a Looper; the game-side calling thread has none
    private val ttsThread = HandlerThread(TAG)
        .apply { start() }

    // Read/written only under the object lock, sharing one lock with the state transitions
    private val pending = ArrayDeque<PendingSpeech>()

    @Volatile
    private var state = STATE_UNINIT

    @Volatile
    private var tts: TextToSpeech? = null

    @Volatile
    private var readySignal = CountDownLatch(1)

    @Volatile
    private var candidateEngines: List<String> = emptyList()
    private val triedEngines: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val utteranceCounter = AtomicLong()

    /** Texts that arrive before the engine is ready; replayed in order once it is */
    private data class PendingSpeech(val text: String, val gain: Float)

    /** Initializes the TTS engine; optimistically returns true during a cold start — early utterances are replayed in order once ready */
    @JvmStatic
    fun init(): Boolean {
        synchronized(this) {
            when (state) {
                STATE_READY -> return true
                STATE_FAILED -> return false
                STATE_INITIALIZING -> return true
                else -> {
                    readySignal = CountDownLatch(1)
                    state = STATE_INITIALIZING
                    val signal = readySignal
                    Handler(ttsThread.looper).post { constructTts(signal, null) }
                }
            }
        }
        // The object lock must not be held during quick determination: the onInit callback and the tts assignment rely on progress outside the lock
        val arrived = try {
            readySignal.await(FAST_CHECK_SECONDS, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            false
        }
        return if (arrived) state == STATE_READY else true
    }

    /** Queues a UTF-8 text for speaking and returns immediately; -1 when unavailable or the text is invalid */
    @JvmStatic
    fun speak(message: ByteArray, gain: Float): Float {
        if (state == STATE_FAILED) return -1f
        val text = runCatching { String(message, Charsets.UTF_8) }.getOrNull() ?: return -1f
        if (text.isBlank()) return -1f
        synchronized(this) {
            val instance = tts
            if (state == STATE_READY && instance != null) {
                enqueue(instance, text, gain)
            } else {
                pending.addLast(PendingSpeech(text, gain))
            }
        }
        return 0f
    }

    /** Stops the current utterance immediately and drops all queued texts; the cut-off entry point when the game interrupts narration */
    @JvmStatic
    fun cancel() {
        synchronized(this) { pending.clear() }
        tts?.runCatching { stop() }
    }

    /** Releases the TTS engine and drops all queued texts */
    @JvmStatic
    @Synchronized
    fun shutdown() {
        state = STATE_UNINIT
        releaseInstance()
        pending.clear()
        readySignal.countDown()
        triedEngines.clear()
        candidateEngines = emptyList()
    }

    private fun enqueue(instance: TextToSpeech, text: String, gain: Float) {
        val utteranceId = "zl-flite-${utteranceCounter.incrementAndGet()}"
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, gain) }
        if (instance.speak(text, TextToSpeech.QUEUE_ADD, params, utteranceId) != TextToSpeech.SUCCESS) {
            Logger.error(TAG, "speak enqueue failed: $utteranceId")
        }
    }

    /** Must be called under the object lock: replays the pre-ready queued texts into the TTS queue in order */
    private fun drainPending() {
        val instance = tts ?: return
        while (pending.isNotEmpty()) {
            val speech = pending.removeFirst()
            enqueue(instance, speech.text, speech.gain)
        }
    }

    private fun constructTts(signal: CountDownLatch, engine: String?) {
        val ref = AtomicReference<TextToSpeech>()
        try {
            val listener = TextToSpeech.OnInitListener { code ->
                if (state == STATE_INITIALIZING) {
                    if (code == TextToSpeech.SUCCESS) {
                        synchronized(this) {
                            if (state == STATE_INITIALIZING) {
                                ref.get()?.let { tts = it }
                                state = STATE_READY
                                drainPending()
                            }
                        }
                        Logger.info(TAG, "TextToSpeech ready (engine=${engine ?: "default"})")
                        signal.countDown()
                    } else {
                        Logger.error(TAG, "engine init failed: engine=${engine ?: "default"} status=$code")
                        // Non-terminal state: don't release the waiters while candidate engines remain; the fallback result decides
                        tryNextEngine(signal, ref.get(), engine)
                    }
                }
                // Stale callbacks (shutdown / re-initialized in the meantime) are ignored outright
            }
            val context = GlobalContext.applicationContext
            val instance = if (engine == null) {
                TextToSpeech(context, listener)
            } else {
                TextToSpeech(context, listener, engine)
            }
            ref.set(instance)
            instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {}

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Logger.error(TAG, "utterance failed: $utteranceId")
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {}
            })
            synchronized(this) {
                if (state != STATE_INITIALIZING) {
                    // Already shutdown during construction: release immediately to avoid a leak
                    instance.shutdown()
                    return
                }
                tts = instance
                candidateEngines = runCatching { instance.engines.map { it.name } }.getOrDefault(emptyList())
            }
        } catch (e: Throwable) {
            Logger.error(TAG, "create TextToSpeech failed", e)
            tryNextEngine(signal, null, engine)
        }
    }

    /** After the current engine fails to initialize, try other installed speech engines one by one */
    private fun tryNextEngine(signal: CountDownLatch, failed: TextToSpeech?, failedEngine: String?) {
        triedEngines.add(failedEngine ?: "default")
        val next = candidateEngines.firstOrNull { it !in triedEngines }
        if (next == null) {
            state = STATE_FAILED
            pending.clear()
            Logger.error(TAG, "no usable TTS engine (tried=$triedEngines), narrator disabled")
            signal.countDown()
            return
        }
        Logger.info(TAG, "falling back to TTS engine: $next")
        Handler(ttsThread.looper).post {
            failed?.runCatching { shutdown() }
            constructTts(signal, next)
        }
    }

    private fun releaseInstance() {
        val instance = tts ?: return
        tts = null
        Handler(ttsThread.looper).post {
            try {
                instance.shutdown()
            } catch (e: Throwable) {
                Logger.error(TAG, "shutdown TextToSpeech failed", e)
            }
        }
    }
}
