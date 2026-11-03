package com.endiq.turtlelauncher.feature.social

import com.google.gson.GsonBuilder
import com.endiq.turtlelauncher.feature.log.Logging
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Who "you" are, as far as the Friends feature is concerned.
 *
 * Two identities exist and both are first class:
 *
 *  - the **provisional** identity ([provisionalId] + [displayName]), created on first launch
 *    without any sign-in. It owns your Turtle friends, requests, parties and message history,
 *    and it keeps working if you never link anything.
 *  - the **linked** Discord identity ([discordUserId] and friends), obtained through the
 *    launcher's OAuth2 public-client flow.
 *
 * "Merging" is intentionally not destructive: linking a Discord account fills the Discord
 * fields on the *same* provisional record (see [SocialIdentityRules.merge]), so existing
 * Turtle friends, requests and conversations keep their keys and survive both linking and
 * unlinking. That is what makes "Handle linked and provisional users" safe to implement
 * without a server: there is only ever one local record.
 */
data class SocialIdentity(
    val provisionalId: String = "",
    val displayName: String = "",
    val discordUserId: String? = null,
    val discordUsername: String? = null,
    val discordDisplayName: String? = null,
    val discordAvatarUrl: String? = null,
    val linkedAtMs: Long = 0L,
    val mergedAtMs: Long = 0L,
    val linkCount: Int = 0,
) {
    val hasDiscordLink: Boolean get() = !discordUserId.isNullOrBlank()

    /** Discord's display name when linked, otherwise the local name. */
    val effectiveName: String
        get() = discordDisplayName?.takeIf { it.isNotBlank() }
            ?: discordUsername?.takeIf { it.isNotBlank() }
            ?: displayName.takeIf { it.isNotBlank() }
            ?: "Turtle player"
}

/** Minimal Discord user payload the identity layer needs. */
data class DiscordProfile(
    val id: String,
    val username: String,
    val globalName: String? = null,
    val avatarUrl: String? = null,
) {
    fun toIdentityFields(): DiscordProfile = this
}

/**
 * Pure merge/rename rules, kept out of [SocialIdentityStore] so they can be unit tested
 * without Android or the network (see SocialIdentityRulesTest).
 */
object SocialIdentityRules {
    const val MAX_DISPLAY_NAME_LENGTH = 32

    /** Trims, collapses whitespace and caps a user-entered display name. */
    fun sanitizeName(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ").take(MAX_DISPLAY_NAME_LENGTH)

    /**
     * Links [profile] onto [current]. Idempotent for the same account; when a *different*
     * Discord account is linked, the Discord fields are replaced but the provisional id -
     * and therefore every local friend/conversation - stays put.
     */
    fun merge(
        current: SocialIdentity,
        profile: DiscordProfile,
        nowMs: Long,
    ): SocialIdentity = current.copy(
        discordUserId = profile.id,
        discordUsername = profile.username,
        discordDisplayName = profile.globalName?.takeIf { it.isNotBlank() },
        discordAvatarUrl = profile.avatarUrl,
        linkedAtMs = if (current.hasDiscordLink && current.discordUserId == profile.id) {
            current.linkedAtMs
        } else {
            nowMs
        },
        mergedAtMs = nowMs,
        linkCount = if (current.discordUserId == profile.id) current.linkCount else current.linkCount + 1,
    )

    /** Disconnects Discord, keeping the provisional identity and everything attached to it. */
    fun unlink(current: SocialIdentity): SocialIdentity = current.copy(
        discordUserId = null,
        discordUsername = null,
        discordDisplayName = null,
        discordAvatarUrl = null,
        linkedAtMs = 0L,
    )

    fun rename(current: SocialIdentity, rawName: String): SocialIdentity {
        val name = sanitizeName(rawName)
        return if (name.isBlank()) current else current.copy(displayName = name)
    }
}

/**
 * Persists the identity and notifies listeners when it changes.
 *
 * The file is only written when the value actually changes, so presence polling and screen
 * rotation do not rewrite it.
 */
object SocialIdentityStore {
    private const val TAG = "SocialIdentityStore"
    private const val FILE_NAME = "identity.json"
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val listeners = CopyOnWriteArrayList<(SocialIdentity) -> Unit>()
    private val lock = Any()

    @Volatile
    private var cached: SocialIdentity? = null

    fun addListener(listener: (SocialIdentity) -> Unit) {
        listeners.add(listener)
        listener(current())
    }

    fun removeListener(listener: (SocialIdentity) -> Unit) {
        listeners.remove(listener)
    }

    /** The current identity, created (and persisted) on first call. */
    fun current(): SocialIdentity = synchronized(lock) {
        cached?.let { if (it.provisionalId.isNotBlank()) return it }

        val loaded = readFile()
        val identity = if (loaded != null && loaded.provisionalId.isNotBlank()) {
            loaded
        } else {
            // First run, or a file written by an older build without an id: mint a stable
            // provisional identity and write it immediately so it never changes again.
            SocialIdentity(
                provisionalId = UUID.randomUUID().toString(),
                displayName = "Turtle player",
            ).also { writeFile(it) }
        }
        cached = identity
        identity
    }

    fun linkDiscord(profile: DiscordProfile): SocialIdentity =
        update { current -> SocialIdentityRules.merge(current, profile, System.currentTimeMillis()) }

    fun unlinkDiscord(): SocialIdentity = update { current -> SocialIdentityRules.unlink(current) }

    fun rename(rawName: String): SocialIdentity =
        update { current -> SocialIdentityRules.rename(current, rawName) }

    private fun update(transform: (SocialIdentity) -> SocialIdentity): SocialIdentity {
        val updated = synchronized(lock) {
            val next = transform(current())
            if (next != cached) {
                cached = next
                writeFile(next)
            }
            next
        }
        listeners.forEach { listener -> runCatching { listener(updated) } }
        return updated
    }

    private fun readFile(): SocialIdentity? {
        val raw = SocialStorage.read(FILE_NAME, SocialIdentity::class.java) ?: return null
        // Gson bypasses Kotlin constructors, so a hand-edited/partial file can smuggle in
        // nulls for non-null fields; normalise before anyone relies on them.
        return raw.copy(
            provisionalId = raw.provisionalId.orEmpty(),
            displayName = raw.displayName.orEmpty().ifBlank { "Turtle player" },
        )
    }

    private fun writeFile(identity: SocialIdentity) {
        SocialStorage.write(FILE_NAME, identity)
        Logging.d(TAG, "identity saved (discord linked: ${identity.hasDiscordLink})")
    }

    /** Test/diagnostics helper - drops the in-memory copy so the next read hits the file. */
    internal fun invalidate() {
        synchronized(lock) { cached = null }
    }

    internal fun gsonForDebug(): String = gson.toJson(current())
}
