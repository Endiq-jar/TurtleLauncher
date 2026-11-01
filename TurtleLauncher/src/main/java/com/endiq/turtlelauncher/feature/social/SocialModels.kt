package com.endiq.turtlelauncher.feature.social

/**
 * Shared value types for the Friends feature.
 *
 * The launcher's social graph is a *union* of two things that used to be separate:
 *
 *  - **Discord** - whoever the linked Discord account can see (friends, blocked users,
 *    incoming/outgoing requests), read through Discord's REST API with the launcher's own
 *    OAuth2 (public client + PKCE) session. See [DiscordOAuthClient] / [DiscordRestClient].
 *  - **Turtle** - the people you actually played with: friends added from a Friends/LAN
 *    (Terracotta) party, request/accept flows, block/ignore, and presence that comes from
 *    the P2P tunnel itself.
 *
 * Both collapse into [SocialFriend] with a stable [SocialFriend.key], which is what the UI
 * and the message store use. Nothing in this file touches Android APIs, so the merge rules
 * are unit-testable off-device (see SocialDirectoryTest).
 */

enum class SocialSource { DISCORD, TURTLE }

/** Presence as the launcher understands it. Discord presence needs the Social SDK's gateway. */
enum class PresenceStatus { ONLINE, IDLE, DO_NOT_DISTURB, OFFLINE, UNKNOWN }

/** What a friend is doing right now, when known. */
data class SocialActivity(
    val label: String,
    val detail: String? = null,
    val startedAtMs: Long = 0L,
)

/**
 * One row in the unified friends list.
 *
 * [key] is stable across renames and across identity merging: for Discord it is
 * `discord:<userId>`, for Turtle it is `turtle:<local record id>`. Linking a Discord account
 * does not rewrite those keys - it fills in the Discord fields on the matching Turtle record
 * instead, so conversations keep pointing at the same row.
 */
data class SocialFriend(
    val key: String,
    val source: SocialSource,
    val displayName: String,
    val subtitle: String? = null,
    val avatarUrl: String? = null,
    val discordUserId: String? = null,
    val minecraftName: String? = null,
    val presence: PresenceStatus = PresenceStatus.UNKNOWN,
    val presenceLabel: String? = null,
    val activity: SocialActivity? = null,
    val blocked: Boolean = false,
    val ignored: Boolean = false,
    val inParty: Boolean = false,
    val lastSeenMs: Long = 0L,
    val note: String? = null,
) {
    /** Blocked/ignored rows stay visible, but are sorted last and marked in the UI. */
    val isMuted: Boolean get() = blocked || ignored
}

enum class FriendRequestDirection { INCOMING, OUTGOING }

/** A pending friend request, from either source. */
data class FriendRequest(
    val id: String,
    val source: SocialSource,
    val displayName: String,
    val detail: String? = null,
    val direction: FriendRequestDirection,
    val createdAtMs: Long = 0L,
    val discordUserId: String? = null,
) {
    val key: String get() = if (source == SocialSource.DISCORD) {
        "discord:$discordUserId"
    } else {
        "turtle:$id"
    }
}

enum class ConversationKind { DIRECT, LOBBY, CHANNEL }

/**
 * A thread in the Messages tab. [id] is namespaced by kind so a lobby chat and a DM with the
 * same person never collide.
 *
 * [platformChannelId] is the Discord channel id for DIRECT/CHANNEL conversations, and the
 * lobby id for LOBBY threads. [canSend]/[canDelete]/[supportsAttachments] mirror what the
 * backing transport actually permits - the UI disables, rather than silently drops, actions.
 */
data class SocialConversation(
    val id: String,
    val kind: ConversationKind,
    val title: String,
    val subtitle: String? = null,
    val peerKey: String? = null,
    val platformChannelId: String? = null,
    val unreadCount: Int = 0,
    val lastMessage: String? = null,
    val lastMessageAtMs: Long = 0L,
    val canSend: Boolean = true,
    val canDelete: Boolean = false,
    val supportsAttachments: Boolean = false,
    val source: SocialSource = SocialSource.TURTLE,
)

/** Attachment metadata. Only ever populated where the transport supports files. */
data class SocialAttachment(
    val url: String,
    val fileName: String? = null,
    val contentType: String? = null,
    val sizeBytes: Long = 0L,
)

