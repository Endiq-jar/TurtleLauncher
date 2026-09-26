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

package com.endiq.turtlelauncher.shizuku

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.screens.content.settings.layouts.CardPosition
import com.endiq.turtlelauncher.ui.screens.content.settings.layouts.SettingsCard
import com.endiq.turtlelauncher.ui.screens.content.settings.layouts.SettingsCardColumn
import com.endiq.turtlelauncher.ui.screens.content.settings.layouts.SwitchSettingsCard
import com.endiq.turtlelauncher.utils.hasStoragePermissions
import com.endiq.turtlelauncher.utils.network.openLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SHIZUKU_WEBSITE = "https://shizuku.rikka.app/"

/**
 * The full Shizuku settings section:
 * status card, permission flow, usage toggles and the
 * "grant all-files access via Shizuku" action.
 */
@Composable
fun ShizukuSettingsSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    SettingsCardColumn(modifier = modifier.fillMaxWidth()) {
        val installed = remember { ShizukuManager.isShizukuInstalled(context) }
        val alive = ShizukuManager.binderAlive
        val granted = ShizukuManager.permissionGranted
        val ready = ShizukuManager.serviceReady

        //Status card — tap to refresh
        SettingsCard(
            position = CardPosition.Top,
            title = stringResource(R.string.shizuku_status_title),
            summary = when {
                !installed -> stringResource(R.string.shizuku_status_not_installed)
                !alive -> stringResource(R.string.shizuku_status_not_running)
                !granted -> stringResource(R.string.shizuku_status_no_permission)
                ready -> stringResource(R.string.shizuku_status_ready)
                else -> stringResource(R.string.shizuku_status_ready)
            },
            onClick = { ShizukuManager.refreshState() }
        )

        //Install / permission prompts
        if (!installed) {
            SettingsCard(
                position = CardPosition.Middle,
                title = stringResource(R.string.shizuku_get_title),
                summary = stringResource(R.string.shizuku_get_summary),
                onClick = { activity?.openLink(SHIZUKU_WEBSITE) }
            )
        } else if (alive && !granted) {
            SettingsCard(
                position = CardPosition.Middle,
                title = stringResource(R.string.shizuku_request_permission_title),
                summary = stringResource(R.string.shizuku_request_permission_summary),
                onClick = { ShizukuManager.requestPermission() },
                enabled = ShizukuManager.canRequestPermission()
            )
        }

        //Usage toggle
        SwitchSettingsCard(
            position = CardPosition.Middle,
            unit = AllSettings.shizukuEnabled,
            title = stringResource(R.string.shizuku_enabled_title),
            summary = stringResource(R.string.shizuku_enabled_summary),
            enabled = granted
        )

        //Replace the manual storage permission flow with a one-tap Shizuku grant
        if (granted) {
            var allFilesGranted by remember { mutableStateOf(hasStoragePermissions(context)) }
            var grantMessage by remember { mutableStateOf<String?>(null) }
            val doneText = stringResource(R.string.shizuku_grant_all_files_done)
            val failedText = stringResource(R.string.shizuku_grant_all_files_failed)
            SettingsCard(
                position = CardPosition.Middle,
                title = stringResource(R.string.shizuku_grant_all_files_title),
                summary = grantMessage
                    ?: stringResource(R.string.shizuku_grant_all_files_summary),
                onClick = {
                    if (allFilesGranted) return@SettingsCard
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            ShizukuManager.grantAllFilesAccess(context)
                        }
                        allFilesGranted = ok
                        grantMessage = if (ok) doneText else failedText
                    }
                },
                enabled = !allFilesGranted
            )
        }

        //SAF vs Shizuku preference
        SwitchSettingsCard(
            position = CardPosition.Bottom,
            unit = AllSettings.shizukuPreferOverSaf,
            title = stringResource(R.string.shizuku_prefer_over_saf_title),
            summary = stringResource(R.string.shizuku_prefer_over_saf_summary),
            enabled = AllSettings.shizukuEnabled.state && granted
        )
    }
}
