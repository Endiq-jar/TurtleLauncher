package com.endiq.turtlelauncher.feature.social

import android.content.Context
import com.google.gson.JsonObject
import com.endiq.turtlelauncher.context.ContextExecutor
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.task.TaskExecutors
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * The Friends feature's single source of truth.
 *
 * Everything the UI shows - the unified friend list, pending requests, conversations and
 * their unread counts, parties, voice state, the linked community channel - is assembled here
 * from three backends and published as an immutable [SocialSnapshot]:
 *
 *   | data                    | backend                                             |
 *   |-------------------------|-----------------------------------------------------|
 *   | Discord friends/requests| [DiscordRestClient] with the linked OAuth session    |
 *   | Turtle friends/requests | JSON file + the Friends/LAN tunnel ([SocialTransport])|
 *   | messages                | Discord REST, or the tunnel for Turtle DMs           |
 *   | parties                 | [LobbyManager]                                       |
 *   | voice state             | [VoiceManager]                                       |
 *   | community channel       | [LinkedChannelManager]                               |
 *
 * All mutation happens on one daemon thread so the JSON stores are never written
 * concurrently; listeners are always called on the main thread. Nothing here blocks the UI.
 */
object SocialRepository {
    private const val TAG = "SocialRepository"
    private const val FRIENDS_FILE = "game_social.json"
    private const val MESSAGES_FILE = "messages.json"
    private const val MAX_MESSAGES_PER_THREAD = 250
    private const val PRESENCE_INTERVAL_SECONDS = 25L

    /** On-disk shape of the Turtle side of the graph. */
    internal data class GameSocialFile(
        val friends: MutableList<SocialFriend> = mutableListOf(),
        val requests: MutableList<FriendRequest> = mutableListOf(),
    )

