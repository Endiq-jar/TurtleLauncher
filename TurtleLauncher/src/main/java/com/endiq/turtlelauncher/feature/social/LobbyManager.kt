package com.endiq.turtlelauncher.feature.social

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.feature.terracotta.Terracotta
import com.endiq.turtlelauncher.feature.terracotta.TerracottaState
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Private multiplayer parties ("Play Together").
 *
 * A party is a named group with:
 *  - an invite code (a Terracotta room code, i.e. the same code the Friends/LAN screen shows),
 *  - its members and their voice/mute state (see [VoiceManager]),
 *  - shared metadata: Minecraft version, host address, owner,
 *  - a text thread (the Messages tab renders it as a LOBBY conversation).
 *
 * Parties are *not* server-side: like the rest of the Turtle side of this feature they live
 * in the P2P tunnel. The owner broadcasts a `sync` envelope, joiners announce themselves,
 * and everybody keeps the list locally. Multiple parties can be active at once - the list is
 * a list, and the UI shows every one of them.
 *
 * The launcher cannot silently join a Terracotta room: connecting needs the VPN permission
 * prompt and an Activity. [joinLobby] therefore records the party and hands the code to the
 * UI, which opens the Friends/LAN screen with it pre-filled (TerracottaFragment.BUNDLE_JOIN_CODE).
 */
object LobbyManager {
    private const val TAG = "LobbyManager"
    private const val FILE_NAME = "lobbies.json"

    private const val ACTION_SYNC = "sync"
    private const val ACTION_JOIN = "join"
    private const val ACTION_LEAVE = "leave"
    private const val ACTION_SESSION = "session"

    internal data class LobbyFile(
        val lobbies: MutableList<PartyLobby> = mutableListOf(),
    )

    private val listeners = CopyOnWriteArrayList<(List<PartyLobby>) -> Unit>()

    @Volatile
    private var lobbies: List<PartyLobby> = emptyList()

    @Volatile
    private var loaded = false

    private val transportListener: (JsonObject) -> Unit = { payload -> onPayload(payload) }

    /** Loads persisted parties and starts listening. Safe to call repeatedly. */
    @Synchronized
    fun attach() {
        if (loaded) return
        loaded = true
        lobbies = SocialStorage.read(FILE_NAME, LobbyFile::class.java)?.lobbies?.toList().orEmpty()
        SocialTransport.addListener(transportListener)
        publish()
    }

    fun addListener(listener: (List<PartyLobby>) -> Unit) {
        if (!listeners.contains(listener)) listeners.add(listener)
        listener(lobbies)
    }

    fun removeListener(listener: (List<PartyLobby>) -> Unit) = listeners.remove(listener)

    fun current(): List<PartyLobby> = lobbies

    fun active(): PartyLobby? = lobbies.firstOrNull()

    /**
     * Creates a party owned by this install. If a Friends/LAN room is already hosted, its
     * code becomes the invite code immediately; otherwise the code is filled in by
     * [refreshSession] as soon as the host starts one.
     */
    @Synchronized
    fun createLobby(
        name: String,
        minecraftVersion: String? = null,
    ): PartyLobby {
        attach()
        val owner = SocialTransport.selfName()
        val cleanedName = name.trim().ifBlank { "$owner's party" }
        val lobby = PartyLobby(
            id = UUID.randomUUID().toString(),
            name = cleanedName,
            ownerName = owner,
            inviteCode = currentRoomCode(),
            hostAddress = currentHostAddress(),
            minecraftVersion = minecraftVersion,
            members = listOf(LobbyMember(name = owner, isSelf = true)),
            isOwner = true,
            createdAtMs = System.currentTimeMillis(),
        )
        lobbies = lobbies + lobby
        persist()
        broadcastSync(lobby)
        publish()
        Logging.i(TAG, "Created party '${lobby.name}' (invite code: ${lobby.inviteCode.ifBlank { "none yet" }})")
        return lobby
    }

