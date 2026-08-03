package com.movtery.zalithlauncher.ui.subassembly.aichat

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.utils.path.UrlManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * General-purpose AI chat, backing the Quick Actions "AI Chat" button. Reuses the same
 * calling pattern as [com.movtery.zalithlauncher.feature.log.AiCrashAdvisor] (same
 * settings for API key/model, same request/response shape), but with a general assistant
 * system prompt and full conversation history instead of a one-shot crash log.
 *
 * Uses OpenAI's Chat Completions API (switched from Gemini after the previous hardcoded
 * default key got flagged as leaked and revoked - see AllSettings.aiApiKey). No key is
 * shipped by default here either, for the same reason: any real key committed to this
 * public repo gets caught by secret-scanning and revoked the same way, so there's no
 * hardcoded default to fall back on - the user has to supply their own.
 */
sealed class ChatResult {
    data class Success(val text: String) : ChatResult()
    /** [reason] is a short, human-readable cause - shown directly in the chat UI so a
     *  failure is diagnosable from the app itself instead of just "didn't go through". */
    data class Failure(val reason: String) : ChatResult()
}

object AiChatAdvisor {

    private const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
    private const val MAX_HISTORY_MESSAGES = 20

    private const val SYSTEM_PROMPT =
        "You are a helpful assistant built into an Android Minecraft launcher called " +
        "TurtleLauncher (a PojavLauncher/ZalithLauncher-family fork). You can help with " +
        "Minecraft, modding, and general questions the player might have. Keep replies " +
        "concise and easy to read on a phone screen."

    /**
     * Sends [history] (oldest first, last entry is the newest user message) to OpenAI and
     * returns the reply, or a [ChatResult.Failure] with a specific human-readable reason if
     * AI chat is unavailable or the request fails for any reason. Blocking - call from a
     * background thread.
     */
    @JvmStatic
    fun sendMessage(history: List<ChatMessage>): ChatResult {
        val apiKey = runCatching { AllSettings.aiApiKey.getValue() }.getOrDefault("").trim()
        if (apiKey.isEmpty()) return ChatResult.Failure("No OpenAI API key configured.")
        if (history.isEmpty()) return ChatResult.Failure("Nothing to send.")

        val model = runCatching { AllSettings.aiModel.getValue() }.getOrDefault("gpt-4o-mini")
            .ifBlank { "gpt-4o-mini" }
        val trimmedHistory = history.takeLast(MAX_HISTORY_MESSAGES)

        return runCatching {
            val messages = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", SYSTEM_PROMPT)
                })
                trimmedHistory.forEach { message ->
                    add(JsonObject().apply {
                        addProperty("role", if (message.isUser) "user" else "assistant")
                        addProperty("content", message.text)
                    })
                }
            }
            val requestBody = JsonObject().apply {
                addProperty("model", model)
                add("messages", messages)
                addProperty("temperature", 0.6)
                addProperty("max_tokens", 800)
            }

            val body = requestBody.toString().toRequestBody("application/json".toMediaType())
            val request = UrlManager.createRequestBuilder(ENDPOINT, body)
                .header("Authorization", "Bearer $apiKey")
                .build()

            // Independent client with a longer timeout than AiCrashAdvisor's - this is an
            // interactive chat the user is actively waiting on, not a background crash
            // report, but replies can still take a while to generate.
            val client = UrlManager.createOkHttpClientBuilder { it.callTimeout(45, TimeUnit.SECONDS) }.build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()

                if (!response.isSuccessful) {
                    // OpenAI's own error body is JSON shaped like {"error":{"message":"...",
                    // "type":"...","code":"..."}} - surface that message directly (e.g. "You
                    // exceeded your current quota", "Incorrect API key provided") instead of
                    // just the HTTP code, since that's almost always the actually useful part.
                    val apiMessage = responseBody
                        ?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
                        ?.getAsJsonObject("error")?.get("message")?.asString
                    val reason = "OpenAI API error ${response.code}" + (apiMessage?.let { ": $it" } ?: "")
                    Logging.w("AiChatAdvisor", "OpenAI request failed: $reason")
                    return@runCatching ChatResult.Failure(reason)
                }

                if (responseBody == null) return@runCatching ChatResult.Failure("Empty response from OpenAI.")

                val json = JsonParser.parseString(responseBody).asJsonObject
                val choice = json.getAsJsonArray("choices")?.firstOrNull()?.asJsonObject
                val text = choice
                    ?.getAsJsonObject("message")
                    ?.get("content")?.asString
                    ?.trim()

                if (!text.isNullOrBlank()) {
                    ChatResult.Success(text)
                } else {
                    // No content usually means the reply was cut off or filtered - OpenAI
                    // reports this via choices[0].finish_reason (e.g. "length",
                    // "content_filter") rather than an HTTP error.
                    val finishReason = choice?.get("finish_reason")?.asString
                    ChatResult.Failure(
                        "OpenAI returned no reply" + (finishReason?.let { " (reason: $it)" } ?: "") + "."
                    )
                }
            }
        }.getOrElse { e ->
            Logging.w("AiChatAdvisor", "AI chat message failed", e)
            ChatResult.Failure(e.message?.let { "${e.javaClass.simpleName}: $it" } ?: e.javaClass.simpleName)
        }
    }

    /** Whether the AI chat feature has an API key configured at all (chat is otherwise unusable). */
    @JvmStatic
    fun hasApiKey(): Boolean =
        runCatching { AllSettings.aiApiKey.getValue() }.getOrDefault("").isNotBlank()
}
