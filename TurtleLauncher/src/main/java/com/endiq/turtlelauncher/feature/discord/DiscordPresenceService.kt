package com.endiq.turtlelauncher.feature.discord

import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log

/**
 * Cross-process election only: NEVER initializes the SDK in this Activity-less process.
 * The elected launcher/game process owns the real SDK Activity and native client.
 */
class DiscordPresenceService : Service() {
    companion object {
        const val UPDATE = 1
        const val REMOVE = 2
        const val ACTIVATE = 3
        const val DEACTIVATE = 4
        const val STOPPED = 5
    }
    private var destroyed = false
    private val state = DiscordPresenceState<IBinder>()
    private val clients = mutableMapOf<IBinder, Messenger>()
    private val deaths = mutableMapOf<IBinder, IBinder.DeathRecipient>()
    private var owner: IBinder? = null
    private var stopping: IBinder? = null
    private val handler = Handler(Looper.getMainLooper()) { handle(it); true }
    private val messenger = Messenger(handler)
    private val handoffTimeout = Runnable {
        Log.w("DiscordPresence", "Previous owner did not acknowledge shutdown; continuing handoff")
        owner = null
        stopping = null
        publish()
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun handle(message: Message) {
        if (destroyed || message.sendingUid != Process.myUid()) return
        val client = message.replyTo?.binder ?: return
        when (message.what) {
            UPDATE -> {
                if (client !in deaths) {
                    val death = IBinder.DeathRecipient { handler.post { remove(client); publish() } }
                    try { client.linkToDeath(death, 0) } catch (_: RemoteException) { return }
                    deaths[client] = death
                }
                clients[client] = message.replyTo
                state.update(client, message.data.getString("version"))
            }
            REMOVE -> remove(client) // client shuts down its SDK before removing its lease
            STOPPED -> {
                if (client != stopping) return
                owner = null
                stopping = null
                handler.removeCallbacks(handoffTimeout)
            }
            else -> return
        }
        publish()
    }

    private fun remove(client: IBinder) {
        deaths.remove(client)?.let { client.unlinkToDeath(it, 0) }
        clients.remove(client)
        state.remove(client)
        if (owner == client) owner = null
        if (stopping == client) {
            stopping = null
            handler.removeCallbacks(handoffTimeout)
        }
    }

    private fun send(client: IBinder, command: Int): Boolean = try {
        clients[client]?.send(Message.obtain(null, command).apply {
            data = Bundle().apply { putString("version", state.version) }
        }) != null
    } catch (_: RemoteException) { remove(client); false }

    private fun publish() {
        if (destroyed || stopping != null) return
        val selected = state.owner
        val old = owner
        if (old != null && old != selected) {
            stopping = old
            if (send(old, DEACTIVATE)) {
                handler.postDelayed(handoffTimeout, 3000L)
                return
            }
            stopping = null
        }
        owner = state.owner
        owner?.let { if (!send(it, ACTIVATE)) publish() }
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        owner?.let { send(it, DEACTIVATE) }
        deaths.keys.toList().forEach(::remove)
        super.onDestroy()
    }
}