    /** On-disk shape of message history. */
    internal data class MessageFile(
        val threads: MutableMap<String, MutableList<SocialMessage>> = mutableMapOf(),
        val conversations: MutableList<SocialConversation> = mutableListOf(),
    )

    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Turtle-Social").apply { isDaemon = true }
    }
    private val presenceScheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "Turtle-SocialPresence").apply { isDaemon = true }
        }
    private val listeners = CopyOnWriteArrayList<(SocialSnapshot) -> Unit>()
    private val lock = Any()

    @Volatile private var snapshot: SocialSnapshot = SocialSnapshot()
    @Volatile private var discordFriends: List<SocialFriend> = emptyList()
    @Volatile private var discordRequests: List<FriendRequest> = emptyList()
    @Volatile private var statusMessage: String? = null
    @Volatile private var busy: Boolean = false
    @Volatile private var openConversationId: String? = null
    @Volatile private var attached: Boolean = false

    private var game: GameSocialFile = GameSocialFile()
    private var conversations: MutableList<SocialConversation> = mutableListOf()
    private var threads: MutableMap<String, MutableList<SocialMessage>> = mutableMapOf()
    private var channelMessages: MutableList<SocialMessage> = mutableListOf()

    private val transportListener: (JsonObject) -> Unit = { payload -> onPayload(payload) }

    // ── Lifecycle ──────────────────────────────────────────────────────────────────

    /** Loads everything and starts listening. Safe to call from any thread, repeatedly. */
    @Synchronized
    fun attach() {
        if (attached) return
        attached = true

        SocialTransport.attach()
        LobbyManager.attach()
        VoiceManager.attach()
        LinkedChannelManager.attach()
        SocialTransport.addListener(transportListener)

        LobbyManager.addListener { lobbies ->
            // Party membership is also the voice roster; keep the two in step, then republish.
            lobbies.firstOrNull()?.let { lobby -> VoiceManager.updateMembers(lobby.members.map { it.name }) }
            publishLater()
        }
        VoiceManager.addListener { publishLater() }
        LinkedChannelManager.addListener { publishLater() }
        SocialIdentityStore.addListener { publishLater() }

        worker.execute {
            loadFromDisk()
            publish()
        }

        presenceScheduler.scheduleWithFixedDelay(
            { runCatching { announcePresence() } },
            PRESENCE_INTERVAL_SECONDS,
            PRESENCE_INTERVAL_SECONDS,
            TimeUnit.SECONDS,
        )
    }

    fun addListener(listener: (SocialSnapshot) -> Unit) {
        attach()
        if (!listeners.contains(listener)) listeners.add(listener)
        listener(snapshot)
    }

    fun removeListener(listener: (SocialSnapshot) -> Unit) = listeners.remove(listener)

    fun current(): SocialSnapshot {
        attach()
        return snapshot
    }

    /** Re-reads Discord relationships (when linked) and republishes. */
    fun refresh() = enqueue {
        busy = true
        statusMessage = null
        publish()

        val identity = SocialIdentityStore.current()
        if (identity.hasDiscordLink) {
            val token = DiscordOAuthClient.accessToken()
            if (token.isNullOrBlank()) {
                statusMessage = "Discord session expired - reconnect your account."
                discordFriends = emptyList()
            } else {
                loadDiscord(token)
            }
        } else {
            discordFriends = emptyList()
        }

        LobbyManager.refreshSession()
        busy = false
        publish()
    }

    private fun loadDiscord(token: String) {
        val relationshipResult = runCatching { DiscordRestClient.relationships(token) }
        relationshipResult.onFailure { error ->
            val message = when (error) {
                is DiscordRestClient.DiscordApiException ->
                    if (error.isForbidden) {
                        "Discord friends need the relationships.read scope. Add it to the app in the " +
                            "Discord Developer Portal, then link again."
                    } else {
                        error.message
                    }
                else -> error.message
            }
            statusMessage = message
            Logging.w(TAG, "Discord relationships unavailable", error)
        }

        val relationships = relationshipResult.getOrElse { emptyList() }
        if (relationships.isNotEmpty()) statusMessage = null

        val friends = mutableListOf<SocialFriend>()
        val requests = mutableListOf<FriendRequest>()
        for (relationship in relationships) {
            val base = SocialFriend(
                key = "discord:${relationship.userId}",
                source = SocialSource.DISCORD,
                displayName = relationship.displayName ?: relationship.username,
                subtitle = "@${relationship.username}",
                avatarUrl = relationship.avatarUrl,
                discordUserId = relationship.userId,
                presence = PresenceStatus.UNKNOWN,
                presenceLabel = "Discord presence needs the Social SDK connection",
                blocked = relationship.isBlocked,
            )
            when {
                relationship.isFriend || relationship.isBlocked -> friends.add(base)
                relationship.isIncomingRequest -> requests.add(
                    FriendRequest(
                        id = relationship.userId,
                        source = SocialSource.DISCORD,
                        displayName = base.displayName,
                        detail = "Discord friend request",
                        direction = FriendRequestDirection.INCOMING,
                        discordUserId = relationship.userId,
                    )
                )
                relationship.isOutgoingRequest -> requests.add(
                    FriendRequest(
                        id = relationship.userId,
                        source = SocialSource.DISCORD,
                        displayName = base.displayName,
                        detail = "Waiting for them to accept",
                        direction = FriendRequestDirection.OUTGOING,
                        discordUserId = relationship.userId,
                    )
                )
            }
        }
        discordFriends = friends
        discordRequests = requests
        Logging.i(TAG, "Loaded ${friends.size} Discord friend(s) and ${requests.size} request(s)")
    }

    private fun loadFromDisk() {
        synchronized(lock) {
            game = SocialStorage.read(FRIENDS_FILE, GameSocialFile::class.java) ?: GameSocialFile()
            val messages = SocialStorage.read(MESSAGES_FILE, MessageFile::class.java)
            if (messages != null) {
                conversations = messages.conversations
                threads = messages.threads
            }
        }
    }

    // ── Identity ───────────────────────────────────────────────────────────────────

    fun renameIdentity(rawName: String) {
        SocialIdentityStore.rename(rawName)
        publishLater()
    }

    fun unlinkDiscord() {
        DiscordOAuthClient.unlink()
        discordFriends = emptyList()
        discordRequests = emptyList()
        statusMessage = "Discord disconnected. Turtle friends are unchanged."
        enqueue { publish() }
    }

    // ── Friends ────────────────────────────────────────────────────────────────────

    /** Adds a Turtle friend by name (usually the Minecraft name you played with). */
    fun addGameFriend(rawName: String) = enqueue {
        val name = rawName.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) return@enqueue
        if (name.equals(SocialTransport.selfName(), ignoreCase = true)) {
            statusMessage = "That's you."
            publish()
            return@enqueue
        }
        val existing = game.friends.firstOrNull {
            it.displayName.equals(name, ignoreCase = true) ||
                it.minecraftName?.equals(name, ignoreCase = true) == true
        }
        if (existing != null) {
            statusMessage = "$name is already in your friends list."
            publish()
            return@enqueue
        }

        game.friends.add(
            SocialFriend(
                key = "turtle:${UUID.randomUUID()}",
                source = SocialSource.TURTLE,
                displayName = name,
                minecraftName = name,
                presence = PresenceStatus.UNKNOWN,
                presenceLabel = "Not seen in a party yet",
            )
        )
        SocialTransport.send(
            JsonObject().apply {
                addProperty("t", SocialTransport.TYPE_FRIEND_REQUEST)
                addProperty("action", "request")
                addProperty("name", name)
            }
        )
        game.requests.add(
            FriendRequest(
                id = UUID.randomUUID().toString(),
                source = SocialSource.TURTLE,
                displayName = name,
                detail = "Friend request sent",
                direction = FriendRequestDirection.OUTGOING,
                createdAtMs = System.currentTimeMillis(),
            )
        )
        statusMessage = "Friend request sent to $name."
        persist()
        publish()
    }

    fun removeFriend(key: String) = enqueue {
        val friend = allTurtleFriends().firstOrNull { it.key == key }
        game.friends.removeAll { it.key == key }
        // A Discord friend is removed through Discord, so relationships stay in sync everywhere.
        discordFriends.firstOrNull { it.key == key }?.discordUserId?.let { userId ->
            DiscordOAuthClient.accessToken()?.let { token ->
                runCatching { DiscordRestClient.deleteRelationship(token, userId) }
                    .onFailure { e -> statusMessage = "Discord: ${e.message}" }
            }
            discordFriends = discordFriends.filterNot { it.key == key }
        }
        conversations.removeAll { it.peerKey == key }
        if (friend != null) statusMessage = "Removed ${friend.displayName}."
        persist()
        publish()
    }

    fun setBlocked(key: String, blocked: Boolean) = enqueue {
        game = game.copy(
            friends = game.friends.map {
                if (it.key == key) it.copy(blocked = blocked, ignored = if (blocked) false else it.ignored) else it
            }.toMutableList()
        )
        val userId = discordFriends.firstOrNull { it.key == key }?.discordUserId
        if (userId != null) {
            DiscordOAuthClient.accessToken()?.let { token ->
                runCatching {
                    if (blocked) DiscordRestClient.blockUser(token, userId)
                    else DiscordRestClient.deleteRelationship(token, userId)
                }.onFailure { e -> statusMessage = "Discord: ${e.message}" }
            }
            discordFriends = discordFriends.map { if (it.key == key) it.copy(blocked = blocked) else it }
        }
        statusMessage = if (blocked) "Blocked - they can't message you in Turtle." else "Unblocked."
        persist()
        publish()
    }

    fun setIgnored(key: String, ignored: Boolean) = enqueue {
        game = game.copy(
            friends = game.friends.map {
                if (it.key == key) it.copy(ignored = ignored, blocked = if (ignored) false else it.blocked) else it
            }.toMutableList()
        )
        discordFriends = discordFriends.map { if (it.key == key) it.copy(ignored = ignored) else it }
        statusMessage = if (ignored) "Ignored - messages are hidden and notifications are off." else "No longer ignored."
        persist()
        publish()
    }

    fun acceptRequest(request: FriendRequest) = enqueue {
        when (request.source) {
            SocialSource.DISCORD -> {
                val token = DiscordOAuthClient.accessToken()
                if (token == null) {
                    statusMessage = "Reconnect your Discord account to accept requests."
                } else {
                    runCatching {
                        DiscordRestClient.acceptRelationship(token, request.discordUserId ?: request.id)
                    }.onSuccess {
                        statusMessage = "Accepted ${request.displayName} on Discord."
                        discordRequests = discordRequests.filterNot { it.id == request.id }
                    }.onFailure { e -> statusMessage = "Discord: ${e.message}" }
                }
            }
            SocialSource.TURTLE -> {
                game.requests.removeAll { it.id == request.id }
                if (game.friends.none { it.displayName.equals(request.displayName, ignoreCase = true) }) {
                    game.friends.add(
                        SocialFriend(
                            key = "turtle:${UUID.randomUUID()}",
                            source = SocialSource.TURTLE,
                            displayName = request.displayName,
                            minecraftName = request.displayName,
                            presence = PresenceStatus.ONLINE,
                            presenceLabel = "Accepted just now",
                            lastSeenMs = System.currentTimeMillis(),
                        )
                    )
                }
                SocialTransport.send(
                    JsonObject().apply {
                        addProperty("t", SocialTransport.TYPE_FRIEND_REQUEST)
                        addProperty("action", "accept")
                        addProperty("name", request.displayName)
                    }
                )
                statusMessage = "You and ${request.displayName} are friends now."
            }
        }
        persist()
        publish()
    }

    fun rejectRequest(request: FriendRequest) = enqueue {
        when (request.source) {
            SocialSource.DISCORD -> {
                val token = DiscordOAuthClient.accessToken()
                if (token != null) {
                    runCatching { DiscordRestClient.deleteRelationship(token, request.discordUserId ?: request.id) }
                        .onFailure { e -> statusMessage = "Discord: ${e.message}" }
                }
                discordRequests = discordRequests.filterNot { it.id == request.id }
            }
            SocialSource.TURTLE -> {
                game.requests.removeAll { it.id == request.id }
                SocialTransport.send(
                    JsonObject().apply {
                        addProperty("t", SocialTransport.TYPE_FRIEND_REQUEST)
                        addProperty("action", "reject")
                        addProperty("name", request.displayName)
                    }
                )
            }
        }
        statusMessage = "Request dismissed."
        persist()
        publish()
    }

    // ── Conversations ──────────────────────────────────────────────────────────────

    fun messages(conversationId: String): List<SocialMessage> = synchronized(lock) {
        threads[conversationId]?.toList().orEmpty()
    }

    fun channelMessages(): List<SocialMessage> = synchronized(lock) { channelMessages.toList() }

    /**
     * Resolves (and creates if necessary) the conversation with [friendKey], then hands it to
     * [onReady] on the main thread. Used by the Friends screen's "Message" action, which must
     * not touch the stores from the UI thread.
     */
    fun openConversationForFriend(friendKey: String, onReady: (SocialConversation?) -> Unit) = enqueue {
        val friend = allFriends().firstOrNull { it.key == friendKey }
        val conversation = friend?.let { ensureDirectConversation(it) }
        persist()
        publish()
        TaskExecutors.runInUIThread { onReady(conversation) }
    }

    fun openLobbyConversation(lobbyId: String, onReady: (SocialConversation?) -> Unit) = enqueue {
        val lobby = LobbyManager.current().firstOrNull { it.id == lobbyId }
        if (lobby != null) ensureLobbyConversation(lobby)
        val conversation = conversations.firstOrNull { it.kind == ConversationKind.LOBBY && it.platformChannelId == lobbyId }
        persist()
        publish()
        TaskExecutors.runInUIThread { onReady(conversation) }
    }

    fun openChannelConversation(onReady: (SocialConversation?) -> Unit) = enqueue {
        val linked = LinkedChannelManager.current()
        if (linked != null) ensureChannelConversation(linked)
        val conversation = linked?.let { conversations.firstOrNull { c -> c.id == LinkedChannelManager.conversationId(it) } }
        persist()
        publish()
        TaskExecutors.runInUIThread { onReady(conversation) }
    }

    /**
     * Loads history for [conversationId]:
     *  - Discord DMs and the linked channel: recent messages from Discord (so the same
     *    conversation is visible in both clients),
     *  - Turtle DMs and lobby chat: whatever the local store has (the P2P tunnel has no
     *    server-side history - that is deliberate, nothing is kept anywhere else).
     * Locally queued/failed messages are preserved rather than overwritten by the remote list.
     */
    fun loadHistory(conversationId: String, onDone: ((List<SocialMessage>) -> Unit)? = null) = enqueue {
        val conversation = conversations.firstOrNull { it.id == conversationId }
        if (conversation == null) {
            statusMessage = "That conversation no longer exists."
            publish()
            onDone?.let { callback -> TaskExecutors.runInUIThread { callback(emptyList()) } }
            return@enqueue
        }

        val identity = SocialIdentityStore.current()
        when {
            conversation.kind == ConversationKind.CHANNEL -> {
                runCatching { LinkedChannelManager.messages() }
                    .onSuccess { remote ->
                        mergeRemoteHistory(
                            conversationId,
                            remote.map { it.copy(conversationId = conversationId) },
                        )
                    }
                    .onFailure { error -> statusMessage = error.message ?: "Could not load the channel." }
            }
            conversation.source == SocialSource.DISCORD -> {
                val token = DiscordOAuthClient.accessToken()
                val userId = discordFriends.firstOrNull { it.key == conversation.peerKey }?.discordUserId
                if (token != null && userId != null) {
                    runCatching {
                        val channelId = conversation.platformChannelId ?: run {
                            val opened = DiscordRestClient.openDirectMessage(token, userId)
                            updateConversation(conversationId) { existing -> existing.copy(platformChannelId = opened) }
                            opened
                        }
                        DiscordRestClient.messages(token, channelId, 50).map { message ->
                            message.toSocialMessage(
                                conversationId,
                                fromSelf = message.authorId != null && message.authorId == identity.discordUserId,
                            )
                        }
                    }.onSuccess { remote -> mergeRemoteHistory(conversationId, remote) }
                        .onFailure { error -> statusMessage = error.message ?: "Could not load Discord messages." }
                }
            }
            else -> Unit
        }

        persist()
        publish()
        onDone?.let { callback -> TaskExecutors.runInUIThread { callback(messages(conversationId)) } }
    }

    private fun mergeRemoteHistory(conversationId: String, remote: List<SocialMessage>) {
        synchronized(lock) {
            val local = threads[conversationId].orEmpty()
            val remoteIds = remote.map { it.id }.toSet()
            val queued = local.filter { it.id !in remoteIds && (it.failed || it.pending) }
            val merged = (remote + queued).sortedBy { it.sentAtMs }.toMutableList()
            while (merged.size > MAX_MESSAGES_PER_THREAD) merged.removeAt(0)
            threads[conversationId] = merged
        }
        refreshConversationPreview(conversationId)
    }

    fun markRead(conversationId: String) = enqueue {
        openConversationId = conversationId
        var changed = false
        conversations = conversations.map { conversation ->
            if (conversation.id == conversationId && conversation.unreadCount != 0) {
                changed = true
                conversation.copy(unreadCount = 0)
            } else {
                conversation
            }
        }.toMutableList()
        if (changed) {
            persist()
            publish()
        }
        appContext()?.let { context -> SocialNotifications.clear(context, conversationId) }
    }

    fun closeConversation(conversationId: String) {
        if (openConversationId == conversationId) openConversationId = null
    }

    fun sendMessage(conversationId: String, rawText: String, onComplete: ((SocialMessage) -> Unit)? = null) {
        val body = rawText.trim()
        if (body.isEmpty()) return
        enqueue {
            val conversation = conversations.firstOrNull { it.id == conversationId }
            if (conversation == null) {
                statusMessage = "That conversation no longer exists."
                publish()
                return@enqueue
            }
            val result = when {
                conversation.kind == ConversationKind.CHANNEL -> sendToChannel(body)
                conversation.source == SocialSource.DISCORD ->
                    sendDiscordMessage(conversation, body)
                else -> sendTurtleMessage(conversation, body)
            }
            if (result != null) {
                appendMessage(result)
                onComplete?.let { callback -> TaskExecutors.runInUIThread { callback(result) } }
            }
            publish()
        }
    }

    private fun sendDiscordMessage(conversation: SocialConversation, body: String): SocialMessage {
        val token = DiscordOAuthClient.accessToken()
            ?: return failedMessage(conversation, body, "Reconnect your Discord account to send this.")

        val userId = discordFriends.firstOrNull { it.key == conversation.peerKey }?.discordUserId
            ?: return failedMessage(conversation, body, "That Discord friend is no longer in your list.")

        return runCatching {
            var channelId = conversation.platformChannelId
            if (channelId.isNullOrBlank()) {
                channelId = DiscordRestClient.openDirectMessage(token, userId)
                updateConversation(conversation.id) { it.copy(platformChannelId = channelId) }
            }
            DiscordRestClient.sendMessage(token, channelId, body)
                .toSocialMessage(conversation.id, fromSelf = true)
        }.getOrElse { error ->
            failedMessage(conversation, body, error.message ?: "Discord rejected the message.")
        }
    }

    private fun sendTurtleMessage(conversation: SocialConversation, body: String): SocialMessage {
        val friend = game.friends.firstOrNull { it.key == conversation.peerKey }
        val target = friend?.minecraftName?.takeIf { it.isNotBlank() }
            ?: friend?.displayName
            ?: conversation.title

        val messageId = UUID.randomUUID().toString()
        val delivered = SocialTransport.send(
            JsonObject().apply {
                addProperty("t", SocialTransport.TYPE_DIRECT_MESSAGE)
                addProperty("to", target)
                addProperty("text", body)
                addProperty("msgId", messageId)
                addProperty("peerKey", friend?.key.orEmpty())
            }
        )

        return SocialMessage(
            id = messageId,
            conversationId = conversation.id,
            authorName = SocialTransport.selfName(),
            fromSelf = true,
            text = body,
            sentAtMs = System.currentTimeMillis(),
            failed = !delivered,
            failureReason = if (delivered) null else "Friends/LAN isn't connected - the message stays in history and is not delivered.",
            deletable = true,
        )
    }

    private fun sendToChannel(body: String): SocialMessage {
        val linked = LinkedChannelManager.current()
            ?: return failedMessage(
                SocialConversation(id = "channel:none", kind = ConversationKind.CHANNEL, title = "Community"),
                body,
                "Link a Discord channel first.",
            )
        return LinkedChannelManager.send(body).getOrElse { error ->
            failedMessage(
                SocialConversation(
                    id = LinkedChannelManager.conversationId(linked),
                    kind = ConversationKind.CHANNEL,
                    title = linked.channelName,
                    source = SocialSource.DISCORD,
                ),
                body,
                error.message ?: "Discord rejected the message.",
            )
        }
    }

    private fun failedMessage(conversation: SocialConversation, body: String, reason: String): SocialMessage {
        statusMessage = reason
        return SocialMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversation.id,
            authorName = SocialTransport.selfName(),
            fromSelf = true,
            text = body,
            sentAtMs = System.currentTimeMillis(),
            failed = true,
            failureReason = reason,
            deletable = true,
        )
    }

    fun deleteMessage(conversationId: String, messageId: String) = enqueue {
        val conversation = conversations.firstOrNull { it.id == conversationId }
        val message = synchronized(lock) {
            threads[conversationId]?.firstOrNull { it.id == messageId }
        }
        if (conversation != null && message != null &&
            conversation.source == SocialSource.DISCORD && !conversation.platformChannelId.isNullOrBlank()
        ) {
            DiscordOAuthClient.accessToken()?.let { token ->
                runCatching {
                    DiscordRestClient.deleteMessage(token, conversation.platformChannelId!!, messageId)
                }.onFailure { e -> statusMessage = "Discord: ${e.message}" }
            }
        }
        synchronized(lock) {
            val thread = threads[conversationId] ?: return@synchronized
            thread.removeAll { it.id == messageId }
        }
        refreshConversationPreview(conversationId)
        persist()
        publish()
    }

    fun refreshChannelMessages(onDone: ((List<SocialMessage>) -> Unit)? = null) = enqueue {
        runCatching { LinkedChannelManager.messages() }
            .onSuccess { messages ->
                synchronized(lock) { channelMessages = messages.toMutableList() }
                onDone?.let { callback -> TaskExecutors.runInUIThread { callback(messages) } }
            }
            .onFailure { error ->
                statusMessage = error.message ?: "Could not load the linked channel."
            }
        publish()
    }

    // ── Parties ────────────────────────────────────────────────────────────────────

    fun createLobby(name: String, minecraftVersion: String? = null) = enqueue {
        val lobby = LobbyManager.createLobby(name, minecraftVersion)
        ensureLobbyConversation(lobby)
        publish()
    }

    fun joinLobby(inviteCode: String) = enqueue {
        val lobby = LobbyManager.joinLobby(inviteCode)
        if (lobby == null) {
            statusMessage = "Enter the invite code a friend shared with you."
        } else {
            ensureLobbyConversation(lobby)
            statusMessage = "Party joined. Open Friends/LAN to finish connecting."
        }
        publish()
    }

    fun leaveLobby(lobbyId: String) = enqueue {
        LobbyManager.leaveLobby(lobbyId)
        conversations.removeAll { it.kind == ConversationKind.LOBBY && it.platformChannelId == lobbyId }
        persist()
        publish()
    }

    /** Sends the party invite to [friendKey] as a DM, so it works across both sources. */
    fun inviteToLobby(lobby: PartyLobby, friendKey: String) = enqueue {
        val friend = allFriends().firstOrNull { it.key == friendKey }
        if (friend == null) {
            statusMessage = "That friend is no longer in your list."
            publish()
            return@enqueue
        }
        val text = LobbyManager.inviteText(lobby)
        val conversation = ensureDirectConversation(friend)
        val message = when (conversation.source) {
            SocialSource.DISCORD -> sendDiscordMessage(conversation, text)
            SocialSource.TURTLE -> sendTurtleMessage(conversation, text)
        }
        appendMessage(message)
        statusMessage = "Invited ${friend.displayName} to ${lobby.name}."
        publish()
    }

    /** Posts just the session code - the "invite to my Minecraft session" shortcut. */
    fun inviteToSession(friendKey: String, code: String?) = enqueue {
        val friend = allFriends().firstOrNull { it.key == friendKey } ?: return@enqueue
        val inviteCode = code?.trim()?.takeIf { it.isNotBlank() }
            ?: LobbyManager.currentRoomCode().takeIf { it.isNotBlank() }
            ?: LobbyManager.active()?.inviteCode?.takeIf { it.isNotBlank() }
        if (inviteCode == null) {
            statusMessage = "Host a Friends/LAN room first, then invite."
            publish()
            return@enqueue
        }
        val text = "Join my Minecraft session - Friends/LAN invite code: $inviteCode"
        val conversation = ensureDirectConversation(friend)
        val message = when (conversation.source) {
            SocialSource.DISCORD -> sendDiscordMessage(conversation, text)
            SocialSource.TURTLE -> sendTurtleMessage(conversation, text)
        }
        appendMessage(message)
        statusMessage = "Session invite sent to ${friend.displayName}."
        publish()
    }

    // ── Community channel ──────────────────────────────────────────────────────────

    fun linkChannel(channelId: String, onResult: (Result<LinkedChannel>) -> Unit) = enqueue {
        val result = LinkedChannelManager.link(channelId)
        result.onSuccess { linked ->
            ensureChannelConversation(linked)
            statusMessage = "Linked #${linked.channelName}."
            refreshChannelMessagesInternal()
        }.onFailure { error ->
            statusMessage = error.message ?: "Could not link that channel."
        }
        onResult(result)
        publish()
    }

    fun listChannelChoices(onResult: (Result<List<LinkedChannelManager.GuildChannels>>) -> Unit) =
        enqueue { onResult(LinkedChannelManager.availableGuilds()) }

    fun unlinkChannel() = enqueue {
        LinkedChannelManager.unlink()
        conversations.removeAll { it.kind == ConversationKind.CHANNEL }
        synchronized(lock) { channelMessages.clear() }
        statusMessage = "Channel unlinked."
        persist()
        publish()
    }

    private fun refreshChannelMessagesInternal() {
        runCatching { LinkedChannelManager.messages() }.onSuccess { messages ->
            synchronized(lock) { channelMessages = messages.toMutableList() }
        }
    }

    // ── Transport ──────────────────────────────────────────────────────────────────

    /** Broadcasts our presence + party session so friends see online/idle/playing state. */
    fun announcePresence() {
        LobbyManager.refreshSession()
        if (!SocialTransport.isConnected) return
        val identity = SocialIdentityStore.current()
        val lobby = LobbyManager.active()
        SocialTransport.send(
            JsonObject().apply {
                addProperty("t", SocialTransport.TYPE_PRESENCE)
                addProperty("status", "ONLINE")
                addProperty("label", if (lobby != null) "In a Turtle party" else "In Turtle Launcher")
                lobby?.let {
                    addProperty("lobbyId", it.id)
                    addProperty("lobbyName", it.name)
                }
                identity.minecraftNameHint()?.let { addProperty("minecraft", it) }
            }
        )
    }

    private fun onPayload(payload: JsonObject) {
        val type = payload.stringField("t") ?: return
        worker.execute {
            when (type) {
                SocialTransport.TYPE_DIRECT_MESSAGE -> onDirectMessage(payload)
                SocialTransport.TYPE_FRIEND_REQUEST -> onFriendRequest(payload)
                SocialTransport.TYPE_PRESENCE -> onPresence(payload)
                else -> Unit // lobby + voice payloads are consumed by their managers
            }
        }
    }

    private fun onDirectMessage(payload: JsonObject) {
        val sender = payload.stringField("sender").orEmpty()
        if (sender.isBlank() || sender.equals(SocialTransport.selfName(), ignoreCase = true)) return
        val to = payload.stringField("to").orEmpty()
        if (!isForMe(to)) return
        val text = payload.stringField("text").orEmpty()
        if (text.isBlank()) return

        val friend = ensureTurtleFriend(sender, reason = "Messaged you")
        if (friend.blocked) {
            Logging.i(TAG, "Dropped a message from blocked user $sender")
            return
        }
        val conversation = ensureDirectConversation(friend)
        val message = SocialMessage(
            id = payload.stringField("msgId") ?: UUID.randomUUID().toString(),
            conversationId = conversation.id,
            authorName = sender,
            authorId = friend.key,
            fromSelf = false,
            text = text,
            sentAtMs = System.currentTimeMillis(),
            deletable = false,
        )
        appendMessage(message)
        if (!friend.ignored) {
            bumpUnread(conversation.id)
            notify(conversation, message)
        }
        publish()
    }

    private fun onFriendRequest(payload: JsonObject) {
        val sender = payload.stringField("sender").orEmpty()
        if (sender.isBlank() || sender.equals(SocialTransport.selfName(), ignoreCase = true)) return
        val action = payload.stringField("action") ?: return
        val target = payload.stringField("name").orEmpty()

        when (action) {
            "request" -> {
                if (!target.equals(SocialTransport.selfName(), ignoreCase = true)) return
                if (game.friends.any { it.displayName.equals(sender, ignoreCase = true) }) {
                    // Already friends - tell them we accepted so their outgoing request clears.
                    SocialTransport.send(
                        JsonObject().apply {
                            addProperty("t", SocialTransport.TYPE_FRIEND_REQUEST)
                            addProperty("action", "accept")
                            addProperty("name", sender)
                        }
                    )
                    return
                }
                if (game.requests.none { it.displayName.equals(sender, true) && it.direction == FriendRequestDirection.INCOMING }) {
                    game.requests.add(
                        FriendRequest(
                            id = UUID.randomUUID().toString(),
                            source = SocialSource.TURTLE,
                            displayName = sender,
                            detail = "Wants to be friends",
                            direction = FriendRequestDirection.INCOMING,
                            createdAtMs = System.currentTimeMillis(),
                        )
                    )
                    appContext()?.let { context -> SocialNotifications.notifyFriendRequest(context, sender) }
                }
            }
            "accept" -> {
                if (!target.equals(SocialTransport.selfName(), ignoreCase = true)) return
                game.requests.removeAll {
                    it.direction == FriendRequestDirection.OUTGOING && it.displayName.equals(sender, true)
                }
                ensureTurtleFriend(sender, reason = "Accepted your friend request")
                statusMessage = "$sender accepted your friend request."
            }
            "reject" -> {
                if (!target.equals(SocialTransport.selfName(), ignoreCase = true)) return
                game.requests.removeAll {
                    it.direction == FriendRequestDirection.OUTGOING && it.displayName.equals(sender, true)
                }
                statusMessage = "$sender declined your friend request."
            }
        }
        persist()
        publish()
    }

    private fun onPresence(payload: JsonObject) {
        val sender = payload.stringField("sender").orEmpty()
        if (sender.isBlank() || sender.equals(SocialTransport.selfName(), ignoreCase = true)) return

        val now = System.currentTimeMillis()
        var changed = false
        game = game.copy(
            friends = game.friends.map { friend ->
                val isTheirs = friend.displayName.equals(sender, ignoreCase = true) ||
                    friend.minecraftName?.equals(sender, ignoreCase = true) == true
                if (!isTheirs) return@map friend

                val status = when (payload.stringField("status")) {
                    "IDLE" -> PresenceStatus.IDLE
                    "OFFLINE" -> PresenceStatus.OFFLINE
                    "DO_NOT_DISTURB" -> PresenceStatus.DO_NOT_DISTURB
                    else -> PresenceStatus.ONLINE
                }
                val lobbyId = payload.stringField("lobbyId")
                changed = true
                friend.copy(
                    presence = status,
                    presenceLabel = payload.stringField("label"),
                    inParty = !lobbyId.isNullOrBlank(),
                    lastSeenMs = now,
                    activity = lobbyId?.let {
                        SocialActivity(
                            label = "Minecraft",
                            detail = payload.stringField("lobbyName") ?: "Turtle party",
                            startedAtMs = now,
                        )
                    },
                    minecraftName = payload.stringField("minecraft")?.takeIf { it.isNotBlank() } ?: friend.minecraftName,
                )
            }.toMutableList()
        )
        if (changed) {
            persist()
            publish()
        }
    }

    private fun isForMe(target: String): Boolean {
        if (target.isBlank()) return true
        val identity = SocialIdentityStore.current()
        return target.equals(SocialTransport.selfName(), ignoreCase = true) ||
            target.equals(identity.displayName, ignoreCase = true) ||
            target.equals(identity.effectiveName, ignoreCase = true)
    }

    private fun ensureTurtleFriend(name: String, reason: String): SocialFriend {
        val existing = game.friends.firstOrNull {
            it.displayName.equals(name, true) || it.minecraftName?.equals(name, true) == true
        }
        if (existing != null) return existing
        val created = SocialFriend(
            key = "turtle:${UUID.randomUUID()}",
            source = SocialSource.TURTLE,
            displayName = name,
            minecraftName = name,
            presence = PresenceStatus.ONLINE,
            presenceLabel = reason,
            lastSeenMs = System.currentTimeMillis(),
        )
        game.friends.add(created)
        return created
    }

    // ── Persistence helpers ────────────────────────────────────────────────────────

    private fun appendMessage(message: SocialMessage) {
        synchronized(lock) {
            val thread = threads.getOrPut(message.conversationId) { mutableListOf() }
            thread.add(message)
            while (thread.size > MAX_MESSAGES_PER_THREAD) thread.removeAt(0)
        }
        updateConversation(message.conversationId) { conversation ->
            conversation.copy(
                lastMessage = if (message.text.isBlank()) {
                    message.attachments.firstOrNull()?.fileName ?: "Attachment"
                } else {
                    message.text
                },
                lastMessageAtMs = message.sentAtMs,
            )
        }
        persist()
    }

    private fun bumpUnread(conversationId: String) {
        if (openConversationId == conversationId) return
        updateConversation(conversationId) { it.copy(unreadCount = it.unreadCount + 1) }
    }

    private fun updateConversation(conversationId: String, transform: (SocialConversation) -> SocialConversation) {
        conversations = conversations.map { if (it.id == conversationId) transform(it) else it }.toMutableList()
    }

    private fun notify(conversation: SocialConversation, message: SocialMessage) {
        val title = conversation.title
        val context = appContext() ?: return
        SocialNotifications.notifyMessage(context, conversation.id, title, message.text)
    }

    private fun ensureDirectConversation(friend: SocialFriend): SocialConversation {
        val id = "dm:${friend.key}"
        conversations.firstOrNull { it.id == id }?.let { existing ->
            val updated = existing.copy(
                title = friend.displayName,
                subtitle = friend.presenceLabel ?: friend.subtitle,
                peerKey = friend.key,
                source = friend.source,
            )
            updateConversation(id) { updated }
            return updated
        }
        val created = SocialConversation(
            id = id,
            kind = ConversationKind.DIRECT,
            title = friend.displayName,
            subtitle = friend.presenceLabel ?: friend.subtitle,
            peerKey = friend.key,
            source = friend.source,
            canSend = true,
            canDelete = true,
            supportsAttachments = friend.source == SocialSource.DISCORD,
        )
        conversations.add(created)
        persist()
        return created
    }

    private fun ensureLobbyConversation(lobby: PartyLobby) {
        val id = "lobby:${lobby.id}"
        if (conversations.any { it.id == id }) return
        conversations.add(
            SocialConversation(
                id = id,
                kind = ConversationKind.LOBBY,
                title = lobby.name,
                subtitle = lobby.members.size.toString() + " member(s)",
                platformChannelId = lobby.id,
                source = SocialSource.TURTLE,
                canSend = true,
                canDelete = true,
            )
        )
        persist()
    }

    private fun ensureChannelConversation(linked: LinkedChannel) {
        val id = LinkedChannelManager.conversationId(linked)
        if (conversations.any { it.id == id }) return
        conversations.add(
            SocialConversation(
                id = id,
                kind = ConversationKind.CHANNEL,
                title = "#${linked.channelName}",
                subtitle = linked.guildName,
                platformChannelId = linked.channelId,
                source = SocialSource.DISCORD,
                canSend = true,
                canDelete = true,
                supportsAttachments = false,
            )
        )
        persist()
    }

    private fun refreshConversationPreview(conversationId: String) {
        synchronized(lock) {
            val thread = threads[conversationId] ?: return
            val last = thread.lastOrNull() ?: return
            updateConversation(conversationId) {
                it.copy(lastMessage = last.text, lastMessageAtMs = last.sentAtMs)
            }
        }
    }

    private fun allTurtleFriends(): List<SocialFriend> = synchronized(lock) { game.friends.toList() }

    private fun allFriends(): List<SocialFriend> {
        val turtle = allTurtleFriends()
        val discordIds = turtle.mapNotNull { it.discordUserId?.lowercase() }.toSet()
        val dedupedDiscord = discordFriends.filterNot { friend ->
            friend.discordUserId?.lowercase()?.let { discordIds.contains(it) } == true
        }
        return turtle + dedupedDiscord
    }

    private fun syncConversationsWithFriends(friends: List<SocialFriend>) {
        for (friend in friends) {
            if (friend.isMuted) continue
            ensureDirectConversation(friend)
        }
        val validPeerKeys = friends.map { it.key }.toSet()
        conversations.removeAll { it.kind == ConversationKind.DIRECT && it.peerKey != null && it.peerKey !in validPeerKeys }
    }

    private fun persist() {
        synchronized(lock) {
            SocialStorage.write(FRIENDS_FILE, game)
            // Mutable map with a list per thread; Gson serialises both fine.
            SocialStorage.write(
                MESSAGES_FILE,
                MessageFile(
                    threads = threads.toMutableMap(),
                    conversations = conversations,
                ),
            )
        }
    }

    // ── Snapshot ───────────────────────────────────────────────────────────────────

    private fun publish() {
        val friends = allFriends().sortedWith(
            compareBy<SocialFriend>({ it.isMuted }, { presenceRank(it.presence) }, { it.displayName.lowercase() })
        )
        syncConversationsWithFriends(friends)

        val lobbies = LobbyManager.current()
        val linked = LinkedChannelManager.current()
        if (linked != null) ensureChannelConversation(linked)

        val requests = allTurtleRequests() + discordRequests
        val conversationsSorted = conversations.sortedWith(
            compareByDescending<SocialConversation> { it.lastMessageAtMs }
                .thenByDescending { it.unreadCount }
                .thenBy { it.title.lowercase() }
        )

        snapshot = SocialSnapshot(
            identity = SocialIdentityStore.current(),
            discordLinked = SocialIdentityStore.current().hasDiscordLink,
            friends = friends,
            requests = requests,
            conversations = conversationsSorted,
            lobbies = lobbies,
            linkedChannel = linked,
            voice = VoiceManager.current(),
            statusMessage = statusMessage,
            busy = busy,
        )

        val published = snapshot
        TaskExecutors.runInUIThread {
            for (listener in listeners) runCatching { listener(published) }
        }
    }

    private fun allTurtleRequests(): List<FriendRequest> = synchronized(lock) { game.requests.toList() }

    private fun presenceRank(presence: PresenceStatus): Int = when (presence) {
        PresenceStatus.ONLINE -> 0
        PresenceStatus.IDLE -> 1
        PresenceStatus.DO_NOT_DISTURB -> 2
        PresenceStatus.UNKNOWN -> 3
        PresenceStatus.OFFLINE -> 4
    }

    private fun enqueue(block: () -> Unit) {
        attach()
        worker.execute { runCatching(block).onFailure { e -> Logging.e(TAG, "Social task failed", e) } }
    }

    private fun publishLater() = enqueue { publish() }

    private fun appContext(): Context? = runCatching { ContextExecutor.getApplication() }.getOrNull()
}

/** Minecraft name hint used in the presence envelope; blank when no account is signed in. */
private fun SocialIdentity.minecraftNameHint(): String? =
    runCatching { com.endiq.turtlelauncher.feature.accounts.AccountsManager.currentAccount?.username }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
