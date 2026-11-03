package com.endiq.turtlelauncher.feature.discord

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import net.endiq.launcher.MainActivity
import net.endiq.launcher.LauncherActivity
import net.endiq.launcher.services.GameService
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Launcher lease plus an actual Activity reference in each possible SDK-owner process.
 *
 * Presence is meant to be *always* up while Turtle Launcher is in use, so this class owns the
 * keep-alive below in addition to the Activity bookkeeping:
 *
 *  - the lease is re-published every [HEARTBEAT_MS], which re-elects the owner if the presence
 *    service was recreated or its binding died (no Activity event fires for either);
 *  - a connection that is supposed to be up but is not (`DiscordRpcManager.isConnected()` is
 *    false) is fully re-armed at most every [RECOVER_INTERVAL_MS]. That is what makes the RPC
 *    recover when Discord is installed/started *after* the launcher, when the user force-stops
 *    Discord, or when the RPC pipe is closed under us - previously the only retry was the next
 *    Activity start, which may never come while the user plays Minecraft.
 *
 * The heartbeat never runs in a process that has no lease, and it never resurrects a connection
 * the user switched off in Settings (`discordRpcPresenceEnabled` is still checked by
 * [DiscordRpcManager.start], which every lease update goes through).
 */
object DiscordPresenceLifecycle : Application.ActivityLifecycleCallbacks {
    private const val HEARTBEAT_MS = 30_000L
    private const val RECOVER_INTERVAL_MS = 120_000L

    private val visible = Collections.newSetFromMap(IdentityHashMap<Activity, Boolean>())
    private val handler = Handler(Looper.getMainLooper())
    private var client: DiscordPresenceClient? = null
    private var registered = false
    @Volatile private var lastRecoverAt = 0L

    private val close = Runnable {
        if (visible.isEmpty()) {
            handler.removeCallbacks(heartbeat)
            client?.close()
            client = null
        }
    }

    /** The lease in this process: the launcher's client, or the game's (see [GameService]). */
    private fun liveClient(): DiscordPresenceClient? = client ?: GameService.currentPresence()

    /**
     * Keep-alive tick. Re-publishing is cheap (one binder message; the SDK already re-sends the
     * activity every 30s natively), while [recover] restarts the native client - so it is only
     * used for a lane that holds the election and still reports no successful publish.
     */
    private val heartbeat = object : Runnable {
        override fun run() {
            val live = liveClient()
            if (live == null) {
                // Nothing to keep alive. While a game session is running the GameService may
                // still be coming up, so keep ticking rather than dropping the watch.
                if (GameService.isActive()) {
                    handler.removeCallbacks(this)
                    handler.postDelayed(this, HEARTBEAT_MS)
                } else {
                    handler.removeCallbacks(this)
                }
                return
            }
            live.refresh()
            if (live.activated && DiscordRpcManager.isConfigured() && !DiscordRpcManager.isConnected()) {
                val now = System.currentTimeMillis()
                if (now - lastRecoverAt >= RECOVER_INTERVAL_MS) {
                    lastRecoverAt = now
                    live.recover()
                }
            }
            handler.removeCallbacks(this)
            handler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    private fun startHeartbeat() {
        handler.removeCallbacks(heartbeat)
        handler.postDelayed(heartbeat, HEARTBEAT_MS)
    }

    @JvmStatic fun register(application: Application) {
        if (registered) return
        registered = true
        application.registerActivityLifecycleCallbacks(this)
    }

    @JvmStatic fun refresh() {
        client?.refresh()
        startHeartbeat()
    }

    override fun onActivityCreated(activity: Activity, state: Bundle?) {
        if (activity is LauncherActivity || activity is MainActivity) DiscordRpcManager.setActivity(activity)
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity is MainActivity) {
            DiscordRpcManager.setActivity(activity)
            GameService.refreshDiscordPresence()
            // The game process owns its own lease; keep it re-published for long sessions.
            startHeartbeat()
            return // the foreground GameService owns this process's game lease
        }
        if (activity !is LauncherActivity) return
        DiscordRpcManager.setActivity(activity)
        handler.removeCallbacks(close)
        visible.add(activity)
        if (client == null) client = DiscordPresenceClient(activity.applicationContext)
        client?.refresh()
        startHeartbeat()
    }

    override fun onActivityStopped(activity: Activity) {
        // Previously this scheduled client?.close() 2s after the last LauncherActivity
        // went invisible, which tore down the whole Discord connection the moment the
        // launcher was merely backgrounded (Home button, app switcher, etc). Keep the
        // presence connection alive in the background - only onActivityDestroyed below
        // (the activity actually being torn down, not just hidden) releases it.
    }

    override fun onActivityDestroyed(activity: Activity) {
        visible.remove(activity)
        if (activity is LauncherActivity && visible.isEmpty()) {
            handler.removeCallbacks(close)
            handler.removeCallbacks(heartbeat)
            client?.close()
            client = null
        }
        if (activity is MainActivity && activity.isFinishing) GameService.endDiscordPresence()
        DiscordRpcManager.releaseActivity(activity)
    }

    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
}
