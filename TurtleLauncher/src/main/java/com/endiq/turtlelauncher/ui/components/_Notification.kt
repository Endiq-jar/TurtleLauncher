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

package com.endiq.turtlelauncher.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.notification.NotificationManager

/**
 * Notification permission check
 * @param onGranted the user granted the permission
 * @param onIgnore the user dismissed (denied) the request
 * @param onDismiss the user closed the permission dialog
 */
@Composable
fun NotificationCheck(
    title: String = stringResource(R.string.notification_title),
    text: String,
    onGranted: () -> Unit = {},
    onIgnore: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current

    val requestPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                onGranted()
            } else {
                onIgnore()
            }
        }

    SimpleAlertDialog(
        title = title,
        text = { Text(text = text) },
        confirmText = stringResource(R.string.notification_request),
        dismissText = stringResource(R.string.generic_ignore),
        onConfirm = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                //Below 13: jump to settings so the user can enable notifications
                NotificationManager.openNotificationSettings(context)
                onDismiss()
            } else {
                //Android 13+ can show the permission prompt directly
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onCancel = {
            onIgnore()
        },
        onDismissRequest = {
            onDismiss()
        }
    )
}