    /**
     * Joins a party from an invite code. Stores the party locally and broadcasts a join
     * announcement; actually connecting Minecraft is the Friends/LAN screen's job (the UI
     * passes the same code to TerracottaFragment).
     */
    @Synchronized
    fun joinLobby(inviteCode: String, name: String? = null): PartyLobby? {
        attach()
        val code = inviteCode.trim()
        if (code.isEmpty()) return null

        lobbies.firstOrNull { it.inviteCode == code }?.let { existing ->
            // Already known: still announce, so the owner sees us in the member list.
            sendEnvelope(ACTION_JOIN, existing.id, extras = { it.addProperty("member", SocialTransport.selfName()) })
            return existing
        }

        val self = SocialTransport.selfName()
        val lobby = PartyLobby(
            id = UUID.randomUUID().toString(),
            name = name?.trim()?.takeIf { it.isNotBlank() } ?: "Party $code",
            ownerName = "",
            inviteCode = code,
            members = listOf(LobbyMember(name = self, isSelf = true)),
            isOwner = false,
            createdAtMs = System.currentTimeMillis(),
        )
        lobbies = lobbies + lobby
        persist()
        sendEnvelope(ACTION_JOIN, lobby.id, extras = { it.addProperty("member", self) })
        publish()
        return lobby
    }

    @Synchronized
    fun leaveLobby(lobbyId: String) {
        val lobby = lobbies.firstOrNull { it.id == lobbyId } ?: return
        sendEnvelope(ACTION_LEAVE, lobby.id, extras = { it.addProperty("member", SocialTransport.selfName()) })
        lobbies = lobbies.filterNot { it.id == lobbyId }
        persist()
        VoiceManager.leave(lobby.id)
        publish()
    }

    /** Refreshes invite details from the live Terracotta state and shares them if changed. */
    @Synchronized
    fun refreshSession(minecraftVersion: String? = null) {
        attach()
        if (lobbies.isEmpty()) return
        val code = currentRoomCode()
        val address = currentHostAddress()
        var changed = false
        lobbies = lobbies.map { lobby ->
            if (!lobby.isOwner) return@map lobby
            val updated = lobby.copy(
                inviteCode = code.ifBlank { lobby.inviteCode },
                hostAddress = address ?: lobby.hostAddress,
                minecraftVersion = minecraftVersion ?: lobby.minecraftVersion,
            )
            if (updated != lobby) changed = true
            updated
        }
        if (changed) {
            persist()
            lobbies.filter { it.isOwner }.forEach { lobby -> sendEnvelope(ACTION_SESSION, lobby.id) { it.addProperty("code", lobby.inviteCode); it.addProperty("address", lobby.hostAddress.orEmpty()); it.addProperty("version", lobby.minecraftVersion.orEmpty()) } }
            publish()
        }
    }

    @Synchronized
    fun addMember(lobbyId: String, memberName: String) {
        val name = memberName.trim()
        if (name.isEmpty()) return
        lobbies = lobbies.map { lobby ->
            if (lobby.id != lobbyId) return@map lobby
            if (lobby.members.any { it.name.equals(name, ignoreCase = true) }) return@map lobby
            lobby.copy(members = lobby.members + LobbyMember(name = name))
        }
        persist()
        publish()
    }

    @Synchronized
    fun removeMember(lobbyId: String, memberName: String) {
        lobbies = lobbies.map { lobby ->
            if (lobby.id != lobbyId) return@map lobby
            lobby.copy(members = lobby.members.filterNot { it.name.equals(memberName, ignoreCase = true) })
        }
        persist()
        publish()
    }

    /** Everything a friend needs in order to join, suitable for a DM or a share sheet. */
    fun inviteText(lobby: PartyLobby): String {
        val code = lobby.inviteCode.trim()
        val address = lobby.hostAddress?.trim().orEmpty()
        return buildString {
            append("Join my Turtle Minecraft party")
            if (lobby.name.isNotBlank()) append(" - ${lobby.name}")
            if (!lobby.minecraftVersion.isNullOrBlank()) append(" (Minecraft ${lobby.minecraftVersion})")
            append(".")
            if (code.isNotBlank()) append(" Invite code: $code")
            else append(" Set up a Friends/LAN room first, then send the code.")
            if (address.isNotBlank()) append(" Server: $address")
        }
    }

    /** Terracotta room code currently hosted by this install, or "" when not hosting. */
    fun currentRoomCode(): String = runCatching {
        (Terracotta.getState() as? TerracottaState.HostOK)?.code.orEmpty()
    }.getOrDefault("")

    /** Address guests paste into Minecraft, when this install is the guest of a room. */
    fun currentHostAddress(): String? = runCatching {
        (Terracotta.getState() as? TerracottaState.GuestOK)?.url
    }.getOrNull()?.takeIf { it.isNotBlank() }

    // ── Wire protocol ──────────────────────────────────────────────────────────────

