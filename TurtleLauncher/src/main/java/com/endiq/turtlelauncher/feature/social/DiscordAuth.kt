package com.endiq.turtlelauncher.feature.social

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.path.UrlManager
import okhttp3.FormBody
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * Discord application constants for the Friends feature.
 *
 * [APPLICATION_ID] is Turtle Launcher's registered Discord application - the same one Rich
 * Presence already publishes under (1560576927419338863). It is a *public client*: the OAuth2
 * authorization-code flow uses PKCE, so no client secret ships in the APK and none has to.
 *
 * For account linking to work, that application must have, in the Discord Developer Portal:
 *   1. OAuth2 -> General -> Redirect URI: exactly [DEFAULT_REDIRECT_URI]
 *      (the launcher also lets this be overridden in Settings, and shows the value it will
 *      send, so it is easy to paste the same string into the portal).
 *   2. The scopes listed in [DEFAULT_SCOPES]. `identify` is always available; `guilds`
 *      powers the Communities/channel linking. `relationships.read` is restricted by
 *      Discord (it needs to be granted to the app), which is why a 403 from the
 *      relationships endpoint is surfaced as "friends list needs relationships.read"
 *      rather than hidden - linking, presence-free Discord friend *requests* and account
 *      merging keep working without it.
 */
object DiscordConfig {
    const val APPLICATION_ID: String = "1560576927419338863"
    const val DEFAULT_REDIRECT_URI: String = "turtlelauncher://discord-callback"
    const val AUTHORIZE_URL: String = "https://discord.com/oauth2/authorize"
    const val TOKEN_URL: String = "https://discord.com/api/oauth2/token"
    const val API_BASE: String = "https://discord.com/api/v10"
    const val USER_AGENT: String = "TurtleLauncher (https://github.com/Endiq-jar/TurtleLauncher, 1.0)"

    /** Conservative default: everything here is either public or requested explicitly. */
    const val DEFAULT_SCOPES: String = "identify guilds relationships.read"
}

/** Persisted OAuth2 token set. Never logged, never copied into launcher settings. */
internal data class DiscordTokenFile(
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAtMs: Long = 0L,
    val scope: String = "",
) {
    fun isUsable(nowMs: Long): Boolean =
        accessToken.isNotBlank() && expiresAtMs - CLOCK_SKEW_MS > nowMs

    companion object {
        /** Refresh a little early so a request never races the expiry. */
        const val CLOCK_SKEW_MS = 60_000L
    }
}

/** Raw-token storage; only [DiscordOAuthClient] should touch this. */
internal object DiscordTokenStore {
    private const val FILE_NAME = "discord_token.json"

    fun load(): DiscordTokenFile? = SocialStorage.read(FILE_NAME, DiscordTokenFile::class.java)

    fun save(token: DiscordTokenFile) = SocialStorage.write(FILE_NAME, token)

    fun clear() = SocialStorage.delete(FILE_NAME)
}

/**
 * Discord account linking for the Friends feature: OAuth2 authorization code + PKCE,
 * redirect handling, token refresh, and unlinking.
 *
 * Flow (public client - there is no client secret anywhere in this flow):
 *  1. [beginAuth] builds the authorize URL (state + S256 code challenge) and opens the
 *     system browser. The pending verifier/state are stored so a process restart between
 *     browser and callback does not break the login.
 *  2. Discord redirects to `turtlelauncher://discord-callback?code=...&state=...`, which
 *     `MainActivity` forwards to [handleRedirect].
 *  3. The code is exchanged for tokens, `/users/@me` is read, and the profile is merged onto
 *     the local provisional identity by [SocialIdentityStore.linkDiscord].
 */
object DiscordOAuthClient {
    private const val TAG = "DiscordOAuth"
    private const val PENDING_FILE = "oauth_pending.json"

    /** How long to wait after a failed refresh before asking Discord's token endpoint again. */
    private const val REFRESH_COOLDOWN_MS = 60_000L
    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val client by lazy { UrlManager.createOkHttpClient() }
    private val random = SecureRandom()

    /** Stored between step 1 and step 2 so the verifier survives a process restart. */
    data class PendingAuth(
        val verifier: String = "",
        val state: String = "",
        val redirectUri: String = "",
        val createdAtMs: Long = 0L,
    )

    /** The redirect URI the launcher will ask Discord to send the user back to. */
    fun redirectUri(): String =
        AllSettings.discordOAuthRedirectUri.getValue().trim()
            .ifBlank { DiscordConfig.DEFAULT_REDIRECT_URI }

    /**
     * Scopes that only work for a bot install (`bot` and the `applications.*` family) or for a
     * webhook. Sending them on a public-client user authorization makes Discord reject the whole
     * request with `invalid_scope`, which looks like "sign-in is broken" from the app's side, so
     * they are dropped before the URL is built and reported to the UI instead.
     */
    private val BOT_ONLYISH_SCOPES = setOf(
        "bot",
        "applications.builds.read",
        "applications.commands",
        "applications.commands.permissions.update",
        "applications.entitlements",
        "applications.store.update",
        "webhook.incoming",
    )

