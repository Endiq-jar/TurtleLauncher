package com.endiq.turtlelauncher.feature.ai

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import com.endiq.turtlelauncher.feature.log.Logging
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The live voice conversation: you talk, Turtle AI talks back.
 *
 * This is the Gemini **Live API**, which is not the request/response shape everything else in
 * [TurtleAiGemini] uses - it is one long-lived WebSocket that carries chunks of audio in both
 * directions:
 *
 *  - the client opens the socket, sends a `setup` frame naming the model, the voice and the
 *    system instruction;
 *  - then it streams microphone audio (`realtimeInput`) as 16 kHz 16-bit mono PCM, and plays
 *    the model's 24 kHz PCM replies from `serverContent.modelTurn.parts[].inlineData` as they
 *    arrive, so it can start speaking before the sentence is finished;
 *  - the server also sends `inputTranscription` / `outputTranscription` (what it heard and what
 *    it said), `interrupted` when the user talks over the reply, and `turnComplete` when the
 *    reply ended - each of which is surfaced to the caller through [Listener].
 *
 * ## Threading and lifecycle
 *
 * One session owns: the WebSocket, one capture thread (blocking `AudioRecord.read`) and one
 * playback `AudioTrack`. [stop] is idempotent and safe from any thread; it always releases the
 * microphone, which is the part that must never be leaked - a hanging `AudioRecord` keeps the
 * mic indicator on and blocks other apps.
 *
 * ## Failure policy
 *
 * Nothing throws out of here. A refused microphone, a bad key, a dropped socket or an
 * unsupported model all end up in [Listener.onEnded] with a reason the UI can show. The caller
 * is expected to treat the session as best-effort, exactly like the rest of Turtle AI.
 */
object TurtleAiVoice {

    private const val TAG = "TurtleAiVoice"

    private const val WS_URL =
        "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

    /** What the microphone sends and what the speaker receives (fixed by the Live API). */
    private const val INPUT_SAMPLE_RATE = 16_000
    private const val OUTPUT_SAMPLE_RATE = 24_000

    /** ~100 ms of audio per frame: small enough to feel live, large enough to be efficient. */
    private const val FRAME_MS = 100

    /**
     * Called on background threads owned by the session; the UI wrapper marshals to the main
     * thread.
     */
    interface Listener {
        /** The socket is open and the setup frame was accepted: safe to start talking. */
        fun onReady(modelId: String) {}
        /** A chunk of what the model is saying, as it is said. */
        fun onHeard(text: String) {}
        /** A chunk of what the model is saying. */
        fun onSaid(text: String) {}
        /** The model stopped; a new turn can start. */
        fun onTurnComplete() {}
        /** The user talked over the reply and it was cut off. */
        fun onInterrupted() {}
        /** The session is over. [reason] is null for a normal stop. */
        fun onEnded(reason: String?)
    }

    /**
     * Starts a session. Returns the session object straight away (the socket connects in the
     * background), or null when there is no key to use.
     */
    @JvmStatic
    fun start(listener: Listener): Session? {
        val key = TurtleAiGemini.apiKey()
        if (key.isEmpty()) {
            listener.onEnded("no API key")
            return null
        }
        if (!TurtleAiGemini.isConfigured()) {
            listener.onEnded("the AI brain is switched off")
            return null
        }
        val session = Session(listener, key)
        session.begin()
        return session
    }

