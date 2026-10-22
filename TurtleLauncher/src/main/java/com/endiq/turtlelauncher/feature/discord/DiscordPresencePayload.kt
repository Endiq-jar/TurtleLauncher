package com.endiq.turtlelauncher.feature.discord

/** SDK field limits, applied before JNI without splitting surrogate pairs. */
internal object DiscordPresencePayload {
    fun text(value: String, maximum: Int = 128, minimum: Int = 2): String {
        val trimmed = value.trim()
        val count = trimmed.codePointCount(0, trimmed.length)
        if (count < minimum) return ""
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, count.coerceAtMost(maximum)))
    }

    private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    /**
     * Normalizes and validates a Rich Presence button link. Users very commonly paste a
     * bare link (e.g. "discord.gg/invite") with no scheme - rather than silently dropping
     * the button (previous behavior: a scheme-less value failed [java.net.URI]'s host
     * check and this returned null with no feedback anywhere), assume https:// for any
     * value that doesn't already declare its own scheme.
     */
    fun buttonUrl(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        val candidate = if (SCHEME_PREFIX.containsMatchIn(trimmed)) trimmed else "https://$trimmed"
        if (candidate.length > 512) return null // truncating a URL can change its destination or invalidate it
        val uri = runCatching { java.net.URI(candidate) }.getOrNull() ?: return null
        return candidate.takeIf {
            (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) && !uri.host.isNullOrBlank()
        }
    }
}
