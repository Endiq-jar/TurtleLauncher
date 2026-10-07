package com.endiq.turtlelauncher.feature.discord

import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.path.UrlManager
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Discord Rich Presence for Android.
 *
 * Discord does not expose its desktop IPC socket on Android, so this optional implementation
 * talks to the Discord Gateway as the signed-in user. That makes RPC possible on Android, but
 * it is not an official Discord integration and may violate Discord's rules for automated user
 * accounts. It is therefore opt-in, requires a user-supplied token, and never runs unless the
 * warning in the settings screen has been accepted.
 *
 * The token is deliberately never written to logs or included in errors.
 */
object DiscordRpcManager {
    private const val TAG = "DiscordRPC"
    private const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"

    private val lock = Any()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "Turtle-DiscordRPC").apply { isDaemon = true }
    }

    private var socket: WebSocket? = null
    private var heartbeat: ScheduledFuture<*>? = null
    private var sequence: Long? = null
    private var currentVersion: String? = null
    private var startedAt = 0L
    private var generation = 0L

    @JvmStatic
    fun start(versionName: String) {
        val token = AllSettings.discordRpcToken.getValue().trim()
        if (!AllSettings.discordRpcEnabled.getValue() || token.isEmpty()) return

        synchronized(lock) {
            stopLocked(sendClear = false)
            currentVersion = versionName.ifBlank { "Minecraft: Java Edition" }
            startedAt = System.currentTimeMillis()
            generation++
            openSocket(token, generation)
        }
    }

    @JvmStatic
    fun stop() {
        synchronized(lock) {
            stopLocked(sendClear = true)
            currentVersion = null
        }
    }

    @JvmStatic
    fun isConfigured(): Boolean = AllSettings.discordRpcToken.getValue().isNotBlank()

    private fun openSocket(token: String, socketGeneration: Long) {
        val request = Request.Builder().url(GATEWAY_URL).build()
        socket = UrlManager.createOkHttpClient().newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrent(webSocket, socketGeneration)) return
                runCatching { handlePayload(webSocket, token, socketGeneration, JsonParser.parseString(text).asJsonObject) }
                    .onFailure { Logging.e(TAG, "Could not process a Discord Gateway message", it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isCurrent(webSocket, socketGeneration)) return
                Logging.e(TAG, "Discord Rich Presence connection failed", t)
                synchronized(lock) {
                    if (isCurrent(webSocket, socketGeneration)) stopLocked(sendClear = false)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrent(webSocket, socketGeneration)) return
                Logging.i(TAG, "Discord Rich Presence connection closed ($code)")
                synchronized(lock) {
                    if (isCurrent(webSocket, socketGeneration)) stopLocked(sendClear = false)
                }
            }
        })
    }

    private fun handlePayload(webSocket: WebSocket, token: String, socketGeneration: Long, payload: JsonObject) {
        if (payload.has("s") && !payload.get("s").isJsonNull) sequence = payload.get("s").asLong
        when (payload.get("op")?.asInt) {
            10 -> { // Hello
                val interval = payload.getAsJsonObject("d").get("heartbeat_interval").asLong
                scheduleHeartbeat(webSocket, socketGeneration, interval)
                sendIdentify(webSocket, token)
            }
            0 -> if (payload.get("t")?.asString == "READY") sendPresence(webSocket)
            1 -> sendHeartbeat(webSocket)
            7 -> reconnect(token, webSocket, socketGeneration)
            9 -> sendIdentify(webSocket, token)
        }
    }

    private fun sendIdentify(webSocket: WebSocket, token: String) {
        val properties = JsonObject().apply {
            addProperty("os", "Android")
            addProperty("browser", "Discord Client")
            addProperty("device", "Turtle Launcher")
        }
        val identify = JsonObject().apply {
            addProperty("token", token)
            addProperty("capabilities", 65)
            addProperty("compress", false)
            addProperty("large_threshold", 100)
            add("properties", properties)
        }
        send(webSocket, 2, identify)
    }

    private fun sendPresence(webSocket: WebSocket) {
        val version = synchronized(lock) { currentVersion } ?: return
        val activity = JsonObject().apply {
            addProperty("name", "Minecraft: Java Edition")
            addProperty("details", "Playing $version")
            addProperty("state", "Launched with Turtle Launcher")
            addProperty("type", 0)
            add("timestamps", JsonObject().apply { addProperty("start", startedAt) })
        }
        val activities = JsonArray().apply { add(activity) }
        val presence = JsonObject().apply {
            addProperty("since", startedAt)
            add("activities", activities)
            addProperty("status", "online")
            addProperty("afk", false)
        }
        send(webSocket, 3, presence)
        Logging.i(TAG, "Discord Rich Presence started for Minecraft $version")
    }

    private fun sendClear(webSocket: WebSocket) {
        val presence = JsonObject().apply {
            addProperty("since", 0)
            add("activities", JsonArray())
            addProperty("status", "online")
            addProperty("afk", false)
        }
        send(webSocket, 3, presence)
    }

    private fun sendHeartbeat(webSocket: WebSocket) {
        val data = sequence?.let { com.google.gson.JsonPrimitive(it) } ?: com.google.gson.JsonNull.INSTANCE
        send(webSocket, 1, data)
    }

    private fun scheduleHeartbeat(webSocket: WebSocket, socketGeneration: Long, intervalMs: Long) {
        synchronized(lock) {
            heartbeat?.cancel(false)
            heartbeat = scheduler.scheduleAtFixedRate(
                { if (isCurrent(webSocket, socketGeneration)) sendHeartbeat(webSocket) },
                intervalMs,
                intervalMs,
                TimeUnit.MILLISECONDS
            )
        }
    }

    private fun reconnect(token: String, webSocket: WebSocket, socketGeneration: Long) {
        synchronized(lock) {
            if (!isCurrent(webSocket, socketGeneration)) return
            heartbeat?.cancel(false)
            heartbeat = null
            socket = null
            webSocket.close(4000, "Reconnect requested")
            generation++
            openSocket(token, generation)
        }
    }

    private fun send(webSocket: WebSocket, op: Int, data: com.google.gson.JsonElement) {
        val payload = JsonObject().apply {
            addProperty("op", op)
            add("d", data)
        }
        webSocket.send(payload.toString())
    }

    private fun isCurrent(webSocket: WebSocket, socketGeneration: Long): Boolean =
        synchronized(lock) { socket === webSocket && generation == socketGeneration }

    private fun stopLocked(sendClear: Boolean) {
        heartbeat?.cancel(false)
        heartbeat = null
        sequence = null
        socket?.let {
            if (sendClear) sendClear(it)
            it.close(1000, "Minecraft stopped")
        }
        socket = null
        generation++
    }
}
