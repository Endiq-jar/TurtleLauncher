package com.endiq.turtlelauncher.feature.social

import com.google.gson.JsonObject
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Voice state for parties and Discord-style voice channels.
 *
 * ## What is real here, and what is not
 *
 * The *session* layer is real: joining/leaving, mute, deafen, push-to-talk, per-participant
 * volume, speaking indicators and the participant stream list all work and are shared with
 * the other players in the party over the Friends/LAN tunnel (`{"t":"voice", ...}`).
 *
 * The *audio* layer is not in this build. Discord's voice implementation lives behind
 * `Client::Connect()` in the Social SDK (OAuth session + native gateway + Opus/WebRTC
 * capture), while Turtle Launcher's native bridge deliberately uses only the SDK's
 * token-free Rich Presence RPC - that is exactly what makes Rich Presence work without
 * signing in. [VoiceCapabilities] reports this honestly, and nothing in the UI claims to
 * transmit audio: the screen labels the session state and disables the audio-only knobs
 * (`noise suppression`, device pickers) instead of pretending they do something.
 *
 * When a native voice transport is added later it plugs into [VoiceTransport] - one call to
 * [setTransport] - and the whole existing UI starts driving real audio.
 */
object VoiceManager {
    private const val TAG = "VoiceManager"
    private const val FILE_NAME = "voice_prefs.json"

    /** Hook for a future native audio implementation (see the class comment). */
    interface VoiceTransport {
        fun open(channelId: String): Boolean
        fun close()
        fun setMuted(muted: Boolean)
        fun setDeafened(deafened: Boolean)
        fun isAvailable(): Boolean
    }

    internal data class VoicePrefs(
        val muted: Boolean = false,
        val deafened: Boolean = false,
        val pushToTalk: Boolean = false,
        val noiseSuppression: Boolean = false,
        val inputVolume: Int = 100,
        val outputVolume: Int = 100,
        val inputDevice: String? = null,
        val outputDevice: String? = null,
        val channelId: String? = null,
        val channelName: String? = null,
    )

    private val listeners = CopyOnWriteArrayList<(VoiceState) -> Unit>()

    @Volatile
    private var transport: VoiceTransport? = null

    @Volatile
    private var prefs: VoicePrefs = VoicePrefs()

    @Volatile
    private var state: VoiceState = VoiceState()

    @Volatile
    private var loaded = false

    private val transportListener: (JsonObject) -> Unit = { payload -> onPayload(payload) }

    @Synchronized
    fun attach() {
        if (!loaded) {
            loaded = true
            prefs = SocialStorage.read(FILE_NAME, VoicePrefs::class.java) ?: VoicePrefs(
                muted = false,
                deafened = false,
                pushToTalk = AllSettings.socialPushToTalk.getValue(),
                noiseSuppression = AllSettings.socialNoiseSuppression.getValue(),
            )
            state = VoiceState(
                muted = prefs.muted,
                deafened = prefs.deafened,
                pushToTalkEnabled = prefs.pushToTalk,
                noiseSuppression = prefs.noiseSuppression,
                capabilities = capabilities(),
            )
        }
        SocialTransport.addListener(transportListener)
    }

    fun addListener(listener: (VoiceState) -> Unit) {
        if (!listeners.contains(listener)) listeners.add(listener)
        listener(state)
    }

    fun removeListener(listener: (VoiceState) -> Unit) = listeners.remove(listener)

    fun current(): VoiceState = state

    /** Registers a native audio implementation (none ships in this build). */
    fun setTransport(value: VoiceTransport?) {
        transport = value
        state = state.copy(capabilities = capabilities())
        publish()
    }

    private fun capabilities(): VoiceCapabilities {
        val native = transport?.isAvailable() == true
        return VoiceCapabilities(
            sdkVoiceAvailable = native,
            pushToTalk = true,
            noiseSuppression = native,
            inputDeviceControl = native,
            outputDeviceControl = native,
            readOnlySettings = true,
        )
    }

