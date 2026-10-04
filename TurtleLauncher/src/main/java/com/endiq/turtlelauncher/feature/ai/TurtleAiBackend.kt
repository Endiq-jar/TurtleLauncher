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
 * The optional cloud half of Turtle AI: chat-completion calls, using the user's own API key,
 * with the full Turtle AI identity ([TurtleAiPrompt.systemPrompt]) as the system prompt.
 *
 * What this exists for that the on-device rule engine cannot do:
 *
 *  1. **Other languages.** The offline engine's answers are English. Here the model is told
 *     which language to answer in and is handed the launcher's own verified answer as context,
 *     so a Hindi/Portuguese/Japanese question gets a full answer in that language *and* still
 *     reflects what this launcher actually does instead of a generic guess.
 *  2. **Anything outside the launcher** - writing, code, calculations, general questions.
 *     With web search results attached the model answers under the citation rules in
 *     [TurtleAiPrompt.WEB_SEARCH]; [TurtleAiPrompt.WRITING], [.CODING], [.CALCULATION] and
 *     [.ANSWER_VERIFICATION] tell it how to do the rest.
 *  3. **Continuity.** Earlier turns are replayed to the model ([Exchange]), because a follow-up
 *     like "make it faster" or "now in Python" is not a question on its own.
 *
 * The endpoint is any OpenAI-compatible one (`aiApiBaseUrl`, default OpenAI) and the model is
 * whatever the user names in `aiModel`, so a stronger model is a settings change, not a code
 * change.
 *
 * It is off unless the user turns it on (`aiAssistantCloudBrain`) and needs their key. It does
 * not run for a question the rule engine already answers in English - the on-device answer is
 * faster, free and cannot drift from the launcher's real behaviour. The exception is a request
 * for produced work (code, a calculation, a piece of writing): the rule engine cannot do that
 * at all, so those go to the model even in English.
 */
object TurtleAiBackend {

    private const val TAG = "TurtleAiBackend"

    /**
     * Enough for a real answer: a guide, a worked calculation or a file of code. The first
     * version capped this at 800, which truncated exactly the answers the AI brain exists for -
     * the user saw a sentence stop mid-word with no explanation.
     */
    private const val MAX_ANSWER_TOKENS = 2000
    private const val TIMEOUT_SECONDS = 45L

    /** How much of the conversation is replayed for context, newest last. */
    private const val MAX_HISTORY_TURNS = 6
    private const val MAX_HISTORY_QUESTION_CHARS = 240
    private const val MAX_HISTORY_ANSWER_CHARS = 700

    /** One earlier question/answer pair of the conversation, oldest first. */
    data class Exchange(val question: String, val answer: String)

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
     * @param history       earlier turns of this conversation, oldest first, so follow-up
     *                      questions resolve ("make it faster", "now in Python"). Bounded by
     *                      [MAX_HISTORY_TURNS]; anything older is dropped rather than sent.
     * @param deviceFacts   live readings from this device (RAM, renderer, version, storage)
     *                      from [TurtleAssistant]. Facts for the model to calculate from,
     *                      not text to echo.
     * @return the answer, or null when the request cannot be made or fails (caller falls back
     *         to the on-device text/raw search results).
     */
    @JvmStatic
    fun ask(
        context: Context,
        question: String,
        languageTag: String,
        localAnswer: String?,
        searchBlock: String?,
        history: List<Exchange> = emptyList(),
        deviceFacts: String? = null
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
            if (!deviceFacts.isNullOrBlank()) {
                append("\n\nLive readings from this device, gathered just now (facts - use them if the ")
                append("question needs them, and never contradict or invent other device figures):\n")
                append(deviceFacts.trim())
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
                // The tail of the conversation, oldest first. Older turns are dropped: they
                // cost tokens on every request and the newest ones are what a follow-up
                // refers to.
                history.takeLast(MAX_HISTORY_TURNS).forEach { exchange ->
                    val previousQuestion = exchange.question.trim().take(MAX_HISTORY_QUESTION_CHARS)
                    val previousAnswer = exchange.answer.trim().take(MAX_HISTORY_ANSWER_CHARS)
                    if (previousQuestion.isBlank() || previousAnswer.isBlank()) return@forEach
                    add(JsonObject().apply {
                        addProperty("role", "user")
                        addProperty("content", previousQuestion)
                    })
                    add(JsonObject().apply {
                        addProperty("role", "assistant")
                        addProperty("content", previousAnswer)
                    })
                }
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

            val request = UrlManager.createRequestBuilder(TurtleAiEndpoint.chatCompletions(), body)
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
                val choice = JsonParser.parseString(responseBody).asJsonObject
                    .getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                    ?: return@runCatching null
                if (choice.get("finish_reason")?.takeIf { it.isJsonPrimitive }?.asString == "length") {
                    // The answer was cut off by the token cap. Not user-visible (any marker
                    // written here would be in the wrong language) but the one thing worth
                    // seeing in a bug report.
                    Logging.w(TAG, "AI answer hit the " + MAX_ANSWER_TOKENS + "-token cap")
                }
                choice.getAsJsonObject("message")
                    ?.get("content")?.asString
                    ?.trim()
            }
        }.onFailure { t -> Logging.w(TAG, "AI request failed", t) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { it != localAnswer?.trim() }
    }
}
