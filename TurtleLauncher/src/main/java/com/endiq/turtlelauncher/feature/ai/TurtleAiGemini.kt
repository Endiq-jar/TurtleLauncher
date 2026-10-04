package com.endiq.turtlelauncher.feature.ai

import com.endiq.turtlelauncher.BuildConfig
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.path.UrlManager
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Every call Turtle AI makes to Google's Gemini API.
 *
 * One client for all three AI features in the launcher - the Assistant's brain, the crash
 * advisor and the skin/cape filter - because they differ only in their prompt and the parts
 * they send, not in the wire format. The Gemini REST API is:
 *
 *   POST {BASE}/models/{model}:generateContent      header x-goog-api-key
 *   {"contents":[{"role":"user","parts":[{"text":"..."}]}],
 *    "systemInstruction":{"parts":[{"text":"..."}]},
 *    "generationConfig":{...},
 *    "tools":[{"google_search":{}}]}
 *
 * and answers come back in `candidates[0].content.parts` - either `text` or `inlineData`
 * (base64 media, used by image generation) - with any Google-Search sources in
 * `candidates[0].groundingMetadata.groundingChunks[].web`.
 *
 * ## The key
 *
 * `apiKey()` prefers the user's own key from Settings (so a shared APK can be re-pointed or
 * a leaked build key rotated without a new release) and otherwise falls back to
 * [BuildConfig.GEMINI_API_KEY], which the build injects from the GEMINI_API_KEY repository
 * secret. Nothing here ever logs the key.
 *
 * ## No Context, on purpose
 *
 * Every setting read goes through the global Settings.Manager, so nothing here needs a
 * Context - which matters because the crash advisor runs from the game JVM's exit hook, where
 * there is no Activity to pass in.
 *
 * ## Failure policy
 *
 * Every function returns null (or an empty list) instead of throwing: the launcher's offline
 * rule engine is always a valid fallback, and a network failure must never take the chat
 * screen down with it. Failures are logged with the HTTP status so a bug report can tell
 * "no key" from "quota exhausted" from "model does not exist".
 */
object TurtleAiGemini {

    private const val TAG = "TurtleAiGemini"

    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    /**
     * Used when the user has not chosen a model ("auto"). Flash is the sensible default for a
     * phone: fast and cheap, good enough for the launcher's answers, and the model that every
     * key should have access to. A user who wants more can pick a Pro model in Settings - the
     * picker lists what their key actually supports.
     */
    const val DEFAULT_MODEL = "gemini-2.5-flash"

    /** Server-side image generation ("nano banana" family). */
    const val DEFAULT_IMAGE_MODEL = "gemini-2.5-flash-image"

    /** "auto" in the model setting: pick [DEFAULT_MODEL]. */
    const val AUTO = "auto"

    private const val MAX_OUTPUT_TOKENS = 4000
    private const val TIMEOUT_SECONDS = 60L
    private const val MAX_HISTORY_TURNS = 6
    private const val MAX_HISTORY_QUESTION_CHARS = 240
    private const val MAX_HISTORY_ANSWER_CHARS = 700

    /** One earlier question/answer pair of the conversation, oldest first. */
    data class Exchange(val question: String, val answer: String)

    /** One web source the model actually used (from grounding metadata - never invented). */
    data class Source(val title: String, val url: String)

    /** A finished answer: the text, plus where its facts came from when Search grounded it. */
    data class Answer(
        val text: String,
        val sources: List<Source> = emptyList(),
        val model: String = ""
    )

    /** Generated image bytes plus the format Gemini returned them in. */
    data class GeneratedImage(val bytes: ByteArray, val mimeType: String, val caption: String = "")

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

    /** The model to use: the user's choice, or [DEFAULT_MODEL] for "auto"/blank. */
    @JvmStatic
    fun model(): String =
        runCatching { AllSettings.aiGeminiModel.getValue() }.getOrDefault(AUTO)
            .trim().ifBlank { AUTO }
            .let { if (it == AUTO) DEFAULT_MODEL else it }

    /** The image-generation model: the user's choice, or [DEFAULT_IMAGE_MODEL]. */
    @JvmStatic
    fun imageModel(): String =
        runCatching { AllSettings.aiGeminiImageModel.getValue() }.getOrDefault(DEFAULT_IMAGE_MODEL)
            .trim().ifBlank { DEFAULT_IMAGE_MODEL }