    /**
     * Joins a voice channel. On the Turtle side the "channel" is the party; on the Discord
     * side it would be a real guild voice channel once the SDK transport exists.
     */
    @Synchronized
    fun join(channelId: String, channelName: String, memberNames: List<String> = emptyList()) {
        attach()
        if (channelId.isBlank()) return
        state = state.copy(
            connection = VoiceConnectionState.CONNECTING,
            channelId = channelId,
            channelName = channelName,
        )
        publish()

        val native = transport?.let { runCatching { it.open(channelId) }.getOrDefault(false) } == true
        val signalling = SocialTransport.isConnected
        val connection = when {
            native -> VoiceConnectionState.CONNECTED
            // The interface is live over the party tunnel even without audio - the UI shows
            // participants and mute/speaking state, and says so.
            signalling -> VoiceConnectionState.CONNECTED
            else -> VoiceConnectionState.FAILED
        }

        val self = VoiceParticipant(
            id = "self",
            name = SocialTransport.selfName(),
            muted = prefs.muted,
            deafened = prefs.deafened,
            isSelf = true,
        )
        state = state.copy(
            connection = connection,
            participants = listOf(self) + memberNames
                .filter { !it.equals(self.name, ignoreCase = true) }
                .map { VoiceParticipant(id = it, name = it) },
        )
        prefs = prefs.copy(channelId = channelId, channelName = channelName)
        persist()
        broadcastState()
        publish()
        Logging.i(TAG, "Voice join '$channelName' -> $connection (native audio: $native)")
    }

    @Synchronized
    fun leave(channelId: String? = null) {
        val currentChannel = state.channelId
        if (channelId != null && currentChannel != null && currentChannel != channelId) return
        runCatching { transport?.close() }
        state = state.copy(
            connection = VoiceConnectionState.DISCONNECTED,
            channelId = null,
            channelName = null,
            speaking = false,
            participants = emptyList(),
            capabilities = capabilities(),
        )
        prefs = prefs.copy(channelId = null, channelName = null)
        persist()
        broadcastState()
        publish()
    }

    @Synchronized
    fun setMuted(muted: Boolean) {
        attach()
        prefs = prefs.copy(muted = muted)
        runCatching { transport?.setMuted(muted) }
        state = state.copy(
            muted = muted,
            participants = updateSelf { it.copy(muted = muted) },
        )
        persist()
        broadcastState()
        publish()
    }

    @Synchronized
    fun setDeafened(deafened: Boolean) {
        attach()
        prefs = prefs.copy(deafened = deafened)
        runCatching { transport?.setDeafened(deafened) }
        state = state.copy(
            deafened = deafened,
            participants = updateSelf { it.copy(deafened = deafened) },
        )
        persist()
        broadcastState()
        publish()
    }

    @Synchronized
    fun setPushToTalk(enabled: Boolean) {
        attach()
        prefs = prefs.copy(pushToTalk = enabled)
        AllSettings.socialPushToTalk.put(enabled).save()
        state = state.copy(pushToTalkEnabled = enabled)
        persist()
        publish()
    }

    @Synchronized
    fun setNoiseSuppression(enabled: Boolean) {
        attach()
        if (!state.capabilities.noiseSuppression) {
            // Nothing to apply - but remember the preference so it takes effect the moment a
            // native voice transport exists.
            AllSettings.socialNoiseSuppression.put(enabled).save()
            prefs = prefs.copy(noiseSuppression = enabled)
            state = state.copy(noiseSuppression = enabled)
            persist()
            publish()
            return
        }
        AllSettings.socialNoiseSuppression.put(enabled).save()
        prefs = prefs.copy(noiseSuppression = enabled)
        state = state.copy(noiseSuppression = enabled)
        persist()
        publish()
    }

    /** Push-to-talk key down/up, or a manual "I'm speaking" indicator. */
    @Synchronized
    fun setSpeaking(speaking: Boolean) {
        if (state.connection != VoiceConnectionState.CONNECTED) return
        if (state.muted || state.deafened) {
            if (state.speaking == speaking) return
            state = state.copy(speaking = false, participants = updateSelf { it.copy(speaking = false) })
        } else {
            state = state.copy(speaking = speaking, participants = updateSelf { it.copy(speaking = speaking) })
        }
        broadcastSpeaking()
        publish()
    }