/**
 * One message in a conversation.
 *
 * [pending] means "queued locally, not yet acknowledged"; [failed] means the transport
 * rejected it (offline Discord request, no Terracotta tunnel, ...). Both are shown, so a
 * message never silently disappears.
 */
data class SocialMessage(
    val id: String,
    val conversationId: String,
    val authorName: String,
    val authorId: String? = null,
    val fromSelf: Boolean = false,
    val text: String = "",
    val sentAtMs: Long = 0L,
    val pending: Boolean = false,
    val failed: Boolean = false,
    val failureReason: String? = null,
    val deletable: Boolean = false,
    val attachments: List<SocialAttachment> = emptyList(),
)

/** A participant in a party lobby or voice channel. */
data class LobbyMember(
    val name: String,
    val muted: Boolean = false,
    val deafened: Boolean = false,
    val speaking: Boolean = false,
    val isSelf: Boolean = false,
)

/**
 * A private multiplayer party. [inviteCode] is a Terracotta room code when the party is
 * hosted over Friends/LAN, and [hostAddress] is the address guests paste into Minecraft
 * (same string the Terracotta screen shows).
 */
data class PartyLobby(
    val id: String,
    val name: String,
    val ownerName: String,
    val inviteCode: String = "",
    val hostAddress: String? = null,
    val minecraftVersion: String? = null,
    val members: List<LobbyMember> = emptyList(),
    val isOwner: Boolean = false,
    val voiceEnabled: Boolean = true,
    val createdAtMs: Long = 0L,
    val inviteMessage: String? = null,
)

enum class VoiceConnectionState { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, FAILED }

/**
 * What the current build can actually do with voice.
 *
 * Discord's voice stack lives inside the Social SDK's `Client::Connect` path, which needs an
 * OAuth2 session *and* the native gateway connection; this build's native bridge deliberately
 * only uses the SDK's token-free Rich Presence RPC (that is what makes presence work without
 * signing in). So the launcher ships the whole voice UI and party/voice *signalling*, and
 * reports honestly - through these flags - which parts are waiting on the SDK connection.
 */
data class VoiceCapabilities(
    val sdkVoiceAvailable: Boolean = false,
    val pushToTalk: Boolean = true,
    val noiseSuppression: Boolean = false,
    val inputDeviceControl: Boolean = false,
    val outputDeviceControl: Boolean = false,
    val readOnlySettings: Boolean = true,
)

data class VoiceParticipant(
    val id: String,
    val name: String,
    val muted: Boolean = false,
    val deafened: Boolean = false,
    val speaking: Boolean = false,
    val volume: Int = 100,
    val isSelf: Boolean = false,
)

data class VoiceState(
    val connection: VoiceConnectionState = VoiceConnectionState.DISCONNECTED,
    val channelId: String? = null,
    val channelName: String? = null,
    val muted: Boolean = false,
    val deafened: Boolean = false,
    val pushToTalkEnabled: Boolean = false,
    val noiseSuppression: Boolean = false,
    val speaking: Boolean = false,
    val participants: List<VoiceParticipant> = emptyList(),
    val capabilities: VoiceCapabilities = VoiceCapabilities(),
)

/** A Discord text channel linked into the launcher's Communities tab. */
data class LinkedChannel(
    val channelId: String,
    val channelName: String,
    val guildId: String,
    val guildName: String,
    val linkedAtMs: Long = 0L,
)

/** One-shot view of everything the Friends screen renders. */
data class SocialSnapshot(
    val identity: SocialIdentity = SocialIdentity(),
    val discordLinked: Boolean = false,
    val friends: List<SocialFriend> = emptyList(),
    val requests: List<FriendRequest> = emptyList(),
    val conversations: List<SocialConversation> = emptyList(),
    val lobbies: List<PartyLobby> = emptyList(),
    val linkedChannel: LinkedChannel? = null,
    val voice: VoiceState = VoiceState(),
    val statusMessage: String? = null,
    val busy: Boolean = false,
) {
    val discordFriendCount: Int get() = friends.count { it.source == SocialSource.DISCORD && !it.isMuted }
    val turtleFriendCount: Int get() = friends.count { it.source == SocialSource.TURTLE && !it.isMuted }
    val unreadTotal: Int get() = conversations.sumOf { it.unreadCount }
    val incomingRequestCount: Int get() = requests.count { it.direction == FriendRequestDirection.INCOMING }
}
