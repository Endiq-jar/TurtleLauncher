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
 * Gemini API calling pattern as [com.movtery.zalithlauncher.feature.log.AiCrashAdvisor]
 * (same settings for API key/model, same request/response shape), but with a general
 * assistant system prompt and full conversation history instead of a one-shot crash log.
 */
sealed class ChatResult {
    data class Success(val text: String) : ChatResult()
    /** [reason] is a short, human-readable cause - shown directly in the chat UI so a
     *  failure is diagnosable from the app itself instead of just "didn't go through". */
    data class Failure(val reason: String) : ChatResult()
}

object AiChatAdvisor {

    private const val ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    private const val MAX_HISTORY_MESSAGES = 20

    private const val SYSTEM_PROMPT =
        "You are a helpful assistant built into an Android Minecraft launcher called " +
        "TurtleLauncher (a PojavLauncher/ZalithLauncher-family fork). You can help with " +
        "Minecraft, modding, and general questions the player might have. Keep replies " +
        "concise and easy to read on a phone screen."

    /**
     * Sends [history] (oldest first, last entry is the newest user message) to Gemini and
     * returns the reply, or a [ChatResult.Failure] with a specific human-readable reason if
     * AI chat is unavailable or the request fails for any reason. Blocking - call from a
     * background thread.
     */
    @JvmStatic
    fun sendMessage(history: List<ChatMessage>): ChatResult {
        val apiKey = runCatching { AllSettings.aiCrashHelpApiKey.getValue() }.getOrDefault("").trim()
        if (apiKey.isEmpty()) return ChatResult.Failure("No Gemini API key configured.")
        if (history.isEmpty()) return ChatResult.Failure("Nothing to send.")

        val model = runCatching { AllSettings.aiCrashHelpModel.getValue() }.getOrDefault("gemini-flash-latest")
            .ifBlank { "gemini-flash-latest" }
        val trimmedHistory = history.takeLast(MAX_HISTORY_MESSAGES)

        return runCatching {
            val contents = JsonArray().apply {
                trimmedHistory.forEach { message ->
                    add(JsonObject().apply {
                        addProperty("role", if (message.isUser) "user" else "model")
                        add("parts", JsonArray().apply {
                            add(JsonObject().apply { addProperty("text", message.text) })
                        })
                    })
                }
            }
            val systemInstruction = JsonObject().apply {
                add("parts", JsonArray().apply { add(JsonObject().apply { addProperty("text", SYSTEM_PROMPT) }) })
            }
            val generationConfig = JsonObject().apply {
                addProperty("temperature", 0.6)
                addProperty("maxOutputTokens", 800)
            }
            val requestBody = JsonObject().apply {
                add("contents", contents)
                add("systemInstruction", systemInstruction)
                add("generationConfig", generationConfig)
            }

            val body = requestBody.toString().toRequestBody("application/json".toMediaType())
            val endpoint = "$ENDPOINT_BASE/$model:generateContent"
            val request = UrlManager.createRequestBuilder(endpoint, body)
                .header("x-goog-api-key", apiKey)
                .build()

            // Independent client with a longer timeout than AiCrashAdvisor's - this is an
            // interactive chat the user is actively waiting on, not a background crash
            // report, but replies can still take a while to generate.
            val client = UrlManager.createOkHttpClientBuilder { it.callTimeout(45, TimeUnit.SECONDS) }.build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()

                if (!response.isSuccessful) {
                    // Gemini's own error body is JSON shaped like {"error":{"code":...,
                    // "message":"...","status":"..."}} - surface that message directly
                    // (e.g. "API key not valid", "quota exceeded") instead of just the HTTP
                    // code, since that's almost always the actually useful part.
                    val apiMessage = responseBody
                        ?.let { runCatching { JsonParser.parseString(it).asJsonObject } .getOrNull() }
                        ?.getAsJsonObject("error")?.get("message")?.asString
                    val reason = "Gemini API error ${response.code}" + (apiMessage?.let { ": $it" } ?: "")
                    Logging.w("AiChatAdvisor", "Gemini request failed: $reason")
                    return@runCatching ChatResult.Failure(reason)
                }

                if (responseBody == null) return@runCatching ChatResult.Failure("Empty response from Gemini.")

                val json = JsonParser.parseString(responseBody).asJsonObject
                val candidate = json.getAsJsonArray("candidates")?.firstOrNull()?.asJsonObject
                val text = candidate
                    ?.getAsJsonObject("content")
                    ?.getAsJsonArray("parts")
                    ?.firstOrNull()?.asJsonObject
                    ?.get("text")?.asString
                    ?.trim()

                if (!text.isNullOrBlank()) {
                    ChatResult.Success(text)
                } else {
                    // No text usually means the reply was blocked - Gemini reports this via
                    // candidates[0].finishReason (e.g. "SAFETY", "RECITATION", "MAX_TOKENS")
                    // rather than an HTTP error, so this is the only place it's visible.
                    val finishReason = candidate?.get("finishReason")?.asString
                    ChatResult.Failure(
                        "Gemini returned no reply" + (finishReason?.let { " (reason: $it)" } ?: "") + "."
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
        runCatching { AllSettings.aiCrashHelpApiKey.getValue() }.getOrDefault("").isNotBlank()
}
