package com.endiq.turtlelauncher.feature.social

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.utils.path.UrlManager
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant

/**
 * Discord's user-facing REST API, used only through the launcher's own OAuth2 session.
 *
 * This is the "works today" half of the Friends feature: with a linked account the launcher
 * can list relationships (friends, blocked users, pending requests), open DM channels, read
 * and send messages, delete its own messages, and read the guild/channel list for
 * Communities. It is *not* a gateway client: Discord does not expose presence over REST, so
 * Discord friends show as presence-unknown until the Social SDK's gateway connection exists
 * (see [VoiceCapabilities.sdkVoiceAvailable]). Everything degrades to an explicit
 * [DiscordApiException] instead of a silent empty list.
 *
 * All calls are synchronous and must run off the main thread.
 */
internal object DiscordRestClient {
    private const val TAG = "DiscordRest"
    private val client by lazy { UrlManager.createOkHttpClient() }
    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** HTTP failure with the status code preserved so callers can special-case 403/429. */
    class DiscordApiException(
        val statusCode: Int,
        message: String,
    ) : Exception(message) {
        val isForbidden: Boolean get() = statusCode == 403
        val isRateLimited: Boolean get() = statusCode == 429
        val isUnauthorized: Boolean get() = statusCode == 401
    }

    data class DiscordRelationship(
        val userId: String,
        val type: Int,
        val username: String,
        val displayName: String?,
        val avatarUrl: String?,
    ) {
        val isFriend: Boolean get() = type == TYPE_FRIEND
        val isBlocked: Boolean get() = type == TYPE_BLOCKED
        val isIncomingRequest: Boolean get() = type == TYPE_INCOMING_REQUEST
        val isOutgoingRequest: Boolean get() = type == TYPE_OUTGOING_REQUEST

        companion object {
            const val TYPE_FRIEND = 1
            const val TYPE_BLOCKED = 2
            const val TYPE_INCOMING_REQUEST = 3
            const val TYPE_OUTGOING_REQUEST = 4
        }
    }

    data class DiscordGuild(val id: String, val name: String)

    data class DiscordChannel(
        val id: String,
        val name: String,
        val guildId: String?,
        val type: Int,
    ) {
        val isText: Boolean get() = type == 0 || type == 5
    }

    data class DiscordMessage(
        val id: String,
        val channelId: String,
        val authorId: String?,
        val authorName: String,
        val content: String,
        val timestampMs: Long,
        val attachments: List<SocialAttachment>,
    )

    // ── Identity ───────────────────────────────────────────────────────────────────

    fun me(token: String): DiscordProfile {
        val user = get(token, "/users/@me")
        val id = user.string("id") ?: throw DiscordApiException(200, "Discord returned no user id.")
        return DiscordProfile(
            id = id,
            username = user.string("username").orEmpty(),
            globalName = user.string("global_name"),
            avatarUrl = avatarUrl(id, user.string("avatar")),
        )
    }

    // ── Relationships ──────────────────────────────────────────────────────────────

    fun relationships(token: String): List<DiscordRelationship> {
        val array = getArray(token, "/users/@me/relationships")
        return array.mapNotNull { element ->
            val relation = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val user = relation.obj("user") ?: return@mapNotNull null
            val id = user.string("id") ?: return@mapNotNull null
            DiscordRelationship(
                userId = id,
                type = relation.int("type") ?: 0,
                username = user.string("username").orEmpty(),
                displayName = user.string("global_name"),
                avatarUrl = avatarUrl(id, user.string("avatar")),
            )
        }
    }

    /** Accepts an incoming request (or sends one) - Discord's PUT with `type: 1`. */
    fun acceptRelationship(token: String, userId: String) {
        put(token, "/users/@me/relationships/$userId", """{"type":1}""")
    }

    /** Removes a friend, declines an incoming request, or unblocks a user. */
    fun deleteRelationship(token: String, userId: String) {
        delete(token, "/users/@me/relationships/$userId")
    }

    fun blockUser(token: String, userId: String) {
        put(token, "/users/@me/relationships/$userId", """{"type":2}""")
    }

    // ── Guilds / channels (Communities) ────────────────────────────────────────────

    fun guilds(token: String): List<DiscordGuild> =
        getArray(token, "/users/@me/guilds").mapNotNull { element ->
            val guild = element.asJsonObjectOrNull() ?: return@mapNotNull null
            DiscordGuild(
                id = guild.string("id") ?: return@mapNotNull null,
                name = guild.string("name").orEmpty(),
            )
        }

    fun guildChannels(token: String, guildId: String): List<DiscordChannel> =
        getArray(token, "/guilds/$guildId/channels").mapNotNull { element ->
            val channel = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val id = channel.string("id") ?: return@mapNotNull null
            DiscordChannel(
                id = id,
                name = channel.string("name").orEmpty(),
                guildId = channel.string("guild_id") ?: guildId,
                type = channel.int("type") ?: -1,
            )
        }.filter { it.isText }

    fun channel(token: String, channelId: String): DiscordChannel? {
        val channel = get(token, "/channels/$channelId")
        val id = channel.string("id") ?: return null
        return DiscordChannel(
            id = id,
            name = channel.string("name").orEmpty(),
            guildId = channel.string("guild_id"),
            type = channel.int("type") ?: 0,
        )
    }

    // ── Direct messages ────────────────────────────────────────────────────────────

    /** Opens (or returns) the DM channel with [userId]. */
    fun openDirectMessage(token: String, userId: String): String {
        val body = """{"recipient_id":"$userId"}""".toRequestBody(JSON)
        val response = post(token, "/users/@me/channels", body)
        return response.string("id")
            ?: throw DiscordApiException(200, "Discord did not return a DM channel.")
    }