    private fun sendEnvelope(action: String, lobbyId: String, extras: ((JsonObject) -> Unit)? = null) {
        val lobby = lobbies.firstOrNull { it.id == lobbyId } ?: return
        val payload = JsonObject().apply {
            addProperty("t", SocialTransport.TYPE_LOBBY)
            addProperty("action", action)
            addProperty("lobbyId", lobby.id)
            addProperty("name", lobby.name)
            addProperty("owner", lobby.ownerName)
            addProperty("version", lobby.minecraftVersion.orEmpty())
            addProperty("isOwner", lobby.isOwner)
            extras?.invoke(this)
        }
        SocialTransport.send(payload)
    }

    private fun broadcastSync(lobby: PartyLobby) {
        val members = JsonArray()
        lobby.members.forEach { members.add(it.name) }
        sendEnvelope(ACTION_SYNC, lobby.id) { payload ->
            payload.addProperty("code", lobby.inviteCode)
            payload.addProperty("address", lobby.hostAddress.orEmpty())
            payload.add("members", members)
        }
    }

    private fun onPayload(payload: JsonObject) {
        if (payload.stringField("t") != SocialTransport.TYPE_LOBBY) return
        val action = payload.stringField("action") ?: return
        val sender = payload.stringField("sender").orEmpty()
        if (sender.equals(SocialTransport.selfName(), ignoreCase = true)) return

        val lobbyId = payload.stringField("lobbyId").orEmpty()
        when (action) {
            ACTION_SYNC -> {
                val remoteId = lobbyId.ifBlank { return }
                val name = payload.stringField("name").orEmpty().ifBlank { "Party" }
                val owner = payload.stringField("owner").orEmpty()
                val members = payload.getAsJsonArray("members")?.mapNotNull { it.asString } ?: emptyList()
                val existing = lobbies.firstOrNull { it.id == remoteId }
                val merged = (existing ?: PartyLobby(
                    id = remoteId,
                    name = name,
                    ownerName = owner,
                    createdAtMs = System.currentTimeMillis(),
                )).copy(
                    name = name,
                    ownerName = owner,
                    inviteCode = payload.stringField("code").orEmpty().ifBlank { existing?.inviteCode.orEmpty() },
                    hostAddress = payload.stringField("address")?.takeIf { it.isNotBlank() } ?: existing?.hostAddress,
                    minecraftVersion = payload.stringField("version")?.takeIf { it.isNotBlank() } ?: existing?.minecraftVersion,
                    isOwner = false,
                    members = (members + sender + SocialTransport.selfName())
                        .filter { it.isNotBlank() }
                        .distinctBy { it.lowercase() }
                        .map { name -> LobbyMember(name = name, isSelf = name.equals(SocialTransport.selfName(), true)) },
                )
                lobbies = if (existing == null) lobbies + merged else lobbies.map { if (it.id == remoteId) merged else it }
                persist()
                publish()
            }
            ACTION_JOIN -> {
                val member = payload.stringField("member").orEmpty().ifBlank { sender }
                if (lobbyId.isNotBlank()) addMember(lobbyId, member)
                lobbies.firstOrNull { it.id == lobbyId }?.let { broadcastSync(it) }
            }
            ACTION_LEAVE -> {
                val member = payload.stringField("member").orEmpty().ifBlank { sender }
                if (lobbyId.isNotBlank()) removeMember(lobbyId, member)
            }
            ACTION_SESSION -> {
                lobbies = lobbies.map { lobby ->
                    if (lobby.id != lobbyId) return@map lobby
                    lobby.copy(
                        inviteCode = payload.stringField("code").orEmpty().ifBlank { lobby.inviteCode },
                        hostAddress = payload.stringField("address")?.takeIf { it.isNotBlank() } ?: lobby.hostAddress,
                        minecraftVersion = payload.stringField("version")?.takeIf { it.isNotBlank() } ?: lobby.minecraftVersion,
                    )
                }
                persist()
                publish()
            }
        }
    }

    private fun persist() {
        SocialStorage.write(FILE_NAME, LobbyFile(lobbies.toMutableList()))
    }

    private fun publish() {
        val snapshot = lobbies
        listeners.forEach { listener -> runCatching { listener(snapshot) } }
    }
}

/** CurseForge-style explicit null handling, shared by the social wire protocol. */
internal fun JsonObject.stringField(name: String): String? =
    runCatching { get(name)?.takeIf { !it.isJsonNull }?.asString }.getOrNull()
