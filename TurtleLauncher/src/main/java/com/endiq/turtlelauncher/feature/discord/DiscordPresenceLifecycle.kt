package com.endiq.turtlelauncher.feature.discord

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import net.endiq.launcher.MainActivity
import java.util.Collections
import java.util.IdentityHashMap

/** One launcher lease, not one Gateway connection per screen. A short grace covers transitions. */
object DiscordPresenceLifecycle : Application.ActivityLifecycleCallbacks {
    private val visible = Collections.newSetFromMap(IdentityHashMap<Activity, Boolean>())
    private val handler = Handler(Looper.getMainLooper())
    private var client: DiscordPresenceClient? = null
    private var registered = false
    private val close = Runnable { if (visible.isEmpty()) { client?.close(); client = null } }

    @JvmStatic fun register(application: Application) {
        if (registered) return
        registered = true
        application.registerActivityLifecycleCallbacks(this)
    }

    @JvmStatic fun refresh() { client?.refresh() }

    override fun onActivityStarted(activity: Activity) {
        if (activity is MainActivity) return // the foreground GameService owns the game lease
        handler.removeCallbacks(close)
        visible.add(activity)
        if (client == null) client = DiscordPresenceClient(activity.applicationContext)
        client?.refresh()
    }
    override fun onActivityStopped(activity: Activity) {
        visible.remove(activity)
        if (visible.isEmpty()) handler.postDelayed(close, 2000L)
    }
    override fun onActivityDestroyed(activity: Activity) { visible.remove(activity) }
    override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
}
