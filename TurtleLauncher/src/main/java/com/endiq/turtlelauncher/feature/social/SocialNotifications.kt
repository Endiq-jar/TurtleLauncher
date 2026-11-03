package com.endiq.turtlelauncher.feature.social

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings
import net.endiq.launcher.LauncherActivity

/**
 * Notifications for incoming messages and friend requests.
 *
 * Deliberately low-key: one channel, one notification per conversation (so a chatty friend
 * updates their own notification instead of stacking), no sound when the preview is off, and
 * everything is a no-op when the user turned social notifications off or the OS permission
 * was refused. Notifications are only raised for messages the user is not currently reading -
 * [SocialRepository] decides that, not this class.
 */
object SocialNotifications {
    private const val TAG = "SocialNotifications"
    private const val CHANNEL_ID = "turtle_social"
    private const val REQUEST_NOTIFICATION_ID = 871001

    fun ensureChannel(context: Context) {
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.social_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.social_notification_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }.onFailure { e -> Logging.w(TAG, "Could not create the social notification channel", e) }
    }

    fun notifyMessage(context: Context, conversationId: String, title: String, body: String) {
        if (!enabled()) return
        ensureChannel(context)
        val preview = AllSettings.socialNotificationPreview.getValue()
        val content = if (preview) body.take(140) else context.getString(R.string.social_notification_new_message)
        post(
            context,
            id = conversationId.hashCode(),
            title = title,
            body = content,
        )
    }

    fun notifyFriendRequest(context: Context, name: String) {
        if (!enabled()) return
        ensureChannel(context)
        post(
            context,
            id = REQUEST_NOTIFICATION_ID,
            title = context.getString(R.string.social_notification_request_title),
            body = context.getString(R.string.social_notification_request_body, name),
        )
    }

    fun clear(context: Context, conversationId: String) {
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(conversationId.hashCode())
        }
    }

    fun clearAll(context: Context) {
        runCatching {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancelAll()
        }
    }

    private fun enabled(): Boolean = AllSettings.socialNotificationsEnabled.getValue()

    private fun post(context: Context, id: Int, title: String, body: String) {
        runCatching {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_friends_network)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openFriendsIntent(context))
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(id, notification)
        }.onFailure { e -> Logging.w(TAG, "Could not post a social notification", e) }
    }

    private fun openFriendsIntent(context: Context): PendingIntent? = runCatching {
        val intent = Intent(context, LauncherActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_OPEN_FRIENDS, true)
        }
        PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }.getOrNull()

    /** Consumed by LauncherActivity to jump straight to the Friends screen from a notification. */
    const val EXTRA_OPEN_FRIENDS = "com.endiq.turtlelauncher.extra.OPEN_FRIENDS"
}
