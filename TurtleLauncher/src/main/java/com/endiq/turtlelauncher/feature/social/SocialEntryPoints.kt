package com.endiq.turtlelauncher.feature.social

import android.net.Uri
import android.os.Handler
import android.os.Looper

/**
 * Java-facing entry points for the two intents that land on `LauncherActivity`:
 *
 *  - `turtlelauncher://discord-callback?...` - the OAuth2 redirect from Discord's consent page
 *    (public client + PKCE, see [DiscordOAuthClient]);
 *  - the Friends notification's `EXTRA_OPEN_FRIENDS` (see [SocialNotifications]).
 *
 * The activity only needs to recognise the intent and open the Friends screen; everything else
 * (state validation, token exchange, snapshot refresh) stays in Kotlin here. `Result` is a value
 * class, which is awkward to consume from Java, so the callback carries a plain boolean plus an
 * error message instead.
 */
object SocialEntryPoints {

    /** Java-friendly callback: runs on the main thread. [error] is null on success. */
    fun interface RedirectCallback {
        fun onResult(success: Boolean, error: String?)
    }

    /**
     * True when [uri] is the Discord redirect this build registered - the configured redirect
     * URI is compared, not a hard-coded one, so a custom redirect in Settings still works as
     * long as the user registered the same value in the Developer Portal.
     */
    fun isDiscordRedirect(uri: Uri?): Boolean {
        if (uri == null) return false
        val expected = runCatching { Uri.parse(DiscordOAuthClient.redirectUri()) }.getOrNull() ?: return false
        return expected.scheme.equals(uri.scheme, ignoreCase = true) &&
            expected.host.equals(uri.host, ignoreCase = true) &&
            expected.path == uri.path
    }

    /**
     * Completes the PKCE exchange off the main thread, then refreshes the social snapshot so the
     * Friends screen is already populated when it opens. [callback] runs on the main thread.
     */
    fun completeDiscordRedirect(uri: Uri, callback: RedirectCallback) {
        Thread({
            val outcome = runCatching { DiscordOAuthClient.handleRedirect(uri) }
                .getOrElse { Result.failure<SocialIdentity>(it) }
            if (outcome.isSuccess) {
                SocialRepository.refresh()
            }
            val error = outcome.exceptionOrNull()
            val message = error?.message?.takeIf { it.isNotBlank() } ?: error?.javaClass?.simpleName
            Handler(Looper.getMainLooper()).post { callback.onResult(outcome.isSuccess, message) }
        }, "DiscordOAuthRedirect").apply { isDaemon = true }.start()
    }
}
