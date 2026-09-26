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

package com.endiq.turtlelauncher.viewmodel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.endiq.turtlelauncher.BuildConfig
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.path.GLOBAL_CLIENT
import com.endiq.turtlelauncher.path.GLOBAL_JSON
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.components.MarqueeText
import com.endiq.turtlelauncher.ui.components.SimpleListDialog
import com.endiq.turtlelauncher.ui.screens.content.elements.DisabledAlpha
import com.endiq.turtlelauncher.ui.upgrade.UpgradeDialog
import com.endiq.turtlelauncher.ui.upgrade.UpgradeFilesDialog
import com.endiq.turtlelauncher.upgrade.GithubContentApi
import com.endiq.turtlelauncher.upgrade.RemoteData
import com.endiq.turtlelauncher.upgrade.TooFrequentOperationException
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.network.safeBodyAsJson
import com.endiq.turtlelauncher.utils.network.withRetry
import com.endiq.turtlelauncher.utils.string.decodeBase64
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "LauncherUpgradeVM"

sealed interface LauncherUpgradeOperation {
    data object None : LauncherUpgradeOperation
    /** A new launcher version was found; show the update information */
    data class Upgrade(val data: RemoteData) : LauncherUpgradeOperation
    /** Choose the installer package file to install */
    data class SelectApk(val data: RemoteData) : LauncherUpgradeOperation
    /** Open the cloud drive share */
    data class OpenCloudDrive(val cloudDrive: RemoteData.CloudDrive) : LauncherUpgradeOperation
}

/**
 * Source used to fetch the latest version information
 */
private const val LATEST_VERSION = "latest_version_md.json"
private const val LATEST_API_URL =
    "https://api.github.com/repos/Endiq-jar/TurtleLauncher/contents/$LATEST_VERSION"

/**
 * ViewModel that tracks launcher updates
 */
class LauncherUpgradeViewModel: ViewModel() {
    var operation by mutableStateOf<LauncherUpgradeOperation>(LauncherUpgradeOperation.None)

    private val checkMutex = Mutex()

    /**
     * Checks whether we are still within the rate-limit window
     * @param time the rate-limit window (milliseconds)
     * @param lastCheckTime timestamp of the previous check
     */
    private fun isWithinRateLimit(
        time: Long,
        lastCheckTime: Long
    ): Boolean {
        val currentTime = System.currentTimeMillis()
        if (lastCheckTime > currentTime) {
            //The user has moved the clock into the future, so the window cannot be determined
            //Allow the check directly
            return false
        }
        return currentTime - lastCheckTime < time
    }

    /**
     * Updates the timestamp of the last check
     */
    private fun updateLastCheckTime() {
        AllSettings.lastUpgradeCheck.save(System.currentTimeMillis())
    }

    /**
     * Runs all checks quickly at startup
     */
    fun checkOnAppStart(
        onIsLatest: suspend () -> Unit = {}
    ) {
        viewModelScope.launch {
            if (
                isWithinRateLimit(
                    time = TimeUnit.HOURS.toMillis(1L),
                    lastCheckTime = AllSettings.lastUpgradeCheck.getValue()
                )
            ) {
                Logger.info(TAG, "App start check: Within rate limit, skipping")
                return@launch
            }

            val data = fetchRemoteData()
            if (data != null) {
                checkForUpgrade(
                    data = data,
                    lastIgnored = AllSettings.lastIgnoredVersion.getValue(),
                    ignoreDismissedVersions = true, //the startup check ignores versions the user already dismissed
                    onUpgrade = { data ->
                        operation = LauncherUpgradeOperation.Upgrade(data)
                    },
                    onIsLatest = onIsLatest
                )
            }
            updateLastCheckTime()
        }
    }

    /**
     * The user manually taps "check for updates" in settings
     * @param onInProgress the update check is about to start
     * @param onIsLatest the launcher is already up to date
     */
    suspend fun checkManually(
        onInProgress: suspend () -> Unit = {},
        onIsLatest: suspend () -> Unit = {}
    ): Boolean {
        return checkMutex.withLock {
            if (
                isWithinRateLimit(
                    time = TimeUnit.SECONDS.toMillis(5L),
                    lastCheckTime = AllSettings.lastUpgradeCheck.getValue()
                )
            ) throw TooFrequentOperationException()

            onInProgress()

            val data = fetchRemoteData()
            if (data != null) {
                checkForUpgrade(
                    data = data,
                    lastIgnored = AllSettings.lastIgnoredVersion.getValue(),
                    ignoreDismissedVersions = false,
                    onUpgrade = { data ->
                        operation = LauncherUpgradeOperation.Upgrade(data)
                    },
                    onIsLatest = onIsLatest
                )
            }
            updateLastCheckTime()
            data != null
        }
    }

