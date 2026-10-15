package com.endiq.turtlelauncher.feature.discord

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.endiq.turtlelauncher.feature.log.Logging

/** A bound-service lease; no Discord credentials are passed across processes. Main-thread only. */
class DiscordPresenceClient(context: Context, private var version: String? = null) : ServiceConnection {
    private val context = context.applicationContext
    private var remote: Messenger? = null
    private var bound = false
    private val reply = Messenger(Handler(Looper.getMainLooper()))

    fun connect() {
        if (bound || !DiscordRpcManager.isConfigured()) return
        runCatching {
            bound = context.bindService(Intent(context, DiscordPresenceService::class.java), this, Context.BIND_AUTO_CREATE)
        }.onFailure { Logging.w("DiscordPresence", "Could not bind presence service") }
    }

    fun refresh(version: String? = this.version) {
        this.version = version
        if (!bound) connect() else send(DiscordPresenceService.UPDATE)
    }

    fun close() {
        send(DiscordPresenceService.REMOVE)
        if (bound) context.unbindService(this)
        bound = false
        remote = null
    }

    private fun send(command: Int) {
        val endpoint = remote ?: return
        runCatching {
            endpoint.send(Message.obtain(null, command).apply {
                replyTo = reply
                data = Bundle().apply { putString("version", version) }
            })
        }.onFailure { remote = null }
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        remote = Messenger(binder ?: return)
        send(DiscordPresenceService.UPDATE)
    }
    override fun onServiceDisconnected(name: ComponentName?) { remote = null }
    override fun onBindingDied(name: ComponentName?) { close(); connect() }
    override fun onNullBinding(name: ComponentName?) { close() }
}
