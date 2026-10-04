package com.endiq.turtlelauncher.feature.ai

import com.endiq.turtlelauncher.setting.AllSettings

/**
 * Where the launcher's OpenAI-compatible calls go.
 *
 * Every AI feature in the launcher - the Assistant's brain, the crash advisor and the
 * skin/cape filter - posts to `<base>/chat/completions` with the user's key as a Bearer
 * token. Keeping the URL in one place means a user who points the launcher at a different
 * provider (or a local server: llama.cpp, Ollama, LM Studio, vLLM, a proxy) gets *all* of
 * them moved over, instead of the setting quietly applying to one feature and not the others.
 *
 * Blank means OpenAI itself, so the default behaviour is unchanged for anyone who never
 * opens the setting.
 */
object TurtleAiEndpoint {

    const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    private const val CHAT_COMPLETIONS_PATH = "/chat/completions"

    /** The configured base URL, without a trailing slash; never blank. */
    @JvmStatic
    fun baseUrl(): String =
        runCatching { AllSettings.aiApiBaseUrl.getValue() }.getOrDefault("")
            .trim()
            .ifBlank { DEFAULT_BASE_URL }
            .trimEnd('/')

    /**
     * The chat-completions URL. A user who pastes the full endpoint path into the setting
     * gets it used as-is rather than as `<full url>/chat/completions`.
     */
    @JvmStatic
    fun chatCompletions(): String {
        val base = baseUrl()
        return if (base.endsWith(CHAT_COMPLETIONS_PATH)) base else base + CHAT_COMPLETIONS_PATH
    }
}
