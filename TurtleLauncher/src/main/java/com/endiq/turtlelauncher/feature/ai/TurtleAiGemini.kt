package com.endiq.turtlelauncher.feature.ai

import com.endiq.turtlelauncher.BuildConfig
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.path.UrlManager
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Every call Turtle AI makes to Google's Gemini API.
 *
 * One client for all the launcher's AI work - chat, vision (the skin/cape filter), image
 * generation, video generation, speech and transcription - because they differ in their prompt
 * and the parts they send, not in the wire format. The REST shape is:
 *
 *   POST {BASE}/models/{model}:generateContent      header x-goog-api-key
 *   {"contents":[{"role":"user","parts":[{"text":"..."}]}],
 *    "systemInstruction":{"parts":[{"text":"..."}]},
 *    "generationConfig":{...},
 *    "tools":[{"google_search":{}}]}
 *
 * and answers come back in `candidates[0].content.parts` - `text`, or `inlineData` (base64
 * media: images, speech) - with any Google-Search sources in
 * `candidates[0].groundingMetadata.groundingChunks[].web`. Video is the odd one out: it uses
 * `:predictLongRunning` plus polling (see [generateVideo]).
 *
 * ## Model fallback
 *
 * Every call runs through [runChain], which walks [TurtleAiModels.chain] for the task: the
 * user's chosen model first, then the registry's fallbacks. A model that hits its quota (429),
 * has been retired (404), is not enabled for this key (403) or fails server-side is put on a
 * short cooldown and the request moves to the next one. What stops the walk early is a
 * *definitive* failure: a bad key, a safety block or a dead network - none of those are fixed
 * by asking a different model.
 *
 * ## No Context, on purpose
 *
 * Every setting read goes through the global Settings.Manager, so nothing here needs a
 * Context - which matters because the crash advisor runs from the game JVM's exit hook, where
 * there is no Activity to pass in.
 *
 * ## Failure policy
 *
 * Every function returns null instead of throwing: the launcher's offline rule engine is always
 * a valid fallback. Failures are logged with the HTTP status and the API's own message so a bug
 * report can tell "no key" from "quota exhausted" from "model does not exist".
 */
object TurtleAiGemini {

    private const val TAG = "TurtleAiGemini"

    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    private const val MAX_OUTPUT_TOKENS = 4000
    private const val TIMEOUT_SECONDS = 60L

    /** Video generation is a long-running job: minutes, not seconds. */
    private const val VIDEO_POLL_INTERVAL_MS = 5000L
    private const val VIDEO_MAX_POLLS = 60            // ~5 minutes
    private const val VIDEO_TIMEOUT_SECONDS = 120L

    /** Quota comes back within the hour in practice; a retired model stays retired. */
    private const val COOLDOWN_QUOTA_MS = 90_000L
    private const val COOLDOWN_GONE_MS = 10 * 60_000L
    private const val COOLDOWN_SERVER_MS = 30_000L

    private const val MAX_HISTORY_TURNS = 6
    private const val MAX_HISTORY_QUESTION_CHARS = 240
    private const val MAX_HISTORY_ANSWER_CHARS = 700

    /** One earlier question/answer pair of the conversation, oldest first. */
    data class Exchange(val question: String, val answer: String)

    /** One web source the model actually used (from grounding metadata - never invented). */
    data class Source(val title: String, val url: String)

    /** A finished answer: the text, where its facts came from, and which model wrote it. */
    data class Answer(
        val text: String,
        val sources: List<Source> = emptyList(),
        val modelId: String = ""
    )

    /** Generated image bytes plus the format Gemini returned them in. */
    data class GeneratedImage(
        val bytes: ByteArray,
        val mimeType: String,
        val caption: String = "",
        val modelId: String = ""
    )

    /** A rendered video, already downloaded from the file the operation produced. */
    data class GeneratedVideo(
        val bytes: ByteArray,
        val mimeType: String = "video/mp4",
        val modelId: String = ""
    )

    /** Speech, already wrapped as a playable WAV. */
    data class GeneratedSpeech(
        val bytes: ByteArray,
        val mimeType: String = "audio/wav",
        val modelId: String = ""
    )

    // ── Configuration ───────────────────────────────────────────────────────────────

    /** True when the build itself carries a key (the repository secret was present). */
    @JvmStatic
    fun hasBuildKey(): Boolean =
        runCatching { BuildConfig.GEMINI_API_KEY }.getOrDefault("").isNotBlank()

