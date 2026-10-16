package com.endiq.turtlelauncher.feature.discord

import android.content.*
import android.os.*
import com.endiq.turtlelauncher.feature.log.Logging

/** A broker lease. SDK commands execute only in the elected client's Activity process. */
class DiscordPresenceClient(context: Context, private var version: String? = null) : ServiceConnection {
    private val context = context.applicationContext
    private var remote: Messenger? = null
    private var bound = false
    private var closing = false
    private val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.sendingUid == Process.myUid()) {
            when (message.what) {
                DiscordPresenceService.ACTIVATE -> if (!closing) DiscordRpcManager.start(message.data.getString("version"))
                DiscordPresenceService.DEACTIVATE -> DiscordRpcManager.stop { send(DiscordPresenceService.STOPPED) }
            }
        }
        true
    })

    fun connect() {
        if (bound || closing) return
        runCatching {
            bound = context.bindService(Intent(context, DiscordPresenceService::class.java), this, Context.BIND_AUTO_CREATE)
        }.onFailure { Logging.w("DiscordPresence", "Could not bind presence coordinator") }
    }

    fun refresh(version: String? = this.version) {
        if (closing) return
        this.version = version
        if (!bound) connect() else send(DiscordPresenceService.UPDATE)
    }

    fun close() {
        if (closing) return
        closing = true
        // Do not elect another process until this one has released its Discord connection.
        DiscordRpcManager.stop {
            send(DiscordPresenceService.REMOVE)
            if (bound) context.unbindService(this)
            bound = false
            remote = null
        }
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
        if (!closing) send(DiscordPresenceService.UPDATE)
    }
    override fun onServiceDisconnected(name: ComponentName?) {
        remote = null
        DiscordRpcManager.stop()
    }
    override fun onBindingDied(name: ComponentName?) {
        if (bound) context.unbindService(this)
        bound = false
        remote = null
        DiscordRpcManager.stop()
        if (!closing) connect()
    }
    override fun onNullBinding(name: ComponentName?) { close() }
}
