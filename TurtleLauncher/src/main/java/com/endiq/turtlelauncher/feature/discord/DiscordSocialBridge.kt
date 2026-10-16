package com.endiq.turtlelauncher.feature.discord

import android.util.Log

/**
 * Thin JNI wrapper around the Discord Social SDK's token-free Rich Presence RPC
 * (`Client::SetApplicationId` + `Client::UpdateRichPresence`, no `Client::Connect()`).
 *
 * No Discord account token, OAuth session, or Gateway connection is involved here -
 * this only reaches the Discord app installed and signed in on the same device, over
 * the SDK's native Android RPC channel (Social SDK 1.10+). All calls are fire-and-forget;
 * the native side (discord_rpc_bridge.cpp) owns a dedicated thread that pumps
 * `discordpp::RunCallbacks()`, so nothing here blocks the caller.
 */
internal object DiscordSocialBridge {
    private const val TAG = "Turtle-DiscordSocial"

    @Volatile private var libraryLoaded = false

    init {
        libraryLoaded = runCatching { System.loadLibrary("turtlediscordrpc") }
            .onFailure { Log.w(TAG, "Discord Social SDK native bridge unavailable: ${it.message}") }
            .isSuccess
    }

    /** True once the native library backing this bridge has loaded. */
    fun available(): Boolean = libraryLoaded

    /** Idempotent - safe to call repeatedly; only the first call for a given process spins up the client. */
    fun init(applicationId: Long): Boolean =
        libraryLoaded && runCatching { nativeInit(applicationId) }.getOrDefault(false)

    fun updatePresence(
        name: String,
        details: String,
        state: String,
        activityType: Int,
        startTimestampSec: Long?,
        largeImage: String,
        largeText: String,
        smallImage: String,
        smallText: String,
        buttonLabels: Array<String>,
        buttonUrls: Array<String>,
    ) {
        if (!libraryLoaded) return
        runCatching {
            nativeUpdatePresence(
                name, details, state, activityType,
                startTimestampSec != null, startTimestampSec ?: 0L,
                largeImage, largeText, smallImage, smallText,
                buttonLabels, buttonUrls,
            )
        }
    }

    fun clear() {
        if (libraryLoaded) runCatching { nativeClear() }
    }

    fun shutdown() {
        if (libraryLoaded) runCatching { nativeShutdown() }
    }

    /** Best-effort: true once the last UpdateRichPresence call reported success. */
    fun lastResultOk(): Boolean = libraryLoaded && runCatching { nativeLastResultOk() }.getOrDefault(false)

    @JvmStatic private external fun nativeInit(applicationId: Long): Boolean

    @JvmStatic private external fun nativeUpdatePresence(
        name: String,
        details: String,
        state: String,
        activityType: Int,
        hasTimestamp: Boolean,
        startTimestampSec: Long,
        largeImage: String,
        largeText: String,
        smallImage: String,
        smallText: String,
        buttonLabels: Array<String>,
        buttonUrls: Array<String>,
    )

    @JvmStatic private external fun nativeClear()

    @JvmStatic private external fun nativeShutdown()

    @JvmStatic private external fun nativeLastResultOk(): Boolean
}
