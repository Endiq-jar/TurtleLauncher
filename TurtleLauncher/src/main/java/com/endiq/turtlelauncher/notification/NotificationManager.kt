/*
 * Turtle Launcher
 * Copyright (C) 2025 Endiq and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.endiq.turtlelauncher.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationManager {
    /**
     * Initializes notifications and their channels
     */
    fun initManager(context: Context) {
        NotificationChannelData.entries.forEach { data ->
            createNotificationChannel(context, data)
        }
    }

    /**
     * Tries to check whether notification permission is on; below Android 13 this can't be 100% certain
     */
    fun checkNotificationEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            //May work on some heavily modded systems, but no guarantees
            //So below Android 13, best treat notifications as unavailable by default
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            //SDK 33+ has a unified spec, but some trash systems just can't be helped
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_DENIED
        }
    }

    fun createNotificationChannel(context: Context, channelData: NotificationChannelData) {
        val manager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelData.channelId, channelData.channelName(context), channelData.level).apply {
            channelData.channelDescription?.invoke(context)?.let { desc ->
                description = desc
            }
            setShowBadge(channelData.showBadge)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Jumps to the notification settings page, or only the app detail page on failure
     */
    fun openNotificationSettings(context: Context) {
        try {
            val intent = Intent()
            intent.action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, context.applicationInfo.uid)
            context.startActivity(intent)
        } catch (e: Exception) {
            //On failure, jump to the app's detail settings page
            val intent = Intent()
            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            intent.data = Uri.fromParts("package", context.packageName, null)
            context.startActivity(intent)
        }
    }
}