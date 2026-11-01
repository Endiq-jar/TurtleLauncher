package com.endiq.turtlelauncher.feature.social

import com.endiq.turtlelauncher.feature.log.Logging
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A Discord text channel linked into the launcher ("Communities").
 *
 * The point is one shared community chat while playing: the channel is shown in the Friends
 * screen, messages can be read and posted there, and the conversation stays the same one the
 * Discord client sees - no mirroring bot, no duplicate thread. Reading/writing uses the
 * linked account's OAuth session ([DiscordRestClient]); when the account is unlinked the
 * channel is unlinked with it, because the launcher has no other way to read it.
 *
 * All methods here do network I/O and must be called from a background thread.
 */
object LinkedChannelManager {
    private const val TAG = "LinkedChannelManager"
    private const val FILE_NAME = "linked_channel.json"
    private const val MAX_GUILDS_LISTED = 10
    private const val MAX_CHANNELS_LISTED = 120

    internal data class LinkedChannelFile(
        val channel: LinkedChannel? = null,
    )

    /** A guild plus its text channels, for the "link a channel" picker. */
    data class GuildChannels(
        val guildId: String,
        val guildName: String,
        val channels: List<DiscordRestClient.DiscordChannel>,
    )

    private val listeners = CopyOnWriteArrayList<(LinkedChannel?) -> Unit>()

    @Volatile
    private var channel: LinkedChannel? = null

    @Volatile
    private var loaded = false

    @Synchronized
    fun attach() {
        if (loaded) return
        loaded = true
        channel = SocialStorage.read(FILE_NAME, LinkedChannelFile::class.java)?.channel
    }

    fun addListener(listener: (LinkedChannel?) -> Unit) {
        attach()
        if (!listeners.contains(listener)) listeners.add(listener)
        listener(channel)
    }

    fun removeListener(listener: (LinkedChannel?) -> Unit) = listeners.remove(listener)

    fun current(): LinkedChannel? {
        attach()
        return channel
    }

    /** Every guild the linked account is in, with its text channels. Needs the `guilds` scope. */
    fun availableGuilds(): Result<List<GuildChannels>> = runCatching {
        val token = DiscordOAuthClient.accessToken()
            ?: throw IllegalStateException("Link your Discord account first.")
        val guilds = DiscordRestClient.guilds(token).take(MAX_GUILDS_LISTED)
        var budget = MAX_CHANNELS_LISTED
        guilds.mapNotNull { guild ->
            if (budget <= 0) return@mapNotNull null
            val channels = runCatching { DiscordRestClient.guildChannels(token, guild.id) }
                .getOrElse { emptyList() }
                .take(budget)
            budget -= channels.size
            GuildChannels(guild.id, guild.name, channels)
        }
    }

    /** Links [channelId] after verifying it is a text channel the account can see. */
    fun link(channelId: String): Result<LinkedChannel> = runCatching {
        val token = DiscordOAuthClient.accessToken()
            ?: throw IllegalStateException("Link your Discord account first.")
        val discordChannel = DiscordRestClient.channel(token, channelId)
            ?: throw IllegalStateException("That channel no longer exists.")
        if (!discordChannel.isText) throw IllegalStateException("Only text channels can be linked.")

        val guildId = discordChannel.guildId.orEmpty()
        val guildName = runCatching {
            DiscordRestClient.guilds(token).firstOrNull { it.id == guildId || guildId.isBlank() }?.name
        }.getOrNull().orEmpty()

        val linked = LinkedChannel(
            channelId = discordChannel.id,
            channelName = discordChannel.name.ifBlank { "channel" },
            guildId = guildId,
            guildName = guildName,
            linkedAtMs = System.currentTimeMillis(),
        )
        channel = linked
        SocialStorage.write(FILE_NAME, LinkedChannelFile(linked))
        publish()
        Logging.i(TAG, "Linked #${linked.channelName} in ${linked.guildName}")
        linked
    }

    fun unlink() {
        channel = null
        SocialStorage.write(FILE_NAME, LinkedChannelFile(null))
        publish()
    }

    /** Recent messages from the linked channel, oldest first. */
    fun messages(limit: Int = 50): Result<List<SocialMessage>> = runCatching {
        val linked = current() ?: throw IllegalStateException("No channel is linked.")
        val token = DiscordOAuthClient.accessToken()
            ?: throw IllegalStateException("Link your Discord account first.")
        DiscordRestClient.messages(token, linked.channelId, limit)
            .map { it.toSocialMessage(conversationId = conversationId(linked)) }
            .sortedBy { it.sentAtMs }
    }

    fun send(text: String): Result<SocialMessage> = runCatching {
        val linked = current() ?: throw IllegalStateException("No channel is linked.")
        val body = text.trim()
        if (body.isEmpty()) throw IllegalArgumentException("Message is empty.")
        val token = DiscordOAuthClient.accessToken()
            ?: throw IllegalStateException("Link your Discord account first.")
        DiscordRestClient.sendMessage(token, linked.channelId, body)
            .toSocialMessage(conversationId = conversationId(linked), fromSelf = true)
    }

    fun deleteMessage(messageId: String): Result<Unit> = runCatching {
        val linked = current() ?: throw IllegalStateException("No channel is linked.")
        val token = DiscordOAuthClient.accessToken()
            ?: throw IllegalStateException("Link your Discord account first.")
        DiscordRestClient.deleteMessage(token, linked.channelId, messageId)
    }

    /** Conversation id used by the Messages tab for the linked channel. */
    fun conversationId(linked: LinkedChannel): String = "channel:${linked.channelId}"

    private fun publish() {
        val snapshot = channel
        for (listener in listeners) runCatching { listener(snapshot) }
    }
}

/** Shared mapping from a Discord message to the launcher's message model. */
internal fun DiscordRestClient.DiscordMessage.toSocialMessage(
    conversationId: String,
    fromSelf: Boolean = false,
): SocialMessage = SocialMessage(
    id = id,
    conversationId = conversationId,
    authorName = authorName,
    authorId = authorId,
    fromSelf = fromSelf,
    text = content,
    sentAtMs = timestampMs,
    deletable = fromSelf,
    attachments = attachments,
)