    /** The user's own key if they set one, otherwise the one baked into the build. */
    @JvmStatic
    fun apiKey(): String {
        val userKey = runCatching { AllSettings.aiGeminiApiKey.getValue() }
            .getOrDefault("").trim()
        if (userKey.isNotEmpty()) return userKey
        return runCatching { BuildConfig.GEMINI_API_KEY }.getOrDefault("").trim()
    }

    /** True when the AI brain is switched on *and* there is a key to use. */
    @JvmStatic
    fun isConfigured(): Boolean {
        val enabled =
            runCatching { AllSettings.aiAssistantCloudBrain.getValue() }.getOrDefault(false)
        return enabled && apiKey().isNotEmpty()
    }

    /** True when the user asked for internet search (Google Search grounding). */
    @JvmStatic
    fun searchEnabled(): Boolean =
        runCatching { AllSettings.aiWebSearchEnabled.getValue() }.getOrDefault(false)

    /** The prebuilt voice used for speech and the live session ("Kore" by default). */
    @JvmStatic
    fun voiceName(): String =
        runCatching { AllSettings.aiVoice.getValue() }.getOrDefault(DEFAULT_VOICE)
            .trim().ifBlank { DEFAULT_VOICE }

    const val DEFAULT_VOICE = "Kore"

    /** Voices offered in Settings. Gemini's prebuilt set; more exist server-side. */
    val VOICES: List<String> = listOf(
        "Kore", "Puck", "Charon", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr", "Enceladus",
        "Schedar", "Achernar", "Achird", "Sadachbia", "Sadaltager", "Sulafat", "Vindemiatrix"
    )

    // ── Chat ────────────────────────────────────────────────────────────────────────

