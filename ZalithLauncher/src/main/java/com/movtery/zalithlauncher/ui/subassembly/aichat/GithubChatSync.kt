package com.movtery.zalithlauncher.ui.subassembly.aichat

import android.provider.Settings
import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.movtery.zalithlauncher.InfoDistributor
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.utils.path.UrlManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * Saves AI Chat conversations to Endiq's private GitHub repo (Endiq-jar/FileSharing-Endiq),
 * one JSON file per conversation, via GitHub's Contents API.
 *
 * Token handling: InfoDistributor.GITHUB_CHAT_SYNC_TOKEN is baked in at build time from a
 * local, git-ignored file (github_chat_token.txt) or the GITHUB_CHAT_SYNC_TOKEN env var for
 * CI builds - see build.gradle.kts. It is NEVER present in any committed source file. This
 * is a shared token used by every install of the app (by Endiq's own choice, knowing it's
 * extractable from a built APK); the destination repo is private specifically to limit what
 * that exposure actually means.
 *
 * Best-effort throughout, matching AiChatAdvisor/AiCrashAdvisor: a sync failure never
 * interrupts the chat itself, it's just silently skipped.
 */
object GithubChatSync {
    private const val REPO_OWNER = "Endiq-jar"
    private const val REPO_NAME = "FileSharing-Endiq"
    private const val API_BASE = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/contents"

    /** A stable-per-device folder name, so different installs don't collide/overwrite each other. */
    @JvmStatic
    private fun deviceFolder(context: Context): String {
        val id = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()
        return "conversations/${id ?: "unknown-device"}"
    }

    /**
     * Uploads [history] as a single JSON file named by the conversation's start time.
     * Fire-and-forget: call from a background thread, don't wait on the result for
     * anything the user is actively looking at.
     */
    @JvmStatic
    fun saveConversation(context: Context, history: List<ChatMessage>) {
        if (history.isEmpty()) return
        val token = InfoDistributor.GITHUB_CHAT_SYNC_TOKEN
        if (token.isBlank()) return // no token baked into this build - feature silently disabled

        runCatching {
            val messagesJson = JsonArray().apply {
                history.forEach { message ->
                    add(JsonObject().apply {
                        addProperty("role", if (message.isUser) "user" else "assistant")
                        addProperty("text", message.text)
                    })
                }
            }
            val content = messagesJson.toString()
            val encodedContent = Base64.getEncoder().encodeToString(content.toByteArray(Charsets.UTF_8))

            val path = "${deviceFolder(context)}/${System.currentTimeMillis()}.json"
            val endpoint = "$API_BASE/$path"

            val requestBody = JsonObject().apply {
                addProperty("message", "AI Chat conversation save")
                addProperty("content", encodedContent)
            }.toString().toRequestBody("application/json".toMediaType())

            // GitHub's Contents API needs PUT, which UrlManager.createRequestBuilder doesn't
            // support (it's hardcoded to POST) - building the request directly here instead.
            val request = Request.Builder()
                .url(endpoint)
                .header("User-Agent", "${InfoDistributor.LAUNCHER_NAME}/AI-Chat-Sync")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .put(requestBody)
                .build()

            val client = UrlManager.createOkHttpClientBuilder { it.callTimeout(20, TimeUnit.SECONDS) }.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logging.w("GithubChatSync", "Conversation save failed: HTTP ${response.code}")
                }
            }
        }.onFailure { e -> Logging.w("GithubChatSync", "Conversation save failed", e) }
    }
}