    /** True when the user asked for internet search (the Assistant grounds with Google Search). */
    @JvmStatic
    fun searchEnabled(): Boolean =
        runCatching { AllSettings.aiWebSearchEnabled.getValue() }.getOrDefault(false)

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
     * @return the answer, or null when the request cannot be made or fails.
     */
    @JvmStatic
    fun ask(
        question: String,
        languageTag: String,
        localAnswer: String? = null,
        deviceFacts: String? = null,
        history: List<Exchange> = emptyList(),
        useSearch: Boolean = false
    ): Answer? {
        if (question.isBlank()) return null
        val key = apiKey()
        if (key.isEmpty()) return null
        val usedModel = model()

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

        val root = post("models/$usedModel:generateContent", body, key)
            ?: return null
        val candidate = root.getAsJsonArray("candidates")?.firstOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
            ?: run {
                // Usually a safety block or a quota error with no candidate: worth a line in
                // the log, since the user only sees "couldn't reach the service".
                val feedback = root.getAsJsonObject("promptFeedback")
                    ?.get("blockReason")?.takeIf { it.isJsonPrimitive }?.asString
                Logging.w(TAG, "No candidate in Gemini response" +
                    if (feedback == null) "" else " (blockReason=$feedback)")
                return null
            }

        val text = textOf(candidate)
        if (text.isBlank()) {
            Logging.w(TAG, "Gemini returned no text (finishReason=" +
                (candidate.get("finishReason")?.takeIf { it.isJsonPrimitive }?.asString ?: "?") + ")")
            return null
        }
        val finishReason = candidate.get("finishReason")?.takeIf { it.isJsonPrimitive }?.asString
        if (finishReason == "MAX_TOKENS") {
            Logging.w(TAG, "Gemini answer hit the $MAX_OUTPUT_TOKENS-token cap")
        }

        return Answer(
            text = text,
            sources = sourcesOf(candidate),
            model = usedModel
        )
    }

    /**
     * The plainest entry point: one system prompt, one user message, text back. Used by the
     * crash advisor, which brings its own (shorter) persona and needs a tight token budget
     * more than it needs conversation history or search.
     */
    @JvmStatic
    fun complete(
        systemPrompt: String,
        userText: String,
        temperature: Double = 0.2,
        maxOutputTokens: Int = MAX_OUTPUT_TOKENS
    ): String? {
        if (systemPrompt.isBlank() || userText.isBlank()) return null
        val key = apiKey()
        if (key.isEmpty()) return null
        val usedModel = model()

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

        val root = post("models/$usedModel:generateContent", body, key) ?: return null
        return root.getAsJsonArray("candidates")?.firstOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
            ?.let { textOf(it) }
            ?.takeIf { it.isNotBlank() }
    }

    // ── Vision (used by the skin/cape filter) ───────────────────────────────────────