    /**
     * One chat turn.
     *
     * @param localAnswer  the launcher's own verified answer to this question, when the rule
     *                     engine had one - passed as authoritative context so the model
     *                     translates/expands instead of inventing settings that don't exist.
     * @param deviceFacts  live readings from this device (RAM, renderer, version, storage).
     * @param history      earlier turns, oldest first, so follow-ups resolve.
     * @param useSearch    attach Google Search grounding to this request.
     * @param task         which model chain to use ([TurtleAiModels.Task.CHAT] by default).
     * @return the answer, or null when the request cannot be made or every model failed.
     */
    @JvmStatic
    @JvmOverloads
    fun ask(
        question: String,
        languageTag: String,
        localAnswer: String? = null,
        deviceFacts: String? = null,
        history: List<Exchange> = emptyList(),
        useSearch: Boolean = false,
        task: TurtleAiModels.Task = TurtleAiModels.Task.CHAT
    ): Answer? {
        if (question.isBlank()) return null
        val key = apiKey()
        if (key.isEmpty()) return null

        val systemPrompt = buildString {
            append(TurtleAiPrompt.systemPrompt())
            append("\n\n")
            append(
                TurtleAiPrompt.languageInstruction(
                    languageTag,
                    TurtleAiLanguage.displayName(languageTag)
                )
            )
        }

        val userContent = buildString {
            append("Question: ").append(question.trim())
            if (!localAnswer.isNullOrBlank()) {
                append("\n\nThis launcher's own verified answer to that question (authoritative for ")
                append("anything about Turtle Launcher; translate or rephrase it as needed, and do ")
                append("not contradict it):\n")
                append(localAnswer.trim())
            }
            if (!deviceFacts.isNullOrBlank()) {
                append("\n\nLive readings from this device, gathered just now (facts - use them if the ")
                append("question needs them, and never contradict or invent other device figures):\n")
                append(deviceFacts.trim())
            }
        }

        val contents = buildContents(userContent, history)

        return runChain(task) { model ->
            val body = JsonObject().apply {
                add("contents", contents)
                add("systemInstruction", JsonObject().apply {
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", systemPrompt) })
                    })
                })
                add("generationConfig", JsonObject().apply {
                    addProperty("temperature", 0.3)
                    addProperty("maxOutputTokens", MAX_OUTPUT_TOKENS)
                })
                if (useSearch) {
                    add("tools", JsonArray().apply {
                        add(JsonObject().apply { add("google_search", JsonObject()) })
                    })
                }
            }
            when (val call = performRequest("models/$model:generateContent", body, key)) {
                is Call.Ok -> parseTextAnswer(call.root, model)
                is Call.Retry -> stepNext(model, call)
                is Call.Stop -> Step.Stop
            }
        }
    }

    /**
     * The plainest entry point: one system prompt, one user message, text back. Used by the
     * crash advisor, which brings its own (shorter) persona and a tight token budget.
     */
    @JvmStatic
    @JvmOverloads
    fun complete(
        systemPrompt: String,
        userText: String,
        temperature: Double = 0.2,
        maxOutputTokens: Int = MAX_OUTPUT_TOKENS,
        task: TurtleAiModels.Task = TurtleAiModels.Task.REASONING
    ): String? {
        if (systemPrompt.isBlank() || userText.isBlank()) return null
        val key = apiKey()
        if (key.isEmpty()) return null

        return runChain(task) { model ->
            val body = JsonObject().apply {
                add("contents", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", userText) })
                        })
                    })
                })
                add("systemInstruction", JsonObject().apply {
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", systemPrompt) })
                    })
                })
                add("generationConfig", JsonObject().apply {
                    addProperty("temperature", temperature)
                    addProperty("maxOutputTokens", maxOutputTokens)
                })
            }
            when (val call = performRequest("models/$model:generateContent", body, key)) {
                is Call.Ok -> {
                    val text = call.root.candidate()?.let { textOf(it) }
                    if (text.isNullOrBlank()) {
                        unusable(call.root, model, "no text")
                        Step.Next
                    } else {
                        Step.Value(text)
                    }
                }
                is Call.Retry -> stepNext(model, call)
                is Call.Stop -> Step.Stop
            }
        }
    }

    // ── Vision (screenshots, skins and capes) ───────────────────────────────────────

    /**
     * One image in, one text answer out - Gemini reads the image natively, so the caller keeps
     * its own prompt and parses its own JSON out of the reply.
     */
    @JvmStatic
    @JvmOverloads
    fun askAboutImage(
        prompt: String,
        imageBytes: ByteArray,
        mimeType: String = "image/png",
        task: TurtleAiModels.Task = TurtleAiModels.Task.VISION
    ): String? {
        if (prompt.isBlank() || imageBytes.isEmpty()) return null
        val key = apiKey()
        if (key.isEmpty()) return null
        val encoded = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)

        return runChain(task) { model ->
            val body = JsonObject().apply {
                add("contents", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply {
                                add("inlineData", JsonObject().apply {
                                    addProperty("mimeType", mimeType)
                                    addProperty("data", encoded)
                                })
                            })
                            add(JsonObject().apply { addProperty("text", prompt) })
                        })
                    })
                })
                add("generationConfig", JsonObject().apply {
                    addProperty("temperature", 0.0)
                })
            }
            when (val call = performRequest("models/$model:generateContent", body, key)) {
                is Call.Ok -> {
                    val text = call.root.candidate()?.let { textOf(it) }
                    if (text.isNullOrBlank()) {
                        unusable(call.root, model, "no text for image")
                        Step.Next
                    } else {
                        Step.Value(text)
                    }
                }
                is Call.Retry -> stepNext(model, call)
                is Call.Stop -> Step.Stop
            }
        }
    }

    // ── Image generation ────────────────────────────────────────────────────────────

    /**
     * Text prompt in, image out (`responseModalities` = TEXT + IMAGE). Most image models also
     * return a short text part - a caption, or the refusal when they will not draw something -
     * which is kept so the chat can show *something* instead of an empty bubble.
     */
    @JvmStatic
    @JvmOverloads
    fun generateImage(
        prompt: String,
        task: TurtleAiModels.Task = TurtleAiModels.Task.IMAGE
    ): GeneratedImage? {
        if (prompt.isBlank()) return null
        val key = apiKey()
        if (key.isEmpty()) return null

        return runChain(task) { model ->
            val body = JsonObject().apply {
                add("contents", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", prompt.trim()) })
                        })
                    })
                })
                add("generationConfig", JsonObject().apply {
                    add("responseModalities", JsonArray().apply {
                        add("TEXT")
                        add("IMAGE")
                    })
                })
            }
            when (val call = performRequest("models/$model:generateContent", body, key)) {
                is Call.Ok -> {
                    val parts = call.root.candidate()
                        ?.getAsJsonObject("content")?.getAsJsonArray("parts")
                    val media = firstInlineData(parts)
                    if (media == null) {
                        // A text-only model answers this request with prose and no image part:
                        // not an error, just the wrong model - fall through to the next one.
                        unusable(call.root, model, "no image part")
                        Step.Next
                    } else {
                        Step.Value(
                            GeneratedImage(
                                bytes = media.bytes,
                                mimeType = media.mimeType,
                                caption = textOfParts(parts),
                                modelId = model
                            )
                        )
                    }
                }
                is Call.Retry -> stepNext(model, call)
                is Call.Stop -> Step.Stop
            }
        }
    }

    // ── Video generation (Veo) ──────────────────────────────────────────────────────

    /**
     * Text prompt in, video out. Veo is not a `generateContent` model: the request starts a
     * long-running operation (`:predictLongRunning`), the operation is polled until `done`, and
     * the finished video is downloaded from the file URI the operation returns.
     *
     * Blocking for as long as it takes (usually under two minutes) - the caller already runs on
     * a background thread and shows progress in the UI.
     */
    @JvmStatic
    @JvmOverloads
    fun generateVideo(
        prompt: String,
        aspectRatio: String = "16:9",
        task: TurtleAiModels.Task = TurtleAiModels.Task.VIDEO,
        onProgress: ((String) -> Unit)? = null
    ): GeneratedVideo? {
        if (prompt.isBlank()) return null
        val key = apiKey()
        if (key.isEmpty()) return null

        return runChain(task) { model ->
            val startBody = JsonObject().apply {
                add("instances", JsonArray().apply {
                    add(JsonObject().apply { addProperty("prompt", prompt.trim()) })
                })
                add("parameters", JsonObject().apply {
                    addProperty("aspectRatio", aspectRatio)
                })
            }
            val started = when (
                val call = performRequest("models/$model:predictLongRunning", startBody, key)
            ) {
                is Call.Ok -> call.root
                is Call.Retry -> return@runChain stepNext(model, call)
                is Call.Stop -> return@runChain Step.Stop
            }
            val operation = started.get("name")?.takeIf { it.isJsonPrimitive }?.asString
            if (operation.isNullOrBlank()) {
                Logging.w(TAG, "Video request returned no operation name")
                return@runChain Step.Next
            }

            var polls = 0
            while (polls < VIDEO_MAX_POLLS) {
                polls++
                onProgress?.invoke(operation)
                Thread.sleep(VIDEO_POLL_INTERVAL_MS)
                val status = getWithTimeout("$operation", key, VIDEO_TIMEOUT_SECONDS)
                    ?: return@runChain Step.Next
                if (status.get("done")?.takeIf { it.isJsonPrimitive }?.asBoolean != true) {
                    continue
                }
                val error = status.getAsJsonObject("error")
                if (error != null) {
                    val message = error.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: "video operation failed"
                    Logging.w(TAG, "Video operation failed on $model: $message")
                    val code = error.get("code")?.takeIf { it.isJsonPrimitive }?.asInt ?: 500
                    val cooldown = if (code == 429) COOLDOWN_QUOTA_MS else COOLDOWN_SERVER_MS
                    TurtleAiModels.cool(model, cooldown)
                    return@runChain Step.Next
                }
                val uri = findMediaUri(status.getAsJsonObject("response"))
                    ?: run {
                        Logging.w(TAG, "Video operation finished with no video uri")
                        return@runChain Step.Next
                    }
                val bytes = downloadFile(uri, key)
                if (bytes == null || bytes.isEmpty()) {
                    Logging.w(TAG, "Could not download the generated video")
                    return@runChain Step.Next
                }
                return@runChain Step.Value(GeneratedVideo(bytes = bytes, modelId = model))
            }
            Logging.w(TAG, "Video generation timed out after $polls polls")
            Step.Next
        }
    }

    // ── Speech (text to audio) ──────────────────────────────────────────────────────

    /**
     * Reads [text] aloud. Gemini returns raw PCM (`audio/L16`), which is wrapped into a WAV
     * container here so it can be played and shared like any other audio file - nothing on
     * Android plays headerless PCM without extra plumbing.
     */
    @JvmStatic
    @JvmOverloads
    fun speak(
        text: String,
        task: TurtleAiModels.Task = TurtleAiModels.Task.TTS
    ): GeneratedSpeech? {
        if (text.isBlank()) return null
        val key = apiKey()
        if (key.isEmpty()) return null
        val voice = voiceName()

        return runChain(task) { model ->
            val body = JsonObject().apply {
                add("contents", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", text.trim()) })
                        })
                    })
                })
                add("generationConfig", JsonObject().apply {
                    add("responseModalities", JsonArray().apply { add("AUDIO") })
                    add("speechConfig", JsonObject().apply {
                        add("voiceConfig", JsonObject().apply {
                            add("prebuiltVoiceConfig", JsonObject().apply {
                                addProperty("voiceName", voice)
                            })
                        })
                    })
                })
            }
            when (val call = performRequest("models/$model:generateContent", body, key)) {
                is Call.Ok -> {
                    val parts = call.root.candidate()
                        ?.getAsJsonObject("content")?.getAsJsonArray("parts")
                    val media = firstInlineData(parts)
                    if (media == null) {
                        unusable(call.root, model, "no audio part")
                        Step.Next
                    } else {
                        val isPcm = media.mimeType.contains("l16", ignoreCase = true) ||
                            media.mimeType.contains("pcm", ignoreCase = true)
                        Step.Value(
                            GeneratedSpeech(
                                bytes = if (isPcm) {
                                    wrapPcmAsWav(media.bytes, sampleRateOf(media.mimeType))
                                } else {
                                    media.bytes
                                },
                                mimeType = if (isPcm) "audio/wav" else media.mimeType,
                                modelId = model
                            )
                        )
                    }
                }
                is Call.Retry -> stepNext(model, call)
                is Call.Stop -> Step.Stop
            }
        }
    }

    // ── Transcription (audio to text) ───────────────────────────────────────────────

    /** Audio in, text out - used for a recording the user shares with the launcher. */
    @JvmStatic
    @JvmOverloads
    fun transcribe(
        audioBytes: ByteArray,
        mimeType: String,
        task: TurtleAiModels.Task = TurtleAiModels.Task.TRANSCRIBE
    ): String? {
        if (audioBytes.isEmpty()) return null
        val key = apiKey()
        if (key.isEmpty()) return null
        val encoded = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)

        return runChain(task) { model ->
            val body = JsonObject().apply {
                add("contents", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply {
                                add("inlineData", JsonObject().apply {
                                    addProperty("mimeType", mimeType)
                                    addProperty("data", encoded)
                                })
                            })
                            add(JsonObject().apply {
                                addProperty(
                                    "text",
                                    "Transcribe this audio exactly, in the language spoken. " +
                                        "Output only the transcript, no commentary. If there is " +
                                        "no speech in it, say so in one short line."
                                )
                            })
                        })
                    })
                })
                add("generationConfig", JsonObject().apply {
                    addProperty("temperature", 0.0)
                })
            }
            when (val call = performRequest("models/$model:generateContent", body, key)) {
                is Call.Ok -> {
                    val text = call.root.candidate()?.let { textOf(it) }
                    if (text.isNullOrBlank()) {
                        unusable(call.root, model, "no transcript")
                        Step.Next
                    } else {
                        Step.Value(text)
                    }
                }
                is Call.Retry -> stepNext(model, call)
                is Call.Stop -> Step.Stop
            }
        }
    }

    // ── Model listing ───────────────────────────────────────────────────────────────

    /**
     * The model ids this key can actually use, newest-looking first.
     *
     * Fetched rather than hardcoded: model names are the part of the API that changes most, and
     * a list baked into an APK goes stale in months. This is informational - the chat flow uses
     * [TurtleAiModels] - so a failure here just means an empty list in Settings.
     */
    @JvmStatic
    fun listModels(): List<String> {
        val key = apiKey()
        if (key.isEmpty()) return emptyList()
        val root = get("models?pageSize=200", key) ?: return emptyList()
        val models = root.getAsJsonObject("models")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: return emptyList()
        return models.mapNotNull { element ->
            val obj = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
            val name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString
                ?.removePrefix("models/") ?: return@mapNotNull null
            val methods = obj.getAsJsonArray("supportedGenerationMethods")
                ?.mapNotNull { it.takeIf { m -> m.isJsonPrimitive }?.asString } ?: emptyList()
            if ("generateContent" !in methods) return@mapNotNull null
            name
        }.distinct().sortedDescending()
    }

    // ── The fallback engine ─────────────────────────────────────────────────────────

    /** What one attempt produced. */
    private sealed class Step<out T> {
        /** This model answered. */
        data class Value<T>(val value: T) : Step<T>()
        /** This model could not; try the next one in the chain. */
        object Next : Step<Nothing>()
        /** Nothing further will help - a bad key, a safety block, no network. */
        object Stop : Step<Nothing>()
    }

    /** How an HTTP call ended, from the fallback engine's point of view. */
    private sealed class Call {
        data class Ok(val root: JsonObject) : Call()
        /** Worth trying another model: quota, retired model, permission, server error. */
        data class Retry(val status: Int, val detail: String) : Call()
        /** Trying another model would fail the same way. */
        data class Stop(val detail: String) : Call()
    }

    /** Runs [attempt] down the task's model chain, honouring cooldowns, and returns the first
     *  result. Null means every model in the chain failed (or the chain was empty). */
    private fun <T> runChain(
        task: TurtleAiModels.Task,
        attempt: (model: String) -> Step<T>
    ): T? {
        val full = TurtleAiModels.chain(task)
        if (full.isEmpty()) return null
        val cooling = full.filter { TurtleAiModels.isCooling(it) }
        // Models on cooldown are skipped - unless every model is cooling, in which case the
        // cooldown may have been a blip and trying beats refusing to answer at all.
        val candidates = full.filterNot { TurtleAiModels.isCooling(it) }.ifEmpty { full }
        if (cooling.isNotEmpty()) {
            Logging.w(TAG, "Skipping models on cooldown for $task: $cooling")
        }
        for (model in candidates) {
            when (val step = attempt(model)) {
                is Step.Value -> return step.value
                Step.Next -> Unit
                Step.Stop -> return null
            }
        }
        Logging.w(TAG, "Every model in the $task chain failed")
        return null
    }

    /** Cools a model down and asks the chain to move on. */
    private fun stepNext(model: String, call: Call.Retry): Step<Nothing> {
        Logging.w(TAG, "Model $model unavailable (${call.status}): ${call.detail}")
        TurtleAiModels.cool(model, call.cooldownMs())
        return Step.Next
    }

    private fun Call.Retry.cooldownMs(): Long = when (status) {
        404 -> COOLDOWN_GONE_MS
        403 -> COOLDOWN_GONE_MS
        429 -> COOLDOWN_QUOTA_MS
        else -> COOLDOWN_SERVER_MS
    }

    /** Logs why a response could not be used; the caller then moves to the next model. */
    private fun unusable(root: JsonObject, model: String, what: String) {
        val blocked = root.getAsJsonObject("promptFeedback")
            ?.get("blockReason")?.takeIf { it.isJsonPrimitive }?.asString
        val candidate = root.candidate()
        val finish = candidate?.get("finishReason")?.takeIf { it.isJsonPrimitive }?.asString
        Logging.w(
            TAG,
            "$model returned $what" +
                (if (finish == null) "" else " (finishReason=$finish)") +
                (if (blocked == null) "" else " (blockReason=$blocked)")
        )
    }

    // ── HTTP plumbing ───────────────────────────────────────────────────────────────

    private fun buildContents(userContent: String, history: List<Exchange>): JsonArray =
        JsonArray().apply {
            // The tail of the conversation. Gemini has no server-side thread: the whole
            // context is replayed on every request, so an unbounded history would grow the
            // request (and the bill) forever.
            history.takeLast(MAX_HISTORY_TURNS).forEach { exchange ->
                val previousQuestion = exchange.question.trim().take(MAX_HISTORY_QUESTION_CHARS)
                val previousAnswer = exchange.answer.trim().take(MAX_HISTORY_ANSWER_CHARS)
                if (previousQuestion.isBlank() || previousAnswer.isBlank()) return@forEach
                add(content("user", previousQuestion))
                add(content("model", previousAnswer))
            }
            add(content("user", userContent))
        }

    private fun content(role: String, text: String): JsonObject =
        JsonObject().apply {
            addProperty("role", role)
            add("parts", JsonArray().apply {
                add(JsonObject().apply { addProperty("text", text) })
            })
        }

    /** The concatenated text parts of a candidate; empty when it returned media only. */
    private fun textOf(candidate: JsonObject): String =
        textOfParts(candidate.getAsJsonObject("content")?.getAsJsonArray("parts"))

    private fun textOfParts(parts: JsonArray?): String {
        if (parts == null) return ""
        return parts.mapNotNull { part ->
            runCatching { part.asJsonObject }.getOrNull()
                ?.get("text")?.takeIf { it.isJsonPrimitive }?.asString
        }.joinToString("").trim()
    }

    /** Where a grounded answer's facts came from, deduplicated by URL. */
    private fun sourcesOf(candidate: JsonObject): List<Source> {
        val chunks = candidate.getAsJsonObject("groundingMetadata")
            ?.getAsJsonArray("groundingChunks") ?: return emptyList()
        val seen = HashSet<String>()
        return chunks.mapNotNull { element ->
            val web = runCatching { element.asJsonObject }.getOrNull()
                ?.getAsJsonObject("web") ?: return@mapNotNull null
            val url = web.get("uri")?.takeIf { it.isJsonPrimitive }?.asString
                ?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            if (!seen.add(url)) return@mapNotNull null
            val title = web.get("title")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            Source(title = title.ifBlank { url }, url = url)
        }
    }

    /** Text answers need the answer, the grounding sources and a missing-text guard. */
    private fun parseTextAnswer(root: JsonObject, model: String): Step<Answer> {
        val candidate = root.candidate()
        if (candidate == null) {
            val blocked = root.getAsJsonObject("promptFeedback")
                ?.get("blockReason")?.takeIf { it.isJsonPrimitive }?.asString
            if (blocked != null) {
                // A safety block is about the *content*, not the model: another model would
                // refuse the same request, so stop instead of burning the whole chain.
                Logging.w(TAG, "Request blocked by safety filters ($blocked)")
                return Step.Stop
            }
            unusable(root, model, "no candidate")
            return Step.Next
        }
        val text = textOf(candidate)
        if (text.isBlank()) {
            unusable(root, model, "no text")
            return Step.Next
        }
        val finish = candidate.get("finishReason")?.takeIf { it.isJsonPrimitive }?.asString
        if (finish == "MAX_TOKENS") {
            Logging.w(TAG, "$model hit the $MAX_OUTPUT_TOKENS-token cap")
        }
        return Step.Value(
            Answer(text = text, sources = sourcesOf(candidate), modelId = model)
        )
    }

    /** The first inlineData part of a candidate, decoded. */
    private class Media(val bytes: ByteArray, val mimeType: String)

    private fun firstInlineData(parts: JsonArray?): Media? {
        if (parts == null) return null
        for (part in parts) {
            val obj = runCatching { part.asJsonObject }.getOrNull() ?: continue
            val inline = obj.getAsJsonObject("inlineData") ?: obj.getAsJsonObject("inline_data")
                ?: continue
            val data = inline.get("data")?.takeIf { it.isJsonPrimitive }?.asString
            if (data.isNullOrBlank()) continue
            val mime = inline.get("mimeType")?.takeIf { it.isJsonPrimitive }?.asString
                ?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
            val bytes = runCatching {
                android.util.Base64.decode(data, android.util.Base64.DEFAULT)
            }.onFailure { t -> Logging.w(TAG, "Media part was not valid base64", t) }
                .getOrNull() ?: continue
            return Media(bytes, mime)
        }
        return null
    }

    private fun JsonObject.candidate(): JsonObject? =
        getAsJsonArray("candidates")?.firstOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject

    /**
     * One request. [body] null = GET. Classifies the outcome for the fallback engine rather
     * than logging and giving up: whether a failure is worth retrying on another model is the
     * whole point of this class.
     */
    @Volatile
    private var lastFailure: String? = null

    /** Short, user-facing reason the most recent Gemini request failed, or null if it didn't. */
    @JvmStatic
    fun failureReason(): String? {
        val f = lastFailure ?: return null
        return when {
            f == "no key" -> "No Gemini API key is set (Settings -> Experimental)."
            f == "key rejected" || f == "HTTP 401" || f == "HTTP 403" ->
                "Gemini rejected the API key - it is invalid, expired or restricted."
            f == "HTTP 429" -> "Gemini quota or rate limit reached - try again in a minute."
            f == "HTTP 404" -> "None of the configured Gemini models exist for this key."
            f.startsWith("HTTP 5") -> "Gemini is having server problems - try again shortly."
            f.contains("UnknownHost") || f.contains("Connect") || f.contains("Timeout") ->
                "No internet connection to Gemini."
            else -> "Gemini request failed ($f)."
        }
    }

    private fun performRequest(
        path: String,
        body: JsonObject?,
        key: String,
        timeoutSeconds: Long = TIMEOUT_SECONDS
    ): Call {
        if (key.isEmpty()) { lastFailure = "no key"; return Call.Stop("no key") }
        val url = "$BASE/$path"
        return try {
            val requestBody = body?.toString()?.toRequestBody("application/json".toMediaType())
            val builder = UrlManager.createRequestBuilder(url, requestBody)
                .header("x-goog-api-key", key)
                .header("Accept", "application/json")
                .addHeader("User-Agent", "TurtleLauncher-TurtleAI/1.0")
            val client = UrlManager
                .createOkHttpClientBuilder { it.callTimeout(timeoutSeconds, TimeUnit.SECONDS) }
                .build()
            client.newCall(builder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // Never log the key; the URL carries none (it goes in a header).
                    val detail = responseBody.take(300)
                    Logging.w(TAG, "Gemini HTTP ${response.code} for $path - $detail")
                    val c = classify(response.code, detail)
                    lastFailure = if (c is Call.Stop && c.detail == "key rejected") "key rejected"
                    else "HTTP ${response.code}"
                    c
                } else {
                    lastFailure = null
                    if (responseBody.isBlank()) return Call.Stop("empty response")
                    val parsed = JsonParser.parseString(responseBody)
                        .takeIf { it.isJsonObject }?.asJsonObject
                    if (parsed == null) Call.Stop("non-object response") else Call.Ok(parsed)
                }
            }
        } catch (t: Exception) {
            // A network problem is not a model problem: every other model would fail the same
            // way, so stop rather than walking the chain.
            Logging.w(TAG, "Gemini request failed for $path", t)
            lastFailure = t.javaClass.simpleName
            Call.Stop(t.javaClass.simpleName)
        }
    }

    /**
     * Turns an HTTP failure into "try the next model" or "give up".
     *
     * The API's own message is the tie-breaker for 400/403, which are used for both "this model
     * cannot do that" (retry elsewhere) and "your key is wrong" (retrying is pointless).
     */
    private fun classify(status: Int, detail: String): Call {
        val lower = detail.lowercase()
        val keyProblem = status == 401 ||
            lower.contains("api key not valid") || lower.contains("api_key_invalid") ||
            lower.contains("api key expired")
        if (keyProblem) return Call.Stop("key rejected")
        if (status == 400 && lower.contains("invalid") && !lower.contains("not supported")) {
            // A malformed request repeats identically on every model.
            if (lower.contains("field") || lower.contains("argument") || lower.contains("format")) {
                return Call.Stop("bad request")
            }
        }
        return Call.Retry(status, detail)
    }

    private fun get(path: String, key: String): JsonObject? =
        when (val call = performRequest(path, null, key)) {
            is Call.Ok -> call.root
            else -> null
        }

    private fun getWithTimeout(path: String, key: String, timeoutSeconds: Long): JsonObject? =
        when (val call = performRequest(path, null, key, timeoutSeconds)) {
            is Call.Ok -> call.root
            else -> null
        }

    /** Downloads a generated file (video) - the file endpoint takes the same key header. */
    private fun downloadFile(url: String, key: String): ByteArray? = runCatching {
        val request = UrlManager.createRequestBuilder(url)
            .header("x-goog-api-key", key)
            .addHeader("User-Agent", "TurtleLauncher-TurtleAI/1.0")
            .build()
        val client = UrlManager
            .createOkHttpClientBuilder { it.callTimeout(VIDEO_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Logging.w(TAG, "Video download failed: HTTP ${response.code}")
                return@runCatching null
            }
            response.body?.bytes()
        }
    }.onFailure { t -> Logging.w(TAG, "Video download failed", t) }.getOrNull()

    /**
     * The video URI inside a finished operation. The response shape has already changed once
     * (`generateVideoResponse.generatedSamples[].video.uri` vs `videos[].uri` vs the Vertex
     * `predictions[]` form), so this walks the whole tree looking for the first string that
     * looks like a download URI instead of trusting one path.
     */
    private fun findMediaUri(response: JsonObject?): String? {
        if (response == null) return null
        val candidates = mutableListOf<String>()
        fun walk(element: JsonElement?) {
            when {
                element == null -> Unit
                element.isJsonObject -> element.asJsonObject.entrySet()
                    .forEach { walk(it.value) }
                element.isJsonArray -> element.asJsonArray.forEach { walk(it) }
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                    val value = element.asString
                    if (value.startsWith("http") &&
                        (value.contains(":download") || value.contains("alt=media") ||
                            value.endsWith(".mp4"))
                    ) {
                        candidates.add(value)
                    }
                }
            }
        }
        walk(response)
        return candidates.firstOrNull()
    }

    /** `audio/L16;rate=24000` -> 24000. Defaults to 24 kHz, Gemini's usual speech rate. */
    private fun sampleRateOf(mimeType: String): Int {
        val match = Regex("rate=(\\d+)").find(mimeType) ?: return 24_000
        return match.groupValues.getOrNull(1)?.toIntOrNull() ?: 24_000
    }

    /**
     * Wraps headerless 16-bit mono PCM in a WAV container. Gemini returns speech as
     * `audio/L16` - raw samples with no header - which Android cannot play or share as-is.
     */
    private fun wrapPcmAsWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)                       // PCM chunk size
        header.putShort(1)                      // format = PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * bitsPerSample / 8).toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcm.size)
        return header.array() + pcm
    }
}