    /** The three scopes the Friends feature actually consumes. */
    val RECOMMENDED_SCOPES: String = DiscordConfig.DEFAULT_SCOPES

    /**
     * Splits a configured scope string into what can be requested and what needed a bot token.
     * Falls back to [RECOMMENDED_SCOPES] when nothing usable is left.
     */
    fun sanitizeScopes(raw: String): Pair<List<String>, List<String>> {
        val requested = raw.split(' ', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        val dropped = requested.filter { it.lowercase() in BOT_ONLYISH_SCOPES }
        val usable = requested.filterNot { it.lowercase() in BOT_ONLYISH_SCOPES }
        val effective = usable.ifEmpty {
            DiscordConfig.DEFAULT_SCOPES.split(' ').filter { it.isNotBlank() }
        }
        return effective to dropped
    }

    fun scopes(): String =
        sanitizeScopes(
            AllSettings.discordOAuthScopes.getValue().trim()
                .ifBlank { DiscordConfig.DEFAULT_SCOPES }
        ).first.joinToString(" ")

    /** Bot-only scopes named in Settings that cannot be granted here. */
    fun unsupportedScopes(): List<String> =
        sanitizeScopes(
            AllSettings.discordOAuthScopes.getValue().trim()
                .ifBlank { DiscordConfig.DEFAULT_SCOPES }
        ).second

    fun isLinked(): Boolean = SocialIdentityStore.current().hasDiscordLink

    fun hasToken(): Boolean = DiscordTokenStore.load()?.accessToken?.isNotBlank() == true

    fun pendingAuth(): PendingAuth? {
        val pending = SocialStorage.read(PENDING_FILE, PendingAuth::class.java) ?: return null
        // Ten minutes is plenty for "tap link, sign in, come back"; older entries would only
        // fail at the token endpoint anyway.
        if (System.currentTimeMillis() - pending.createdAtMs > 10 * 60_000L) return null
        return pending.takeIf { it.verifier.isNotBlank() && it.state.isNotBlank() }
    }

    /**
     * Starts the flow. Returns the URL that was opened, so the caller can fall back to
     * showing it (e.g. when no browser is available) instead of leaving the user stuck.
     */
    fun beginAuth(context: Context): Result<String> = runCatching {
        val verifier = randomToken(64)
        val state = randomToken(32)
        val redirectUri = redirectUri()

        SocialStorage.write(
            PENDING_FILE,
            PendingAuth(verifier, state, redirectUri, System.currentTimeMillis()),
        )

        val url = Uri.parse(DiscordConfig.AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("client_id", DiscordConfig.APPLICATION_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", scopes())
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", pkceChallenge(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("prompt", "consent")
            .build()
            .toString()

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        Logging.i(TAG, "Opened Discord authorization (redirect: $redirectUri)")
        url
    }

    /**
     * Completes the flow from the redirect URI. Synchronous (two network calls), so call it
     * off the main thread.
     */
    fun handleRedirect(uri: Uri): Result<SocialIdentity> = runCatching {
        val error = uri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            val description = uri.getQueryParameter("error_description").orEmpty()
            SocialStorage.delete(PENDING_FILE)
            throw IllegalStateException(
                when {
                    // The authorization URL asked for something Discord will not grant to a
                    // public client - almost always a bot-only scope pasted into Settings.
                    error == "invalid_scope" ->
                        "Discord rejected the requested scopes. Use only: $RECOMMENDED_SCOPES " +
                            "(bot-only scopes such as bot/applications.* cannot be used here)."
                    description.isNotBlank() -> description
                    else -> "Discord returned '$error'."
                }
            )
        }

        val pending = pendingAuth()
            ?: throw IllegalStateException("No Discord sign-in was in progress.")
        val returnedState = uri.getQueryParameter("state")
        if (returnedState != pending.state) {
            SocialStorage.delete(PENDING_FILE)
            throw IllegalStateException("Discord sign-in state did not match - please try again.")
        }
        val code = uri.getQueryParameter("code")
            ?: throw IllegalStateException("Discord did not return an authorization code.")

        val token = exchangeCode(code, pending)
        DiscordTokenStore.save(token)
        SocialStorage.delete(PENDING_FILE)

        val accessToken = token.accessToken
        val profile = DiscordRestClient.me(accessToken)
        val identity = SocialIdentityStore.linkDiscord(profile)
        Logging.i(TAG, "Linked Discord account ${profile.username} (${profile.id})")
        identity
    }

    fun unlink(): SocialIdentity {
        DiscordTokenStore.clear()
        return SocialIdentityStore.unlinkDiscord()
    }

    /** Scopes Discord actually granted, straight from the stored token response. */
    fun grantedScopes(): Set<String> =
        DiscordTokenStore.load()
            ?.scope
            ?.split(' ', ',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()

    /**
     * True when the stored token is known to carry [scope]. A token that recorded no scopes at
     * all is treated as "unknown" rather than "missing", so an older token is not blocked here.
     */
    fun hasScope(scope: String): Boolean {
        val granted = grantedScopes()
        return granted.isEmpty() || granted.contains(scope)
    }

    /** Drops the tokens but keeps the merged identity, e.g. after Discord rejected a session. */
    fun clearToken() = DiscordTokenStore.clear()

    /**
     * Repairs a rejected/expired session in one tap: drop the tokens and start the sign-in again.
     * The merged identity (Discord profile + Turtle friends) is untouched, so the user does not
     * have to unlink - which used to be the only way out of "reconnect your account".
     */
    fun reconnect(context: Context): Result<String> {
        DiscordTokenStore.clear()
        return beginAuth(context)
    }

    /**
     * A valid access token, refreshing it when needed. Returns null when not linked, when the
     * refresh token is gone/rejected, or when the request could not be made.
     *
     * [forceRefresh] skips the "expiry says it is still good" shortcut. That matters after a 401:
     * Discord can reject a token whose local expiry has not passed yet (revoked by a re-consent,
     * clock skew, password change), and the only way to tell a dead session from a stale one is
     * to trade the refresh token for a new one.
     */
    /**
     * Serialises refreshes. Discord rotates refresh tokens on use, so two concurrent refreshes
     * (presence tick + a send, say) would invalidate each other and permanently break a link that
     * was perfectly healthy. One refresh at a time, and the stored token is re-read under the lock.
     */
    private val refreshLock = Any()

    /** Backs off the token endpoint after a failure so a broken refresh token is not hammered. */
    @Volatile private var lastRefreshFailureMs = 0L

    fun accessToken(forceRefresh: Boolean = false): String? {
        if (!isLinked()) return null
        synchronized(refreshLock) {
            val stored = DiscordTokenStore.load() ?: return null
            val now = System.currentTimeMillis()
            if (!forceRefresh && stored.isUsable(now)) return stored.accessToken

            if (stored.refreshToken.isBlank()) {
                Logging.w(TAG, "Access token expired and no refresh token is stored")
                return null
            }
            if (now - lastRefreshFailureMs < REFRESH_COOLDOWN_MS) return null

            return runCatching {
                val refreshed = refreshToken(stored.refreshToken)
                DiscordTokenStore.save(refreshed)
                lastRefreshFailureMs = 0L
                refreshed.accessToken
            }.onFailure { e ->
                lastRefreshFailureMs = System.currentTimeMillis()
                Logging.w(TAG, "Could not refresh Discord token", e)
            }.getOrNull()
        }
    }

    // ── OAuth2 plumbing ────────────────────────────────────────────────────────────

    private fun exchangeCode(code: String, pending: PendingAuth): DiscordTokenFile {
        val body = FormBody.Builder()
            .add("client_id", DiscordConfig.APPLICATION_ID)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", pending.redirectUri)
            .add("code_verifier", pending.verifier)
            .build()
        return requestToken(body)
    }

    private fun refreshToken(refreshToken: String): DiscordTokenFile {
        val body = FormBody.Builder()
            .add("client_id", DiscordConfig.APPLICATION_ID)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build()
        val token = requestToken(body)
        // Discord may omit the refresh token on refresh (it keeps the old one valid).
        return if (token.refreshToken.isBlank()) token.copy(refreshToken = refreshToken) else token
    }

    private fun requestToken(body: FormBody): DiscordTokenFile {
        val request = Request.Builder()
            .url(DiscordConfig.TOKEN_URL)
            .header("User-Agent", DiscordConfig.USER_AGENT)
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val description = runCatching {
                    JsonParser.parseString(payload).asJsonObject.get("error_description")?.asString
                }.getOrNull()
                throw IllegalStateException(
                    description?.takeIf { it.isNotBlank() }
                        ?: "Discord rejected the sign-in (HTTP ${response.code})."
                )
            }
            val json = JsonParser.parseString(payload).asJsonObject
            return tokenFrom(json)
        }
    }

    private fun tokenFrom(json: JsonObject): DiscordTokenFile {
        val accessToken = json.get("access_token")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        if (accessToken.isBlank()) throw IllegalStateException("Discord returned no access token.")
        val expiresIn = json.get("expires_in")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
        return DiscordTokenFile(
            accessToken = accessToken,
            refreshToken = json.get("refresh_token")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
            expiresAtMs = System.currentTimeMillis() + expiresIn * 1000L,
            scope = json.get("scope")?.takeIf { !it.isJsonNull }?.asString.orEmpty(),
        )
    }

    private fun randomToken(length: Int): String {
        val charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val builder = StringBuilder(length)
        repeat(length) { builder.append(charset[random.nextInt(charset.length)]) }
        return builder.toString()
    }

    private fun pkceChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /** Exposed for the "copy this URI into the Discord portal" row and for diagnostics. */
    fun describeConfiguration(): String {
        val state = if (isLinked()) "linked" else "not linked"
        val dropped = unsupportedScopes()
        val ignored = if (dropped.isEmpty()) "" else " · ignored ${dropped.joinToString(" ")}"
        return "app ${DiscordConfig.APPLICATION_ID} · $state · redirect ${redirectUri()} · scopes ${scopes()}$ignored"
    }

    internal fun pendingAuthForDebug(): String = gson.toJson(pendingAuth())
}