    @Synchronized
    fun setParticipantVolume(name: String, volume: Int) {
        state = state.copy(
            participants = state.participants.map {
                if (it.name.equals(name, ignoreCase = true)) it.copy(volume = volume.coerceIn(0, 200)) else it
            },
        )
        publish()
    }

    @Synchronized
    fun setDevices(inputDevice: String?, outputDevice: String?) {
        prefs = prefs.copy(
            inputDevice = inputDevice ?: prefs.inputDevice,
            outputDevice = outputDevice ?: prefs.outputDevice,
        )
        persist()
        publish()
    }

    fun inputDevice(): String? = prefs.inputDevice

    fun outputDevice(): String? = prefs.outputDevice

    /** Keeps the participant list in sync with the party lobby it is attached to. */
    @Synchronized
    fun updateMembers(memberNames: List<String>) {
        if (state.connection != VoiceConnectionState.CONNECTED) return
        val existing = state.participants.associateBy { it.name.lowercase() }
        val selfName = SocialTransport.selfName()
        val participants = listOf(
            existing["self"] ?: VoiceParticipant(id = "self", name = selfName, isSelf = true)
        ) + memberNames
            .filter { !it.equals(selfName, ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .map { name -> existing[name.lowercase()] ?: VoiceParticipant(id = name, name = name) }
        state = state.copy(participants = participants)
        publish()
    }

    // ── Wire protocol ──────────────────────────────────────────────────────────────

    private fun updateSelf(transform: (VoiceParticipant) -> VoiceParticipant): List<VoiceParticipant> {
        val selfName = SocialTransport.selfName()
        if (state.participants.none { it.isSelf }) {
            return listOf(transform(VoiceParticipant(id = "self", name = selfName, isSelf = true))) +
                state.participants
        }
        return state.participants.map { if (it.isSelf) transform(it) else it }
    }

    private fun broadcastState() {
        if (state.channelId == null) return
        val payload = JsonObject().apply {
            addProperty("t", SocialTransport.TYPE_VOICE)
            addProperty("action", "state")
            addProperty("channelId", state.channelId)
            addProperty("channelName", state.channelName.orEmpty())
            addProperty("muted", state.muted)
            addProperty("deafened", state.deafened)
        }
        SocialTransport.send(payload)
    }

    private fun broadcastSpeaking() {
        val payload = JsonObject().apply {
            addProperty("t", SocialTransport.TYPE_VOICE)
            addProperty("action", "speaking")
            addProperty("channelId", state.channelId.orEmpty())
            addProperty("speaking", state.speaking)
        }
        SocialTransport.send(payload)
    }

    private fun onPayload(payload: JsonObject) {
        if (payload.stringField("t") != SocialTransport.TYPE_VOICE) return
        val sender = payload.stringField("sender").orEmpty()
        if (sender.isBlank() || sender.equals(SocialTransport.selfName(), ignoreCase = true)) return
        val channelId = payload.stringField("channelId").orEmpty()
        if (state.connection != VoiceConnectionState.CONNECTED) return
        if (channelId.isNotBlank() && channelId != state.channelId) return

        when (payload.stringField("action")) {
            "state" -> {
                val muted = payload.get("muted")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                val deafened = payload.get("deafened")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                val existing = state.participants.firstOrNull { it.name.equals(sender, true) }
                val updated = (existing ?: VoiceParticipant(id = sender, name = sender)).copy(
                    muted = muted,
                    deafened = deafened,
                )
                state = state.copy(
                    participants = if (existing == null) {
                        state.participants + updated
                    } else {
                        state.participants.map { if (it.name.equals(sender, true)) updated else it }
                    },
                )
                publish()
            }
            "speaking" -> {
                val speaking = payload.get("speaking")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
                state = state.copy(
                    participants = state.participants.map { participant ->
                        if (participant.name.equals(sender, true)) participant.copy(speaking = speaking) else participant
                    },
                )
                publish()
            }
        }
    }

    private fun persist() {
        SocialStorage.write(FILE_NAME, prefs)
    }

    private fun publish() {
        val snapshot = state.copy(capabilities = capabilities())
        for (listener in listeners) runCatching { listener(snapshot) }
    }
}