    fun messages(token: String, channelId: String, limit: Int = 50): List<DiscordMessage> {
        val url = apiUrl("/channels/$channelId/messages")
            .newBuilder()
            .addQueryParameter("limit", limit.coerceIn(1, 100).toString())
            .build()
        val response = execute(Request.Builder().url(url).get(), token)
        val array = JsonParser.parseString(response).takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
        return array.mapNotNull { element ->
            val message = element.asJsonObjectOrNull() ?: return@mapNotNull null
            messageOf(message, channelId)
        }
    }

    fun sendMessage(token: String, channelId: String, content: String): DiscordMessage {
        val payload = JsonObject().apply { addProperty("content", content) }
        val body = payload.toString().toRequestBody(JSON)
        val response = post(token, "/channels/$channelId/messages", body)
        return messageOf(response, channelId)
            ?: throw DiscordApiException(200, "Discord did not return the sent message.")
    }

    /** Only the author's own messages can be deleted with a user token. */
    fun deleteMessage(token: String, channelId: String, messageId: String) {
        delete(token, "/channels/$channelId/messages/$messageId")
    }

    // ── Message mapping ────────────────────────────────────────────────────────────

    private fun messageOf(json: JsonObject, channelId: String): DiscordMessage? {
        val id = json.string("id") ?: return null
        val author = json.obj("author")
        val attachments = json.getAsJsonArray("attachments")?.mapNotNull { element ->
            val attachment = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val url = attachment.string("url") ?: return@mapNotNull null
            SocialAttachment(
                url = url,
                fileName = attachment.string("filename"),
                contentType = attachment.string("content_type"),
                sizeBytes = attachment.long("size") ?: 0L,
            )
        }.orEmpty()

        return DiscordMessage(
            id = id,
            channelId = json.string("channel_id") ?: channelId,
            authorId = author?.string("id"),
            authorName = author?.string("global_name")
                ?: author?.string("username")
                ?: "Discord user",
            content = json.string("content").orEmpty(),
            timestampMs = timestampMs(json.string("timestamp")),
            attachments = attachments,
        )
    }

    private fun timestampMs(value: String?): Long {
        if (value.isNullOrBlank()) return System.currentTimeMillis()
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
    }

    fun avatarUrl(userId: String, avatarHash: String?): String? {
        if (avatarHash.isNullOrBlank()) return null
        val extension = if (avatarHash.startsWith("a_")) "gif" else "png"
        return "https://cdn.discordapp.com/avatars/$userId/$avatarHash.$extension?size=128"
    }

    // ── HTTP plumbing ──────────────────────────────────────────────────────────────

    private fun apiUrl(path: String): HttpUrl = (DiscordConfig.API_BASE + path).toHttpUrl()

    private fun execute(builder: okhttp3.Request.Builder, token: String): String {
        val request = builder
            .header("Authorization", "Bearer $token")
            .header("User-Agent", DiscordConfig.USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    JsonParser.parseString(body).asJsonObject.let { json ->
                        json.string("message") ?: json.string("error_description")
                    }
                }.getOrNull()
                Logging.w(TAG, "HTTP ${response.code} for ${request.url.encodedPath}: $message")
                throw DiscordApiException(
                    response.code,
                    when (response.code) {
                        401 -> "Discord session expired - reconnect your account."
                        403 -> message?.takeIf { it.isNotBlank() }
                            ?: "Discord refused the request (missing scope or permission)."
                        429 -> "Discord is rate limiting the launcher - try again in a moment."
                        else -> message?.takeIf { it.isNotBlank() }
                            ?: "Discord request failed (HTTP ${response.code})."
                    },
                )
            }
            return body
        }
    }

    private fun get(token: String, path: String): JsonObject {
        val body = execute(Request.Builder().url(apiUrl(path)).get(), token)
        return JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
            ?: throw DiscordApiException(200, "Discord returned an unexpected response.")
    }

    private fun getArray(token: String, path: String): JsonArray {
        val body = execute(Request.Builder().url(apiUrl(path)).get(), token)
        return JsonParser.parseString(body).takeIf { it.isJsonArray }?.asJsonArray ?: JsonArray()
    }

    private fun post(token: String, path: String, body: okhttp3.RequestBody): JsonObject {
        val response = execute(Request.Builder().url(apiUrl(path)).post(body), token)
        return JsonParser.parseString(response).takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
    }

    private fun put(token: String, path: String, json: String) {
        execute(
            Request.Builder().url(apiUrl(path)).put(json.toRequestBody(JSON)),
            token,
        )
    }

    private fun delete(token: String, path: String) {
        execute(Request.Builder().url(apiUrl(path)).delete(), token)
    }
}

// ── JSON helpers (Discord sends explicit nulls everywhere) ─────────────────────────

private fun JsonElement.asJsonObjectOrNull(): JsonObject? =
    takeIf { it.isJsonObject }?.asJsonObject

private fun JsonObject.obj(name: String): JsonObject? =
    get(name)?.takeIf { !it.isJsonNull }?.asJsonObject

private fun JsonObject.string(name: String): String? =
    runCatching { get(name)?.takeIf { !it.isJsonNull }?.asString }.getOrNull()

private fun JsonObject.int(name: String): Int? =
    runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull()

private fun JsonObject.long(name: String): Long? =
    runCatching { get(name)?.takeIf { !it.isJsonNull }?.asLong }.getOrNull()
