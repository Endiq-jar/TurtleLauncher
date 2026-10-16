package com.endiq.turtlelauncher.feature.discord

import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.setting.Settings
import android.app.Activity
import android.os.Handler
import android.os.Looper
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Official token-free RPC. Only the broker-elected Activity process runs the SDK.
 * SDK creation, publishing and shutdown are serialized off the main/UI thread.
 */
object DiscordRpcManager {

    /** Turtle Launcher's registered Discord application. A settings override can replace this. */
    private const val DEFAULT_APPLICATION_ID = 1560576927419338863L

    private const val MAX_TEXT_LEN = 128
    private const val MAX_STATE_DETAILS_LEN = 128
    private const val MAX_BUTTON_LABEL = 32
    private const val MAX_IMAGE_KEY_LEN = 300

    private val VALID_TYPES = setOf(0, 1, 2, 3, 5)

    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Turtle-DiscordOwner").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var activity = WeakReference<Activity>(null)
    @Volatile private var active = false
    private var sdkActivity = WeakReference<Activity>(null)
    @Volatile private var initializedApplicationId: Long? = null
    private val session = DiscordPresenceSession()
    private var owner: Any? = null // actor-thread only; stale lease shutdowns cannot stop a new owner

    /** Resolves the configured application id, falling back to the launcher's own. */
    private fun applicationId(): Long =
        AllSettings.discordRpcApplicationId.getValue().trim()
            .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?.toLongOrNull()?.takeIf { it > 0 }
            ?: DEFAULT_APPLICATION_ID

    @JvmStatic fun setActivity(value: Activity) { activity = WeakReference(value) }

    @JvmStatic fun releaseActivity(value: Activity) {
        if (activity.get() === value) {
            activity = WeakReference(null)
            executor.execute {
                // Losing/recreating an Activity is not the end of the GameService lease.
                // A new Activity may already have arrived before this actor command runs.
                if (activity.get() == null) {
                    active = false
                    shutdown(resetSession = false)
                }
            }
        }
    }

    private fun prepareActivity(): Boolean {
        val latch = CountDownLatch(1)
        var ok = false
        main.post {
            try {
                activity.get()?.takeIf { !it.isDestroyed && !it.isFinishing }?.let {
                    ok = DiscordSocialBridge.prepare(it)
                    if (ok) sdkActivity = WeakReference(it)
                }
            } finally { latch.countDown() }
        }
        return latch.await(5, TimeUnit.SECONDS) && ok
    }

    private fun shutdown(resetSession: Boolean = true) {
        if (initializedApplicationId != null) DiscordSocialBridge.shutdown()
        initializedApplicationId = null
        sdkActivity = WeakReference(null)
        if (resetSession) session.reset()
    }

    @JvmStatic @JvmOverloads fun start(versionName: String? = null, lease: Any? = null) {
        executor.execute {
            owner = lease
            runCatching {
                Settings.refreshSettings()
                // Erase unused legacy credentials rather than leaving them in private storage.
                val legacyKeys = listOf("discordRpcToken", "discordAccessToken", "discordRefreshToken",
                    "discordUserId", "discordUsername", "discordAvatarHash")
                if (legacyKeys.any { Settings.Manager.getValue(it, "") { value -> value }.isNotBlank() }) {
                    val erase = Settings.Manager.put("discordRpcEnabled", false)
                    legacyKeys.forEach { erase.put(it, "") }
                    erase.save()
                }
                val wanted = applicationId()
                if (initializedApplicationId != wanted || sdkActivity.get() !== activity.get()) {
                    shutdown(resetSession = false)
                    if (!prepareActivity() || !DiscordSocialBridge.init(wanted)) return@execute
                    initializedApplicationId = wanted
                }
                session.start(versionName, System.currentTimeMillis())
                active = true
                publish()
            }.onFailure { android.util.Log.w("DiscordPresence", "Could not update presence", it) }
        }
    }

    /** Completion runs on the main thread after native shutdown has finished. */
    @JvmStatic @JvmOverloads fun stop(lease: Any? = null, onStopped: (() -> Unit)? = null) {
        executor.execute {
            try {
                if (lease == null || lease === owner) {
                    active = false
                    shutdown()
                    owner = null
                }
            }
            catch (e: Exception) { android.util.Log.w("DiscordPresence", "Could not stop presence", e) }
            finally { if (onStopped != null) main.post { onStopped() } }
        }
    }

    /** SDK availability, not a claim that Discord accepted an activity. */
    @JvmStatic fun isConfigured(): Boolean = DiscordSocialBridge.available()
    @JvmStatic fun isConnected(): Boolean = active && initializedApplicationId != null && DiscordSocialBridge.lastResultOk()
    @JvmStatic fun updatePresence() {
        executor.execute {
            if (active) {
                Settings.refreshSettings()
                publish()
            }
        }
    }

    // ── Activity model ──────────────────────────────────────────────────────────────

    private fun publish() {
        val version = session.version
        val since = session.startedAt

        val name = renderText(AllSettings.discordRpcName.getValue(), version).ifEmpty { "Turtle Launcher" }
        val details = if (version == null) "Browsing Turtle Launcher" else renderText(AllSettings.discordRpcDetails.getValue(), version)
        val state = renderText(AllSettings.discordRpcState.getValue(), version)
        val activityType = AllSettings.discordRpcType.getValue().trim().toIntOrNull()?.takeIf { it in VALID_TYPES } ?: 0
        val startTimestampMs = if (AllSettings.discordRpcShowElapsed.getValue()) {
            since.takeIf { it > 0 } ?: System.currentTimeMillis()
        } else {
            null
        }

        val buttonPairs = mutableListOf<Pair<String, String>>()
        listOf(
            AllSettings.discordRpcButton1.getValue() to AllSettings.discordRpcButton1Url.getValue(),
            AllSettings.discordRpcButton2.getValue() to AllSettings.discordRpcButton2Url.getValue(),
        ).forEach { (label, url) ->
            val l = DiscordPresencePayload.text(label, MAX_BUTTON_LABEL, 1)
            val u = DiscordPresencePayload.buttonUrl(url)
            if (l.isNotEmpty() && u != null) buttonPairs.add(l to u)
        }

        DiscordSocialBridge.updatePresence(
            name = name.take(MAX_TEXT_LEN),
            details = DiscordPresencePayload.text(details, MAX_STATE_DETAILS_LEN),
            state = DiscordPresencePayload.text(state, MAX_STATE_DETAILS_LEN),
            activityType = activityType,
            startTimestampMs = startTimestampMs,
            largeImage = AllSettings.discordRpcLargeImage.getValue().trim().take(MAX_IMAGE_KEY_LEN),
            largeText = DiscordPresencePayload.text(AllSettings.discordRpcLargeText.getValue(), MAX_TEXT_LEN),
            smallImage = AllSettings.discordRpcSmallImage.getValue().trim().take(MAX_IMAGE_KEY_LEN),
            smallText = DiscordPresencePayload.text(AllSettings.discordRpcSmallText.getValue(), MAX_TEXT_LEN),
            buttonLabels = buttonPairs.map { it.first }.toTypedArray(),
            buttonUrls = buttonPairs.map { it.second }.toTypedArray(),
        )
    }

    /** Renders the running version and the browsing/playing activity placeholders. */
    private fun renderText(template: String, version: String?): String {
        val safeVersion = version?.ifBlank { "Minecraft" } ?: "Minecraft"
        return template.replace("{version}", safeVersion)
            .replace("{activity}", if (version == null) "Browsing Turtle Launcher" else "Playing Minecraft")
            .trim()
    }
}