    /**
     * One image in, one text answer out - Gemini reads the image natively, so the caller gets
     * to keep its own prompt and parse its own JSON out of the reply.
     */
    @JvmStatic
    fun askAboutImage(
        prompt: String,
        imageBytes: ByteArray,
        mimeType: String = "image/png"
    ): String? {
        if (prompt.isBlank() || imageBytes.isEmpty()) return null
        val key = apiKey()
        if (key.isEmpty()) return null
        val usedModel = model()

        val body = JsonObject().apply {
            add("contents", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply {
                            add("inlineData", JsonObject().apply {
                                addProperty("mimeType", mimeType)
                                addProperty("data", android.util.Base64.encodeToString(
                                    imageBytes, android.util.Base64.NO_WRAP
                                ))
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

        val root = post("models/$usedModel:generateContent", body, key) ?: return null
        return root.getAsJsonArray("candidates")?.firstOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
            ?.let { textOf(it) }
            ?.takeIf { it.isNotBlank() }
    }

    // ── Image generation ────────────────────────────────────────────────────────────

    /**
     * Text prompt in, image out. Uses the Gemini image model (`responseModalities` = TEXT +
     * IMAGE) rather than Imagen's separate `:predict` endpoint, so it shares the key, the
     * model setting style and the response parser with everything else.
     *
     * Most image models also return a short text part (a caption or a refusal), which is kept
     * so the chat can show *something* when no image comes back.
     */
    @JvmStatic
    fun generateImage(prompt: String): GeneratedImage? {
        if (prompt.isBlank()) return null
        val key = apiKey()
        if (key.isEmpty()) return null
        val usedModel = imageModel()

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

        val root = post("models/$usedModel:generateContent", body, key)
            ?: return null
        val candidate = root.getAsJsonArray("candidates")?.firstOrNull()
            ?.takeIf { it.isJsonObject }?.asJsonObject
            ?: run {
                Logging.w(TAG, "Image request returned no candidate")
                return null
            }
        val parts = candidate.getAsJsonObject("content")?.getAsJsonArray("parts")
            ?: return null

        var bytes: ByteArray? = null
        var mime = "image/png"
        val caption = StringBuilder()
        for (part in parts) {
            val obj = runCatching { part.asJsonObject }.getOrNull() ?: continue
            obj.get("text")?.takeIf { it.isJsonPrimitive }?.asString
                ?.takeIf { it.isNotBlank() }?.let { caption.append(it).append('\n') }
            val inline = obj.getAsJsonObject("inlineData") ?: obj.getAsJsonObject("inline_data")
            if (inline != null && bytes == null) {
                val data = inline.get("data")?.takeIf { it.isJsonPrimitive }?.asString
                if (!data.isNullOrBlank()) {
                    bytes = runCatching {
                        android.util.Base64.decode(data, android.util.Base64.DEFAULT)
                    }.onFailure { t ->
                        Logging.w(TAG, "Image part was not valid base64", t)
                    }.getOrNull()
                    inline.get("mimeType")?.takeIf { it.isJsonPrimitive }?.asString
                        ?.takeIf { it.isNotBlank() }?.let { mime = it }
                }
            }
        }

        val imageBytes = bytes ?: run {
            Logging.w(TAG, "Image request produced no image part")
            return null
        }
        return GeneratedImage(
            bytes = imageBytes,
            mimeType = mime,
            caption = caption.toString().trim()
        )
    }

    /**
     * The model ids this key can use for text, newest-looking first.
     *
     * Fetched rather than hardcoded: model names are the part of the API that changes most,
     * and a list baked into an APK goes stale in months. Embedding/vision-only/audio models
     * are filtered out of the picker by their supported methods.
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
            // Vision-only and audio models answer generateContent too, but they are not chat
            // brains: keep them out of a list the user picks a chat model from.
            val lowered = name.lowercase()
            if (lowered.contains("embedding") || lowered.contains("aqa") ||
                lowered.contains("tts") || lowered.contains("imagen") ||
                lowered.contains("veo")
            ) return@mapNotNull null
            name
        }.distinct().sortedDescending()
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
    private fun textOf(candidate: JsonObject): String {
        val parts = candidate.getAsJsonObject("content")?.getAsJsonArray("parts") ?: return ""
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

    private fun post(path: String, body: JsonObject, key: String): JsonObject? =
        request(path, body, key)

    private fun get(path: String, key: String): JsonObject? =
        request(path, null, key)

    /**
     * One request. [body] null = GET. Returns the parsed root object, or null after logging
     * why - the API's own error text is in the response body, and without it "it didn't work"
     * is undiagnosable.
     */
    private fun request(
        path: String,
        body: JsonObject?,
        key: String
    ): JsonObject? {
        if (key.isEmpty()) return null
        val url = "$BASE/$path"
        return runCatching {
            val requestBody = body?.toString()?.toRequestBody("application/json".toMediaType())
            val builder = UrlManager.createRequestBuilder(url, requestBody)
                .header("x-goog-api-key", key)
                .header("Accept", "application/json")
                .addHeader("User-Agent", "TurtleLauncher-TurtleAI/1.0")
            val client = UrlManager
                .createOkHttpClientBuilder { it.callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
                .build()
            client.newCall(builder.build()).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // Never log the key; the URL carries none (it goes in a header).
                    Logging.w(TAG, "Gemini HTTP " + response.code + " for " + path +
                        " - " + responseBody.take(300))
                    return@runCatching null
                }
                if (responseBody.isBlank()) return@runCatching null
                JsonParser.parseString(responseBody).takeIf { it.isJsonObject }?.asJsonObject
            }
        }.onFailure { t -> Logging.w(TAG, "Gemini request failed for " + path, t) }
            .getOrNull()
    }
}
