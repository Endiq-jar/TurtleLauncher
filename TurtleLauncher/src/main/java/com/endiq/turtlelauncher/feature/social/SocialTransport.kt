package com.endiq.turtlelauncher.feature.social

import com.google.gson.JsonObject
import com.endiq.turtlelauncher.feature.accounts.AccountsManager
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.feature.terracotta.chat.TerracottaChat
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The Turtle side of the Friends feature's transport.
 *
 * Everything Turtle-specific travels over the same P2P tunnel that Friends/LAN already
 * establishes (see [TerracottaChat]): a friend request, a DM, lobby state, presence and
 * voice signalling are all `{"t": ..., ...}` JSON lines on that socket, fanned out here.
 * That means:
 *
 *  - no server, no account, no token - it works as soon as a party is connected, and
 *  - it degrades honestly: when the tunnel is down [send] returns false and the caller
 *    queues/fails the message instead of losing it silently.
 *
 * Discord conversations do *not* come through here - they use [DiscordRestClient].
 */
object SocialTransport {
    private const val TAG = "SocialTransport"

    const val TYPE_DIRECT_MESSAGE = "dm"
    const val TYPE_LOBBY = "lobby"
    const val TYPE_PRESENCE = "presence"
    const val TYPE_VOICE = "voice"
    const val TYPE_FRIEND_REQUEST = "freq"

    private val listeners = CopyOnWriteArrayList<(JsonObject) -> Unit>()
    private val chatListener = TerracottaChat.PayloadListener { payload -> dispatch(payload) }
    @Volatile private var attached = false

    /** Idempotent; safe to call from any thread (the Friends screen does it on open). */
    @Synchronized
    fun attach() {
        if (attached) return
        attached = true
        TerracottaChat.addPayloadListener(chatListener)
        Logging.i(TAG, "Attached to the Friends/LAN transport")
    }

    fun detach() {
        if (!attached) return
        attached = false
        TerracottaChat.removePayloadListener(chatListener)
    }

    fun addListener(listener: (JsonObject) -> Unit) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: (JsonObject) -> Unit) = listeners.remove(listener)

    val isConnected: Boolean get() = TerracottaChat.isActive

    /**
     * Name this install is known by to other players. Prefers the signed-in Minecraft name
     * (that is what friends see in a party), then the local Turtle display name.
     */
    fun selfName(): String {
        val minecraft = runCatching { AccountsManager.currentAccount?.username }.getOrNull()
        if (!minecraft.isNullOrBlank()) return minecraft
        val identity = SocialIdentityStore.current()
        return identity.effectiveName.ifBlank { "Turtle player" }
    }

    /** Sends [payload] with the sender stamped in. False when no tunnel is connected. */
    fun send(payload: JsonObject): Boolean {
        val body = payload.deepCopy()
        if (!body.has("sender")) body.addProperty("sender", selfName())
        body.addProperty("sentAt", System.currentTimeMillis())
        val sent = runCatching { TerracottaChat.sendPayload(body) }.getOrDefault(false)
        if (!sent) Logging.d(TAG, "Tunnel not connected - ${body.get("t")} not sent")
        return sent
    }

    private fun dispatch(payload: JsonObject) {
        for (listener in listeners) {
            runCatching { listener(payload) }
                .onFailure { e -> Logging.w(TAG, "Payload listener failed", e) }
        }
    }
}
