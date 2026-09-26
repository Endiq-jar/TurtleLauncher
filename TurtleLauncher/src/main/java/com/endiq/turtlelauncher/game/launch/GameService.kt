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

package com.endiq.turtlelauncher.game.launch

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.notification.NOTIFICATION_ID_GAME_SERVICE
import com.endiq.turtlelauncher.notification.NotificationChannelData
import com.endiq.turtlelauncher.utils.logging.Logger

private const val TAG = "GameService"

class GameService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //Ensure the service stays in the foreground
        startForegroundNotification()
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val data = NotificationChannelData.GAME_SERVICE_CHANNEL

        val notification: Notification = NotificationCompat.Builder(this, data.channelId)
            .setContentTitle(getString(R.string.notification_jvm_running_name))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID_GAME_SERVICE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID_GAME_SERVICE, notification)
            }
        } catch (e: Exception) {
            //If the app recedes during promotion, the system refuses foreground status and keep-alive becomes moot
            Logger.error(TAG, "Failed to start foreground notification", e)
        }
    }
}