    /** One live conversation. Created by [start]; call [stop] to end it. */
    class Session internal constructor(
        private val listener: Listener,
        private val key: String
    ) {
        private val closed = AtomicBoolean(false)

        /** The live models to try, best first (see TurtleAiModels) - the same fallback idea as
         *  everywhere else, applied to the one path that is a socket rather than a request. */
        private val chain = TurtleAiModels.chain(TurtleAiModels.Task.LIVE)
        private var attempt = 0
        private var socket: WebSocket? = null
        private var captureThread: Thread? = null
        private var record: AudioRecord? = null
        @Volatile
        private var track: AudioTrack? = null
        @Volatile
        private var modelId: String = ""
        /** True once the server accepted the setup frame: from then on the session is live and
         *  a disconnect is not retried (the user is mid-sentence, not waiting to connect). */
        private val ready = AtomicBoolean(false)
        private val startedAt = System.currentTimeMillis()

        private val client: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)   // a live socket idles between turns
                .pingInterval(20, TimeUnit.SECONDS)
                .build()
        }

        internal fun begin() = openCurrent()

        private fun openCurrent() {
            val model = chain.getOrNull(attempt)
            if (model == null) {
                fail("no live voice model available")
                return
            }
            modelId = model
            // The Live API takes the key as a query parameter. The URL is never logged, so the
            // key cannot end up in a crash report or a shared log.
            val request = Request.Builder().url("$WS_URL?key=" + key).build()
            socket = client.newWebSocket(request, SocketListener(model))
        }

        /**
         * A model refused the connection: rest it and try the next one, exactly like the
         * request-based paths do. Only before the session became live - once it is running, a
         * dropped connection is reported to the user instead of silently restarting.
         */
        private fun tryNextModel(model: String): Boolean {
            TurtleAiModels.cool(model, 10 * 60_000L)
            if (ready.get() || attempt + 1 >= chain.size) return false
            attempt++
            Logging.i(TAG, "Live model " + model + " unavailable - trying the next one")
            openCurrent()
            return true
        }

        /** Ends the session. Safe to call twice, or after the socket already died. */
        fun stop() {
            if (!closed.compareAndSet(false, true)) return
            runCatching { socket?.close(1000, "closed by user") }
            socket = null
            runCatching { captureThread?.interrupt() }
            captureThread = null
            releaseAudio()
            Logging.i(TAG, "Live session ended after " +
                ((System.currentTimeMillis() - startedAt) / 1000) + "s")
        }

        private fun releaseAudio() {
            runCatching { record?.stop() }
            runCatching { record?.release() }
            record = null
            runCatching { track?.stop() }
            runCatching { track?.release() }
            track = null
        }

        private fun fail(reason: String) {
            if (closed.compareAndSet(false, true)) {
                releaseAudio()
                listener.onEnded(reason)
            }
        }

        // ── The socket ──────────────────────────────────────────────────────────────

        private inner class SocketListener(private val model: String) : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(setupFrame(model))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleFrame(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // The Live API sends JSON as text frames, but a binary frame is valid too.
                handleFrame(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Logging.w(TAG, "Live socket failed (HTTP ${response?.code ?: 0})", t)
                if (closed.get()) return
                // A model this key cannot use is the usual cause, so try the next one before
                // giving up on the conversation entirely.
                if (tryNextModel(model)) return
                socket = null
                fail("connection failed" + (response?.code?.let { " (HTTP $it)" } ?: ""))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closed.get()) fail("closed by the server")
            }
        }

        private fun setupFrame(model: String): String {
            val setup = JsonObject().apply {
                addProperty("model", "models/$model")
                add("generationConfig", JsonObject().apply {
                    add("responseModalities", com.google.gson.JsonArray().apply { add("AUDIO") })
                    add("speechConfig", JsonObject().apply {
                        add("voiceConfig", JsonObject().apply {
                            add("prebuiltVoiceConfig", JsonObject().apply {
                                addProperty("voiceName", TurtleAiGemini.voiceName())
                            })
                        })
                    })
                    // What the user hears is also what the transcript shows.
                    add("inputAudioTranscription", JsonObject())
                    add("outputAudioTranscription", JsonObject())
                })
                add("systemInstruction", JsonObject().apply {
                    add("parts", com.google.gson.JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("text", voiceSystemPrompt())
                        })
                    })
                })
            }
            return JsonObject().apply { add("setup", setup) }.toString()
        }

        /**
         * The spoken variant of the Turtle AI persona: the same identity and honesty rules,
         * minus the parts that only make sense on a screen (markdown, code blocks, long
         * lists). Speech has no scrollback - a short answer that gets repeated is better than
         * a complete one that nobody can skim.
         */
        private fun voiceSystemPrompt(): String = buildString {
            append(TurtleAiPrompt.IDENTITY).append("\n\n")
            append(TurtleAiPrompt.REASONING).append("\n\n")
            append(TurtleAiPrompt.KNOWLEDGE_BASE).append("\n\n")
            append(TurtleAiPrompt.ANTI_HALLUCINATION).append("\n\n")
            append(TurtleAiPrompt.LANGUAGE).append("\n\n")
            append(
                """
                This is a spoken conversation, not typing. Answer in a few short sentences -
                two or three is usually right - and stop. Do not read out code, file paths,
                setting keys, URLs or markdown; mention them the way a person would say them
                out loud, and offer to show the details in the chat instead. If the user asks
                for something long, give the short version verbally and say the full one is in
                the chat. Expect to be interrupted: if you are cut off, listen and respond to
                the new question instead of finishing the old answer.
                """.trimIndent()
            )
        }

        // ── Incoming frames ─────────────────────────────────────────────────────────

        private fun handleFrame(text: String) {
            if (closed.get()) return
            val root = runCatching { JsonParser.parseString(text) }
                .getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: return

            if (root.has("setupComplete")) {
                ready.set(true)
                listener.onReady(modelId)
                startCapture()
                return
            }
            root.getAsJsonObject("serverContent")?.let { handleServerContent(it) }
        }

        private fun handleServerContent(content: JsonObject) {
            content.getAsJsonObject("inputTranscription")
                ?.get("text")?.takeIf { it.isJsonPrimitive }?.asString
                ?.takeIf { it.isNotBlank() }?.let { listener.onHeard(it) }

            content.getAsJsonObject("outputTranscription")
                ?.get("text")?.takeIf { it.isJsonPrimitive }?.asString
                ?.takeIf { it.isNotBlank() }?.let { listener.onSaid(it) }

            content.getAsJsonObject("modelTurn")
                ?.getAsJsonObject("content")
                ?.getAsJsonArray("parts")
                ?.forEach { part ->
                    val obj = runCatching { part.asJsonObject }.getOrNull() ?: return@forEach
                    val inline = obj.getAsJsonObject("inlineData")
                        ?: obj.getAsJsonObject("inline_data") ?: return@forEach
                    val data = inline.get("data")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: return@forEach
                    val bytes = runCatching {
                        android.util.Base64.decode(data, android.util.Base64.DEFAULT)
                    }.getOrNull() ?: return@forEach
                    play(bytes)
                }

            if (content.get("interrupted")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) {
                // The user started talking: drop whatever is still queued and let them.
                runCatching { track?.pause() }
                runCatching { track?.flush() }
                listener.onInterrupted()
            }
            if (content.get("turnComplete")?.takeIf { it.isJsonPrimitive }?.asBoolean == true) {
                listener.onTurnComplete()
            }
        }

        // ── Microphone ──────────────────────────────────────────────────────────────

        @SuppressLint("MissingPermission")
        private fun startCapture() {
            if (closed.get()) return
            val minBuffer = AudioRecord.getMinBufferSize(
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferSize = maxOf(minBuffer, INPUT_SAMPLE_RATE / 4)   // >= 250 ms of audio
            val recorder = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    INPUT_SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }.onFailure { Logging.w(TAG, "Couldn't create AudioRecord", it) }
                .getOrNull()

            if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
                runCatching { recorder?.release() }
                fail("microphone unavailable")
                return
            }
            record = recorder
            val thread = Thread({ captureLoop(recorder) }, "TurtleAiVoiceCapture")
            thread.isDaemon = true
            captureThread = thread
            thread.start()
        }

        /**
         * The blocking half of the capture: read one frame, send it, repeat. Runs on its own
         * thread because [AudioRecord.read] blocks, and stops as soon as the session closes or
         * the socket refuses a frame.
         */
        private fun captureLoop(recorder: AudioRecord) {
            val frameBytes = INPUT_SAMPLE_RATE * 2 * FRAME_MS / 1000
            val buffer = ByteArray(frameBytes)
            if (!runCatching { recorder.startRecording() }.isSuccess) {
                fail("couldn't start recording")
                return
            }
            while (!closed.get() && !Thread.currentThread().isInterrupted) {
                val read = runCatching { recorder.read(buffer, 0, buffer.size) }.getOrDefault(-1)
                if (read < 0) break
                if (read == 0) continue
                val payload = if (read == buffer.size) buffer else buffer.copyOf(read)
                val frame = JsonObject().apply {
                    add("realtimeInput", JsonObject().apply {
                        add("audio", JsonObject().apply {
                            addProperty("mimeType", "audio/pcm;rate=$INPUT_SAMPLE_RATE")
                            addProperty(
                                "data",
                                android.util.Base64.encodeToString(payload, android.util.Base64.NO_WRAP)
                            )
                        })
                    })
                }
                if (socket?.send(frame.toString()) != true) break
            }
            // The socket went away (or the user stopped): give the microphone back at once.
            releaseAudio()
            if (!closed.get()) fail("microphone stopped")
        }

        // ── Speaker ─────────────────────────────────────────────────────────────────

        /** Plays one chunk of 24 kHz PCM, creating the track on the first chunk. */
        private fun play(pcm: ByteArray) {
            if (closed.get()) return
            var player = track
            if (player == null) {
                player = runCatching {
                    val minBuffer = AudioTrack.getMinBufferSize(
                        OUTPUT_SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(OUTPUT_SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(maxOf(minBuffer, OUTPUT_SAMPLE_RATE / 2))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                }.onFailure { Logging.w(TAG, "Couldn't create AudioTrack", it) }
                    .getOrNull()
                if (player == null) return
                runCatching { player.play() }
                track = player
            }
            runCatching { player.write(pcm, 0, pcm.size) }
        }
    }

    /** Playback volume helper for callers that want to duck music while talking. */
    @JvmStatic
    fun requestAudioFocus(manager: AudioManager?) {
        runCatching {
            manager?.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }
}
