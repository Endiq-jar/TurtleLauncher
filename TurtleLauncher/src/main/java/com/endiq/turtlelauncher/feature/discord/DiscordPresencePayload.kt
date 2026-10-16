package com.endiq.turtlelauncher.feature.discord

/** SDK field limits, applied before JNI without splitting surrogate pairs. */
internal object DiscordPresencePayload {
    fun text(value: String, maximum: Int = 128, minimum: Int = 2): String {
        val trimmed = value.trim()
        val count = trimmed.codePointCount(0, trimmed.length)
        if (count < minimum) return ""
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, count.coerceAtMost(maximum)))
    }

    fun buttonUrl(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.length > 512) return null // truncating a URL can change its destination or invalidate it
        val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return null
        return trimmed.takeIf {
            (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) && !uri.host.isNullOrBlank()
        }
    }
}
