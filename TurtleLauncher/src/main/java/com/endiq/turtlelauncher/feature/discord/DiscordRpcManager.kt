package com.endiq.turtlelauncher.feature.discord

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.path.UrlManager
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Discord Rich Presence for the launcher.
 *
 * Android's official Discord app does not expose a local Rich Presence IPC endpoint, so this
 * mirrors how community libraries such as dead8309/KizzyRPC and the DiscordRP app work: it opens
 * a direct connection to the Discord Gateway with the signed-in account and publishes a presence
 * (Gateway op-code 3) on that account. It never touches the account password; the only credential
 * used is the user-supplied Discord token, which is read from the launcher's private settings and
 * never logged.
 *
 * Gateway op-codes handled:
 *  - 10 Hello           → identify (2) or resume (6) + start the heartbeat loop.
 *  -  0 Dispatch        → READY (capture session id + resume gateway url) / RESUMED.
 *  -  1 Heartbeat       → server-requested heartbeat.
 *  -  7 Reconnect       → close with 4000 and reconnect to the resume gateway url.
 *  -  9 Invalid Session → drop the session and identify again.
 *
 * The presence activity supports the full feature set KizzyRPC exposes: name / details / state,
 * an activity type, elapsed timestamps, large and small artwork (image + hover text), and up to
 * two clickable buttons with their URLs.
 */
object DiscordRpcManager {

    private const val TAG = "Turtle-DiscordRPC"

    /** Base gateway; on reconnect we prefer the resume gateway url advertised in the READY payload. */
    private const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"

    /** Gateway close code we treat as "reconnectable". */
    private const val RECONNECT_CLOSE_CODE = 4000

    /** Discord's documented length limits for activity fields. */
    private const val MAX_TEXT_LEN = 128
    private const val MAX_STATE_DETAILS_LEN = 256
    private const val MAX_BUTTON_LABEL = 32
    private const val MAX_BUTTON_URL = 512

    private val VALID_STATUSES = setOf("online", "idle", "dnd", "invisible")
    private val VALID_TYPES = setOf("0", "1", "2", "3", "5")