    /**
     * Fetches the latest launcher information from the remote source
     */
    private suspend fun fetchRemoteData(): RemoteData? {
        return withContext(Dispatchers.IO) {
            runCatching {
                withRetry(logTag = "LauncherUpgrade", maxRetries = 2) {
                    //Fetch the latest launcher information
                    val api = GLOBAL_CLIENT.get(LATEST_API_URL).safeBodyAsJson<GithubContentApi>()
                    //Requires Base64 decoding
                    val contentString = decodeBase64(api.content)
                    GLOBAL_JSON.decodeFromString(RemoteData.serializer(), contentString)
                }
            }.getOrElse { e ->
                Logger.warning(TAG, "Failed to check for launcher upgrade!", e)
                null
            }
        }
    }

    /**
     * Checks whether the launcher needs an update
     * @param lastIgnored the version the user dismissed the last time the update dialog appeared
     * @param ignoreDismissedVersions whether to ignore versions the user has dismissed
     * @param onUpgrade called when an update is found
     * @param onIsLatest called when the current version is the latest
     */
    private suspend fun checkForUpgrade(
        data: RemoteData,
        lastIgnored: Int?,
        ignoreDismissedVersions: Boolean,
        onUpgrade: suspend (RemoteData) -> Unit,
        onIsLatest: suspend () -> Unit = {}
    ) {
        val currentVersionCode = BuildConfig.VERSION_CODE
        if (currentVersionCode < data.code) {
            //The launcher is outdated
            when {
                ignoreDismissedVersions && lastIgnored == data.code -> {
                    //Dismiss this update
                    Logger.info(TAG, "Launcher update detected: $currentVersionCode -> ${data.code}, but ignored by user")
                }
                else -> {
                    //Show the update dialog
                    Logger.info(TAG, "Launcher update detected: $currentVersionCode -> ${data.code}, dialog shown to user")
                    onUpgrade(data)
                }
            }
        } else {
            Logger.info(TAG, "Launcher is running the latest version: $currentVersionCode")
            onIsLatest()
        }
    }
}

@Composable
fun LauncherUpgradeOperation(
    operation: LauncherUpgradeOperation,
    onChanged: (LauncherUpgradeOperation) -> Unit,
    onIgnoredClick: (code: Int) -> Unit,
    onLinkClick: (String) -> Unit
) {
    when (operation) {
        is LauncherUpgradeOperation.None -> {}
        is LauncherUpgradeOperation.Upgrade -> {
            UpgradeDialog(
                data = operation.data,
                onDismissRequest = {
                    onChanged(LauncherUpgradeOperation.None)
                },
                onFilesClick = {
                    onChanged(LauncherUpgradeOperation.SelectApk(operation.data))
                },
                onIgnored = {
                    onIgnoredClick(operation.data.code)
                },
                onLinkClick = onLinkClick,
                onCloudDriveClick = { cloudDrive ->
                    onChanged(LauncherUpgradeOperation.OpenCloudDrive(cloudDrive))
                }
            )
        }
        is LauncherUpgradeOperation.SelectApk -> {
            UpgradeFilesDialog(
                data = operation.data,
                onDismissRequest = {
                    onChanged(LauncherUpgradeOperation.None)
                },
                onFileSelected = { file ->
                    onLinkClick(file.uri)
                    onChanged(LauncherUpgradeOperation.None)
                }
            )
        }
        is LauncherUpgradeOperation.OpenCloudDrive -> {
            val current by remember(operation) {
                mutableStateOf<RemoteData.CloudDrive.Link?>(null)
            }
            SimpleListDialog(
                title = stringResource(R.string.upgrade_cloud_drive),
                items = operation.cloudDrive.links,
                onItemSelected = { link ->
                    onLinkClick(link.link)
                },
                onDismissRequest = {
                    onChanged(LauncherUpgradeOperation.None)
                },
                current = current,
                itemLayout = { item, isCurrent, onClick ->
                    CloudDriveLayout(
                        link = item,
                        selected = isCurrent,
                        onClick = onClick
                    )
                },
                showConfirm = true,
                confirmText = {
                    MarqueeText(text = stringResource(R.string.generic_confirm))
                }
            )
        }
    }
}


@Composable
private fun CloudDriveLayout(
    link: RemoteData.CloudDrive.Link,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(shape = MaterialTheme.shapes.large)
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = enabled
        )
        Column(
            modifier = Modifier.alpha(if (enabled) 1.0f else DisabledAlpha),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            //Cloud drive name
            MarqueeText(
                modifier = Modifier.fillMaxWidth(),
                text = link.name,
                style = MaterialTheme.typography.labelMedium
            )
            //Cloud drive link
            MarqueeText(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(0.7f),
                text = link.link,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}