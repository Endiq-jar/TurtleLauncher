package com.endiq.turtlelauncher.feature.skin

import com.google.gson.JsonParser
import com.endiq.turtlelauncher.feature.ai.TurtleAiGemini
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.path.UrlManager
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal object AiContentModerator {

    private const val MAX_CACHE_ENTRIES = 500

    private const val PROMPT =
        "You are an image content moderator for a Minecraft launcher's skin/cape gallery. " +
        "You will be shown a small Minecraft skin or cape texture image (a flat UV-mapped " +
        "pixel-art template, not a rendered 3D character). Judge only whether the texture " +
        "itself depicts anything overtly sexual, graphically violent/gory, or a hate symbol. " +
        "Ignore low resolution, odd proportions, or empty/transparent regions - those are " +
        "normal for this format. Reply with ONLY a JSON object, no other text: " +
        "{\"appropriate\": true or false}."

    private val cache = object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    private val client by lazy {
        UrlManager.createOkHttpClientBuilder { it.callTimeout(30, TimeUnit.SECONDS) }.build()
    }

    /**
     * Downloads [imageUrl] and classifies it, using [cacheKey] (e.g. "littleskin:123" or
     * "laby:<hash>") to dedupe repeat lookups within this process's lifetime. Returns null -
     * "unknown, don't block on this" - whenever the feature is off, unconfigured, or anything
     * about the request/response fails. Blocking - call from a background thread, same as
     * the [LabyModGalleryApi]/[LittleSkinGalleryApi] fetchGallery() functions this is meant
     * to be called from.
     */
    fun fetchAndCheck(cacheKey: String, imageUrl: String): Boolean? {
        if (!runCatching { AllSettings.aiSkinFilterEnabled.getValue() }.getOrDefault(false)) return null
        // No key (and none baked into the build) = the filter stays out of the way and every
        // image is treated as unknown, exactly as if the feature were off.
        if (TurtleAiGemini.apiKey().isEmpty()) return null

        synchronized(cache) { cache[cacheKey] }?.let { return it }

        val result = runCatching {
            val imageBytes = client.newCall(Request.Builder().url(imageUrl).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body?.bytes()
            } ?: return@runCatching null

            val replyText = TurtleAiGemini.askAboutImage(
                prompt = PROMPT,
                imageBytes = imageBytes,
                mimeType = mimeTypeOf(imageUrl)
            ) ?: return@runCatching null

            // Models occasionally wrap JSON in a code fence despite instructions not to -
            // strip that rather than fail the whole classification over formatting.
            val cleaned = replyText.trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            JsonParser.parseString(cleaned).asJsonObject.get("appropriate")?.asBoolean
        }.onFailure { e -> Logging.w("AiContentModerator", "Skin/cape moderation failed for $cacheKey", e) }
            .getOrNull()

        if (result != null) synchronized(cache) { cache[cacheKey] = result }
        return result
    }

    /** Gemini is told the media type explicitly; the gallery URLs carry it in the extension. */
    private fun mimeTypeOf(url: String): String {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".webp") -> "image/webp"
            path.endsWith(".gif") -> "image/gif"
            else -> "image/png"
        }
    }
}
