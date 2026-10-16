package com.endiq.turtlelauncher.feature.discord

import com.endiq.turtlelauncher.setting.AllSettings

/**
 * Discord Rich Presence for the launcher, via the official Discord Social SDK's
 * token-free RPC (`Client::SetApplicationId` + `Client::UpdateRichPresence`, no
 * `Client::Connect()` and no Gateway identify). See:
 * https://docs.discord.com/developers/discord-social-sdk/development-guides/setting-rich-presence#rich-presence-without-authentication
 *
 * This does NOT drive the user's Discord account directly over the Gateway with a
 * stored session token ("self-botting"), which Discord's Terms of Service prohibit.
 * It only reaches the Discord app installed and signed in on the device, through the
 * SDK's native Android RPC channel (Social SDK 1.10+, released August 2026). No
 * Discord credential is read, stored, or transmitted by this class.
 *
 * Presence is always-on while the launcher or Minecraft is in the foreground - see
 * DiscordPresenceService / DiscordPresenceLifecycle, which call start()/stop() as the
 * active presence owner changes and give game sessions priority over launcher browsing.
 * If Discord isn't installed or isn't signed in on the device, updates are silently
 * dropped by the native bridge; nothing here crashes or blocks on that.
 */
object DiscordRpcManager {

    /** Turtle Launcher's registered Discord application. A settings override can replace this. */
    private const val DEFAULT_APPLICATION_ID = 1560576927419338863L

    private const val MAX_TEXT_LEN = 128
    private const val MAX_STATE_DETAILS_LEN = 256
    private const val MAX_BUTTON_LABEL = 32
    private const val MAX_BUTTON_URL = 512
    private const val MAX_IMAGE_KEY_LEN = 300

    private val VALID_TYPES = setOf(0, 1, 2, 3, 5)

    private val lock = Any()
    private var initializedApplicationId: Long? = null
    private var currentVersion: String? = null
    private var startedAt = 0L

    /** Resolves the configured application id, falling back to the launcher's own. */
    private fun applicationId(): Long =
        AllSettings.discordRpcApplicationId.getValue().trim()
            .takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?.toLongOrNull()
            ?: DEFAULT_APPLICATION_ID

    /** Brings the native bridge up for the currently configured application id. */
    private fun ensureInitializedLocked(): Boolean {
        if (!DiscordSocialBridge.available()) return false
        val wantedId = applicationId()
        if (initializedApplicationId == wantedId) return true
        // The application id is fixed for the lifetime of the native client - if it
        // changed (the user edited the override), restart the bridge under the new id.
        if (initializedApplicationId != null) DiscordSocialBridge.shutdown()
        val ok = DiscordSocialBridge.init(wantedId)
        initializedApplicationId = if (ok) wantedId else null
        return ok
    }

    /**
     * Publishes (or refreshes) presence for [versionName]: the running Minecraft version,
     * or null for "browsing the launcher". Called whenever the launcher or a game session
     * becomes the active presence owner - see DiscordPresenceService.
     */
    @JvmStatic
    @JvmOverloads
    fun start(versionName: String? = null) {
        val ready = synchronized(lock) {
            if (currentVersion != versionName || startedAt == 0L) startedAt = System.currentTimeMillis()
            currentVersion = versionName
            ensureInitializedLocked()
        }
        if (ready) publish()
    }

    /** Clears the activity. Safe to call when not running. */
    @JvmStatic
    fun stop() {
        val wasInitialized = synchronized(lock) {
            currentVersion = null
            startedAt = 0L
            initializedApplicationId != null
        }
        if (wasInitialized) DiscordSocialBridge.clear()
    }

    /** True once the native Social SDK bridge has loaded for this device/build. */
    @JvmStatic
    fun isConfigured(): Boolean = DiscordSocialBridge.available()

    /** Best-effort: true once the last presence update actually reached the Discord app. */
    @JvmStatic
    fun isConnected(): Boolean = synchronized(lock) { initializedApplicationId != null } && DiscordSocialBridge.lastResultOk()

    /** Re-publishes with the current version after the user edits customization. */
    @JvmStatic
    fun updatePresence() {
        val ready = synchronized(lock) { initializedApplicationId != null }
        if (ready) publish()
    }

    // ── Activity model ──────────────────────────────────────────────────────────────

    private fun publish() {
        val version = synchronized(lock) { currentVersion }
        val since = synchronized(lock) { startedAt }

        val name = renderText(AllSettings.discordRpcName.getValue(), version).ifEmpty { "Turtle Launcher" }
        val details = if (version == null) "Browsing Turtle Launcher" else renderText(AllSettings.discordRpcDetails.getValue(), version)
        val state = renderText(AllSettings.discordRpcState.getValue(), version)
        val activityType = AllSettings.discordRpcType.getValue().trim().toIntOrNull()?.takeIf { it in VALID_TYPES } ?: 0
        val startTimestampSec = if (AllSettings.discordRpcShowElapsed.getValue()) {
            (since.takeIf { it > 0 } ?: System.currentTimeMillis()) / 1000
        } else {
            null
        }

        val buttonPairs = mutableListOf<Pair<String, String>>()
        listOf(
            AllSettings.discordRpcButton1.getValue() to AllSettings.discordRpcButton1Url.getValue(),
            AllSettings.discordRpcButton2.getValue() to AllSettings.discordRpcButton2Url.getValue(),
        ).forEach { (label, url) ->
            val l = label.trim()
            val u = url.trim()
            if (l.isNotEmpty() && u.isNotEmpty()) buttonPairs.add(l.take(MAX_BUTTON_LABEL) to u.take(MAX_BUTTON_URL))
        }

        DiscordSocialBridge.updatePresence(
            name = name.take(MAX_TEXT_LEN),
            details = details.take(MAX_STATE_DETAILS_LEN),
            state = state.take(MAX_STATE_DETAILS_LEN),
            activityType = activityType,
            startTimestampSec = startTimestampSec,
            largeImage = AllSettings.discordRpcLargeImage.getValue().trim().take(MAX_IMAGE_KEY_LEN),
            largeText = AllSettings.discordRpcLargeText.getValue().trim().take(MAX_TEXT_LEN),
            smallImage = AllSettings.discordRpcSmallImage.getValue().trim().take(MAX_IMAGE_KEY_LEN),
            smallText = AllSettings.discordRpcSmallText.getValue().trim().take(MAX_TEXT_LEN),
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
