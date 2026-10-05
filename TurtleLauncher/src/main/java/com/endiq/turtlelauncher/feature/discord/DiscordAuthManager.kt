package com.endiq.turtlelauncher.feature.discord

import com.google.gson.JsonParser
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.path.UrlManager
import okhttp3.FormBody
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Discord account linking via OAuth2 Authorization Code + PKCE - the same shape as the
 * app's existing Microsoft login (an embedded WebView intercepts the redirect, see
 * DiscordLoginFragment), just with a PKCE code_verifier instead of a client secret, so
 * nothing secret has to live in the APK.
 *
 * REQUIRES A REAL DISCORD APPLICATION: [CLIENT_ID] below is a placeholder. Register one
 * at https://discord.com/developers/applications, add [REDIRECT_URI] under
 * OAuth2 -> Redirects, and drop the real client ID in here - this won't work until that's
 * done, same as any other OAuth integration needs its own registered app.
 *
 * This only identifies who's linked (username/avatar/id via the `identify` scope) and
 * stores their access/refresh token for that. It does NOT attempt to push "currently
 * playing" to the linked account's real Discord presence - there is no public API for a
 * third-party Android app to do that for a real user. Discord's actual Rich Presence
 * mechanism is a local IPC socket the official desktop client exposes to games running
 * on the same machine; it has no Android or remote equivalent. The only other way to
 * affect a user's live presence is to drive their account directly over the Gateway
 * (what Discord calls "self-botting" client automation), which Discord's Terms of
 * Service explicitly prohibits and which would mean this app holding and using a real
 * user session - not something to build quietly into a Minecraft launcher.
 */
object DiscordAuthManager {
    const val CLIENT_ID = "YOUR_DISCORD_APPLICATION_CLIENT_ID"
    const val REDIRECT_URI = "https://endiq-turtlelauncher.local/discord-callback"
    private const val SCOPE = "identify"

    private val client by lazy { UrlManager.createOkHttpClient() }

    data class PkcePair(val verifier: String, val challenge: String)

    fun generatePkce(): PkcePair {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        return PkcePair(verifier, challenge)
    }

    fun buildAuthorizeUrl(challenge: String): String =
        "https://discord.com/api/oauth2/authorize" +
            "?client_id=$CLIENT_ID" +
            "&redirect_uri=${java.net.URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
            "&response_type=code" +
            "&scope=${java.net.URLEncoder.encode(SCOPE, "UTF-8")}" +
            "&code_challenge=$challenge" +
            "&code_challenge_method=S256" +
            "&prompt=consent"

    /** Extracts the `code` query param once the WebView hits [REDIRECT_URI]. */
    fun extractAuthorizationCode(redirectedUrl: String): String? {
        if (!redirectedUrl.startsWith(REDIRECT_URI)) return null
        return runCatching { android.net.Uri.parse(redirectedUrl).getQueryParameter("code") }.getOrNull()
    }

    /**
     * Exchanges the code for a token (PKCE, so only [verifier] authenticates this - no
     * client secret), then fetches /users/@me and saves the linked identity. Runs
     * network I/O, so call off the main thread.
     */
    fun completeLogin(code: String, verifier: String): Boolean {
        return runCatching {
            val tokenBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", verifier)
                .build()
            val tokenRequest = Request.Builder()
                .url("https://discord.com/api/oauth2/token")
                .post(tokenBody)
                .build()
            val tokenJson = client.newCall(tokenRequest).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            } ?: return false

            val tokenObj = JsonParser.parseString(tokenJson).asJsonObject
            val accessToken = tokenObj.get("access_token")?.asString ?: return false
            val refreshToken = tokenObj.get("refresh_token")?.asString ?: ""

            val meRequest = Request.Builder()
                .url("https://discord.com/api/users/@me")
                .header("Authorization", "Bearer $accessToken")
                .build()
            val meJson = client.newCall(meRequest).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            } ?: return false
            val meObj = JsonParser.parseString(meJson).asJsonObject

            AllSettings.discordAccessToken.put(accessToken).save()
            AllSettings.discordRefreshToken.put(refreshToken).save()
            AllSettings.discordUserId.put(meObj.get("id")?.asString ?: "").save()
            AllSettings.discordUsername.put(meObj.get("username")?.asString ?: "").save()
            AllSettings.discordAvatarHash.put(meObj.get("avatar")?.asString ?: "").save()
            true
        }.onFailure { e -> Logging.e("DiscordAuthManager", "Discord login failed.", e) }.getOrDefault(false)
    }

    fun isLinked(): Boolean = AllSettings.discordUserId.getValue().isNotBlank()

    fun avatarUrl(): String? {
        val id = AllSettings.discordUserId.getValue()
        val hash = AllSettings.discordAvatarHash.getValue()
        if (id.isBlank() || hash.isBlank()) return null
        return "https://cdn.discordapp.com/avatars/$id/$hash.png?size=128"
    }

    fun unlink() {
        AllSettings.discordAccessToken.put("").save()
        AllSettings.discordRefreshToken.put("").save()
        AllSettings.discordUserId.put("").save()
        AllSettings.discordUsername.put("").save()
        AllSettings.discordAvatarHash.put("").save()
    }
}
