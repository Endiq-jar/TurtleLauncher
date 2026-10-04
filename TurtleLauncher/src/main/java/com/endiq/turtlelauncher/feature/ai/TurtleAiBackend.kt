package com.endiq.turtlelauncher.feature.ai

import android.content.Context
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
 * The optional cloud half of Turtle AI: one chat-completion call, using the user's own API key,
 * with the full Turtle AI identity ([TurtleAiPrompt.systemPrompt]) as the system prompt.
 *
 * Two things this exists for that the on-device rule engine cannot do:
 *
 *  1. **Other languages.** The offline engine's answers are English. Here the model is told
 *     which language to answer in and is handed the launcher's own verified answer as context,
 *     so a Hindi/Portuguese/Japanese question gets a full answer in that language *and* still
 *     reflects what this launcher actually does instead of a generic guess.
 *  2. **Anything outside the launcher.** With web search results attached, the model summarizes
 *     them under the citation rules in [TurtleAiPrompt.WEB_SEARCH].
 *
 * It is off unless the user turns it on (`aiAssistantCloudBrain`), needs their key, and never
 * runs when the answer is already known locally and the user is writing in English - the
 * on-device answer is faster, free and cannot drift from the launcher's real behaviour.
 */
object TurtleAiBackend {

    private const val TAG = "TurtleAiBackend"

    private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
    private const val MAX_ANSWER_TOKENS = 800
    private const val TIMEOUT_SECONDS = 45L

    /** True when the user has enabled the AI brain *and* given us a key to use. */
    @JvmStatic
    fun isConfigured(context: Context): Boolean {
        val enabled = runCatching { AllSettings.aiAssistantCloudBrain.getValue() }.getOrDefault(false)
        if (!enabled) return false
        return apiKey().isNotEmpty()
    }

    private fun apiKey(): String =
        runCatching { AllSettings.aiApiKey.getValue() }.getOrDefault("").trim()

    private fun model(): String =
        runCatching { AllSettings.aiModel.getValue() }.getOrDefault("gpt-4o-mini")
            .trim().ifBlank { "gpt-4o-mini" }

    /**
     * One turn: [question] in, an answer in [languageTag] out.
     *
     * @param localAnswer the on-device engine's own answer for this question, when it had one.
     *                    Passed as authoritative launcher knowledge so the model translates and
     *                    expands it instead of inventing settings that don't exist.
     * @param searchBlock   pre-formatted web results from [TurtleAiWebSearch.formatForPrompt].
     * @return the answer, or null when the request cannot be made or fails (caller falls back
     *         to the on-device text/raw search results).
     */
    @JvmStatic
    fun ask(
        context: Context,
        question: String,
        languageTag: String,
        localAnswer: String?,
        searchBlock: String?
    ): String? {
        if (!isConfigured(context)) return null
        val key = apiKey()
        if (question.isBlank()) return null

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
            if (!searchBlock.isNullOrBlank()) {
                append("\n\n").append(searchBlock.trim())
                append("\n\nIf the results above do not actually answer the question, say so.")
            }
        }

        return runCatching {
            val messages = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", systemPrompt)
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", userContent)
                })
            }
            val body = JsonObject().apply {
                addProperty("model", model())
                add("messages", messages)
                addProperty("temperature", 0.3)
                addProperty("max_tokens", MAX_ANSWER_TOKENS)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = UrlManager.createRequestBuilder(ENDPOINT, body)
                .header("Authorization", "Bearer " + key)
                .build()
            // Interactive one-shot call while the user waits on a chat reply - generous but
            // finite, same reasoning as AiCrashAdvisor's shorter variant.
            val client = UrlManager
                .createOkHttpClientBuilder { it.callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logging.w(TAG, "AI request failed: HTTP " + response.code)
                    return@runCatching null
                }
                val responseBody = response.body?.string() ?: return@runCatching null
                JsonParser.parseString(responseBody).asJsonObject
                    .getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                    ?.getAsJsonObject("message")
                    ?.get("content")?.asString
                    ?.trim()
            }
        }.onFailure { t -> Logging.w(TAG, "AI request failed", t) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { it != localAnswer?.trim() }
    }
}