    private val lock = Any()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "Turtle-DiscordRPC").apply { isDaemon = true }
    }

    private var socket: WebSocket? = null
    private var heartbeat: ScheduledFuture<*>? = null
    private var sequence: Long? = null
    private var sessionId: String? = null
    private var resumeGatewayUrl: String? = null
    private var token: String? = null
    private var currentVersion: String? = null
    private var startedAt = 0L

    /** Monotonic counter so stale sockets from a previous start()/stop() can be ignored. */
    private var generation = 0L

    /** True once the gateway reports READY (or RESUMED); presence is only sent after this. */
    @Volatile private var ready = false

    /**
     * Opens (or re-uses) the RPC connection for [versionName].
     *
     * @param versionName the selected Minecraft version, used to fill the `{version}` placeholder.
     */
    @JvmStatic
    @JvmOverloads
    fun start(versionName: String? = null) {
        val effectiveToken = AllSettings.discordRpcToken.getValue().trim()
        if (effectiveToken.isEmpty()) {
            Log.w(TAG, "No Discord RPC token configured; skipping start().")
            return
        }
        synchronized(lock) {
            // Already connected and READY with the same token → just refresh the presence in place.
            if (isConnected() && ready && token == effectiveToken) {
                if (versionName != null) currentVersion = versionName
                if (startedAt == 0L) startedAt = System.currentTimeMillis()
                sendPresence()
                return
            }
            // Recreate the connection for a new token / a fresh session.
            closeSocketLocked(clearSession = true)
            token = effectiveToken
            currentVersion = versionName ?: currentVersion
            startedAt = System.currentTimeMillis()
            ready = false
            openSocketLocked(token!!, resumeGatewayUrl ?: GATEWAY_URL)
        }
    }

    /** Cancels any heartbeat and closes the socket. Safe to call when not running. */
    @JvmStatic
    fun stop() {
        synchronized(lock) {
            heartbeat?.cancel(false)
            heartbeat = null
            // Best-effort "clear activity" so the presence disappears promptly for others.
            if (isConnected()) sendClear()
            closeSocketLocked(clearSession = true)
            generation += 1
            token = null
            currentVersion = null
            sequence = null
            sessionId = null
            resumeGatewayUrl = null
            startedAt = 0L
            ready = false
        }
    }

    /** True when a non-null token is stored. */
    @JvmStatic
    fun isConfigured(): Boolean = AllSettings.discordRpcToken.getValue().trim().isNotEmpty()

    /** True when the gateway socket is currently open. */
    @JvmStatic
    fun isConnected(): Boolean = socket != null

    /**
     * Re-sends the presence over an existing, READY connection. Used when the activity content
     * changes but the account/session is unchanged (e.g. the user edits the customization while a
     * game session is running). No-op when the socket is not ready.
     */
    @JvmStatic
    fun updatePresence() {
        synchronized(lock) {
            if (isConnected() && ready) sendPresence()
        }
    }

    // ── WebSocket plumbing ───────────────────────────────────────────────────────────

    private fun openSocketLocked(effectiveToken: String, url: String) {
        // Bump the generation so any callback still in flight from a previous socket is ignored.
        generation += 1
        val gen = generation
        val request = Request.Builder().url(url).build()
        socket = UrlManager.createOkHttpClient().newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Gateway connected (${url.take(48)}…).")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (gen != generation) return
                try {
                    handlePayload(JsonParser.parseString(text).asJsonObject)
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to handle gateway frame: ${t.message}")
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleDisconnect(gen, code)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Gateway socket failure: ${t.message}")
                handleDisconnect(gen, -1)
            }
        })
    }

    private fun handlePayload(payload: JsonObject) {
        synchronized(lock) {
            // Track the sequence number for heartbeats and RESUME.
            payload.get("s")?.takeIf { !it.isJsonNull }?.asLong?.let { sequence = it }

            when (payload.get("op")?.asInt) {
                0 -> handleDispatch(payload)          // DISPATCH
                1 -> sendHeartbeat()                  // server-requested heartbeat
                7 -> requestReconnect()               // RECONNECT
                9 -> handleInvalidSession()           // INVALID SESSION
                10 -> handleHello(payload)            // HELLO
                else -> Unit
            }
        }
    }

    private fun handleDispatch(payload: JsonObject) {
        when (payload.get("t")?.asString) {
            "READY" -> {
                val data = payload.getAsJsonObject("d")
                sessionId = data.get("session_id")?.asString
                data.get("resume_gateway_url")?.asString?.let {
                    resumeGatewayUrl = if (it.startsWith("wss://")) "$it/?v=10&encoding=json" else it
                }
                ready = true
                Log.i(TAG, "READY; session established. Now publishing presence.")
                sendPresence()
            }
            "RESUMED" -> {
                ready = true
                Log.i(TAG, "Session resumed; refreshing presence.")
                sendPresence()
            }
            else -> Unit
        }
    }

    private fun handleHello(payload: JsonObject) {
        val interval = payload.getAsJsonObject("d").get("heartbeat_interval")?.asLong ?: 41250L
        startHeartbeatLocked(interval)

        // Resume if we still have a session to continue, otherwise identify from scratch.
        if (sequence != null && !sessionId.isNullOrBlank()) {
            sendResume()
        } else {
            sendIdentify()
        }
    }

    private fun handleInvalidSession() {
        Log.i(TAG, "Invalid session; re-identifying.")
        // A fresh identify requires a clean sequence/session.
        sequence = null
        sessionId = null
        resumeGatewayUrl = null
        ready = false
        sendIdentify()
    }

    private fun requestReconnect() {
        Log.i(TAG, "Gateway requested reconnect.")
        handleDisconnect(generation, RECONNECT_CLOSE_CODE)
    }

    private fun handleDisconnect(gen: Long, code: Int) {
        // Called both from the locked frame handler (via RECONNECT) and directly from OkHttp's
        // close/failure callbacks, so it must take the lock itself (synchronized is reentrant).
        synchronized(lock) {
            // A newer socket has since been created; this stale callback must be ignored without
            // disturbing the live connection's heartbeat.
            if (gen != generation) return

            heartbeat?.cancel(false)
            heartbeat = null
            ready = false

            // Close code 4000 (either from the gateway's RECONNECT op or a manual resume trigger)
            // means "reconnect immediately to the resume gateway url". Anything else is a real
            // failure: drop the session and stop.
            if (code == RECONNECT_CLOSE_CODE) {
                socket?.cancel()
                socket = null
                openSocketLocked(token ?: return, resumeGatewayUrl ?: GATEWAY_URL)
            } else {
                socket?.cancel()
                socket = null
                Log.w(TAG, "Gateway closed (code=$code); RPC stopped.")
                token = null
                currentVersion = null
                sequence = null
                sessionId = null
                resumeGatewayUrl = null
                startedAt = 0L
            }
        }
    }

    private fun closeSocketLocked(clearSession: Boolean) {
        heartbeat?.cancel(false)
        heartbeat = null
        socket?.close(1000, null)
        socket = null
        if (clearSession) {
            sequence = null
            sessionId = null
            resumeGatewayUrl = null
            ready = false
        }
    }

    // ── Heartbeat ───────────────────────────────────────────────────────────────────

    /**
     * Starts the heartbeat loop. The first beat is sent after a jittered fraction of the interval
     * (Discord's recommended practice to avoid a thundering herd); subsequent beats run on a fixed
     * period equal to the interval.
     */
    private fun startHeartbeatLocked(intervalMs: Long) {
        heartbeat?.cancel(false)
        val safeInterval = if (intervalMs > 0) intervalMs else 41250L
        val initialDelay = (safeInterval * (0.5 + 0.5 * Random.nextDouble())).toLong()
        heartbeat = scheduler.scheduleAtFixedRate({
            // Guard the loop so a transient send error cannot cancel the fixed-rate heartbeat.
            try {
                synchronized(lock) { sendHeartbeat() }
            } catch (t: Throwable) {
                Log.w(TAG, "Heartbeat send failed: ${t.message}")
            }
        }, initialDelay, safeInterval, TimeUnit.MILLISECONDS)
    }

    // ── Outgoing frames ─────────────────────────────────────────────────────────────

    private fun send(op: Int, d: Any?) {
        val frame = JsonObject()
        frame.addProperty("op", op)
        when (d) {
            null -> frame.add("d", JsonNull.INSTANCE)
            is JsonElement -> frame.add("d", d)
            is Number -> frame.addProperty("d", d)          // heartbeat/RESUME sequence as a number
            else -> frame.addProperty("d", d.toString())
        }
        socket?.send(frame.toString())
    }

    private fun sendIdentify() {
        Log.i(TAG, "Sending IDENTIFY.")
        val properties = JsonObject()
        properties.addProperty("os", "Windows")
        properties.addProperty("browser", "Discord Client")
        properties.addProperty("device", "TurtleLauncher")
        val data = JsonObject()
        data.addProperty("token", token)
        data.addProperty("capabilities", 65)
        data.addProperty("compress", false)
        data.addProperty("largeThreshold", 100)
        data.add("properties", properties)
        send(2, data)
    }

    private fun sendResume() {
        Log.i(TAG, "Sending RESUME.")
        val data = JsonObject()
        data.addProperty("token", token)
        data.addProperty("session_id", sessionId)
        data.addProperty("seq", sequence)
        send(6, data)
    }

    private fun sendHeartbeat() {
        // d is the last received sequence number, or null before the first dispatch.
        send(1, sequence)
    }

    /** Builds the presence payload from the current settings and publishes it (op 3). */
    private fun sendPresence() {
        val version = currentVersion ?: ""
        val activity = buildActivity(version)
        val presence = JsonObject()
        presence.addProperty("status", safeStatus(AllSettings.discordRpcStatus.getValue()))
        presence.addProperty("afk", false)
        presence.addProperty("since", if (startedAt > 0) startedAt else System.currentTimeMillis())
        val activities = JsonArray()
        activities.add(activity)
        presence.add("activities", activities)
        send(3, presence)
    }

    /**
     * Clears the current activity (op 3 with an empty activity list) while keeping the account
     * online, so the presence reverts cleanly once the socket closes.
     */
    private fun sendClear() {
        val presence = JsonObject()
        presence.addProperty("status", "online")
        presence.addProperty("afk", false)
        presence.addProperty("since", 0)
        presence.add("activities", JsonArray())
        send(3, presence)
    }

    // ── Activity model ──────────────────────────────────────────────────────────────

    private fun buildActivity(version: String): JsonObject {
        val activity = JsonObject()
        activity.addProperty(
            "name",
            renderText(AllSettings.discordRpcName.getValue(), version).ifEmpty { "Minecraft: Java Edition" }
        )

        val details = renderText(AllSettings.discordRpcDetails.getValue(), version)
        if (details.isNotEmpty()) activity.addProperty("details", details.take(MAX_STATE_DETAILS_LEN))

        val state = renderText(AllSettings.discordRpcState.getValue(), version)
        if (state.isNotEmpty()) activity.addProperty("state", state.take(MAX_STATE_DETAILS_LEN))

        activity.addProperty("type", safeType(AllSettings.discordRpcType.getValue()))

        // Elapsed session timer.
        if (AllSettings.discordRpcShowElapsed.getValue()) {
            val timestamps = JsonObject()
            timestamps.addProperty("start", if (startedAt > 0) startedAt else System.currentTimeMillis())
            activity.add("timestamps", timestamps)
        }

        val applicationId = AllSettings.discordRpcApplicationId.getValue().trim()
            .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        if (applicationId != null) activity.addProperty("application_id", applicationId)

        // Artwork. Assets require an application id to resolve on the client.
        val largeImage = AllSettings.discordRpcLargeImage.getValue().trim()
        val smallImage = AllSettings.discordRpcSmallImage.getValue().trim()
        if (applicationId != null && (largeImage.isNotEmpty() || smallImage.isNotEmpty())) {
            val assets = JsonObject()
            if (largeImage.isNotEmpty()) {
                assets.addProperty("large_image", largeImage.take(MAX_TEXT_LEN))
                val largeText = AllSettings.discordRpcLargeText.getValue().trim()
                if (largeText.isNotEmpty()) assets.addProperty("large_text", largeText.take(MAX_TEXT_LEN))
            }
            if (smallImage.isNotEmpty()) {
                assets.addProperty("small_image", smallImage.take(MAX_TEXT_LEN))
                val smallText = AllSettings.discordRpcSmallText.getValue().trim()
                if (smallText.isNotEmpty()) assets.addProperty("small_text", smallText.take(MAX_TEXT_LEN))
            }
            activity.add("assets", assets)
        }

        // Up to two buttons. A button is only emitted when it has both a label and a URL so the
        // parallel button_urls array stays valid.
        val buttonPairs = mutableListOf<Pair<String, String>>()
        listOf(
            AllSettings.discordRpcButton1.getValue() to AllSettings.discordRpcButton1Url.getValue(),
            AllSettings.discordRpcButton2.getValue() to AllSettings.discordRpcButton2Url.getValue(),
        ).forEach { (label, url) ->
            val l = label.trim()
            val u = url.trim()
            if (l.isNotEmpty() && u.isNotEmpty()) buttonPairs.add(l.take(MAX_BUTTON_LABEL) to u.take(MAX_BUTTON_URL))
        }
        if (buttonPairs.isNotEmpty()) {
            val buttons = JsonArray()
            val urls = JsonArray()
            buttonPairs.forEach { (label, url) ->
                buttons.add(label)
                urls.add(url)
            }
            activity.add("buttons", buttons)
            val metadata = JsonObject()
            metadata.add("button_urls", urls)
            activity.add("metadata", metadata)
        }

        return activity
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────

    /** Replaces the `{version}` placeholder and trims the result. */
    private fun renderText(template: String, version: String): String {
        val safeVersion = version.ifBlank { "Minecraft" }
        return template.replace("{version}", safeVersion).trim()
    }

    private fun safeStatus(value: String): String = value.trim().lowercase().takeIf { it in VALID_STATUSES } ?: "online"

    private fun safeType(value: String): Int = value.trim().takeIf { it in VALID_TYPES }?.toIntOrNull() ?: 0
}
