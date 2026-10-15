package com.endiq.turtlelauncher.feature.discord

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.endiq.turtlelauncher.setting.Settings

/**
 * Exactly one Gateway owner in :presence. Bound by foreground launcher activities and the
 * existing foreground GameService, so Quit Launcher does not kill Minecraft's RPC connection.
 * Android destroys this bound-only service after its last client goes away.
 */
class DiscordPresenceService : Service() {
    companion object { const val UPDATE = 1; const val REMOVE = 2 }
    private var destroyed = false
    private val state = DiscordPresenceState<IBinder>()
    private val deaths = mutableMapOf<IBinder, IBinder.DeathRecipient>()
    private val handler = Handler(Looper.getMainLooper()) { message -> handle(message); true }
    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private fun handle(message: Message) {
        if (destroyed || message.sendingUid != android.os.Process.myUid()) return
        val client = message.replyTo?.binder ?: return
        when (message.what) {
            UPDATE -> {
                if (client !in deaths) {
                    val death = IBinder.DeathRecipient { handler.post { remove(client); publish() } }
                    try { client.linkToDeath(death, 0) } catch (_: android.os.RemoteException) { return }
                    deaths[client] = death
                }
                state.update(client, message.data.getString("version"))
            }
            REMOVE -> remove(client)
            else -> return
        }
        publish()
    }

    private fun remove(client: IBinder) {
        deaths.remove(client)?.let { client.unlinkToDeath(it, 0) }
        state.remove(client)
    }

    private fun publish() {
        if (destroyed) return
        Settings.refreshSettings() // UI and game are separate processes; never write their settings here.
        if (state.active) DiscordRpcManager.start(state.version) else DiscordRpcManager.stop()
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        deaths.keys.toList().forEach(::remove)
        DiscordRpcManager.stop()
        super.onDestroy()
    }
}
