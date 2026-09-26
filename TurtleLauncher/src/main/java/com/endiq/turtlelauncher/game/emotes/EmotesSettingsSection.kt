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

package com.endiq.turtlelauncher.game.emotes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.game.version.installed.Version
import com.endiq.turtlelauncher.game.version.installed.VersionsManager
import com.endiq.turtlelauncher.ui.screens.content.settings.layouts.CardPosition
import com.endiq.turtlelauncher.ui.screens.content.settings.layouts.SettingsCard
import com.endiq.turtlelauncher.ui.screens.content.settings.layouts.SettingsCardColumn
import kotlinx.coroutines.launch

/**
 * Settings card that opens the Emotes (Emotecraft) installation dialog.
 */
@Composable
fun EmotesSettingsSection(
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    SettingsCardColumn(modifier = modifier.fillMaxWidth()) {
        SettingsCard(
            position = CardPosition.Single,
            title = stringResource(R.string.emotes_title),
            summary = stringResource(R.string.emotes_summary),
            onClick = { showDialog = true }
        )
    }

    if (showDialog) {
        EmotesDialog(onDismiss = { showDialog = false })
    }
}

sealed interface EmotesState {
    data object Loading : EmotesState
    data class Failed(val message: String) : EmotesState
    data class Ready(val versions: List<EmotecraftVersion>) : EmotesState
}

@Composable
fun EmotesDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val installedVersions by VersionsManager.versions.collectAsState()
    var selectedVersion by remember { mutableStateOf<Version?>(null) }

    var state by remember { mutableStateOf<EmotesState>(EmotesState.Loading) }
    var busy by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        state = try {
            EmotesState.Ready(EmotesManager.fetchVersions())
        } catch (e: Exception) {
            EmotesState.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    //resolve the best matching Emotecraft build for the picked instance
    val loaderSlug = selectedVersion?.getVersionInfo()?.loaderInfo?.let {
        EmotesManager.modrinthLoaderSlug(it.loader)
    }
    val mcVersion = selectedVersion?.getVersionInfo()?.minecraftVersion
    val pickedBuild: EmotecraftVersion? = (state as? EmotesState.Ready)?.let { ready ->
        selectedVersion?.let { EmotesManager.pickBestVersion(ready.versions, mcVersion, loaderSlug) }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.emotes_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (val s = state) {
                    EmotesState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Text(text = stringResource(R.string.emotes_loading))
                        }
                    }
                    is EmotesState.Failed -> {
                        Text(
                            text = stringResource(R.string.emotes_load_failed, s.message),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is EmotesState.Ready -> {
                        Text(
                            text = stringResource(R.string.emotes_pick_instance),
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (installedVersions.isEmpty()) {
                            Text(text = stringResource(R.string.emotes_no_instances))
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                            ) {
                                items(installedVersions) { version ->
                                    val selected = selectedVersion == version
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !busy) {
                                                selectedVersion = version
                                                resultText = null
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = null,
                                            enabled = !busy
                                        )
                                        Text(text = version.getVersionName())
                                    }
                                }
                            }
                        }

                        selectedVersion?.let { version ->
                            when {
                                loaderSlug == null -> Text(
                                    text = stringResource(R.string.emotes_no_loader_hint),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                pickedBuild == null -> Text(
                                    text = stringResource(
                                        R.string.emotes_no_match,
                                        mcVersion ?: "?",
                                        loaderSlug
                                    ),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall
                                )
                                else -> Text(
                                    text = stringResource(
                                        R.string.emotes_match,
                                        pickedBuild.displayName,
                                        mcVersion ?: "?"
                                    ),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        if (busy) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                Text(text = stringResource(R.string.emotes_installing))
                            }
                        }

                        resultText?.let { Text(text = it) }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && pickedBuild != null && selectedVersion != null,
                onClick = {
                    val version = selectedVersion ?: return@Button
                    val build = pickedBuild ?: return@Button
                    val doneTextTemplate = context.getString(R.string.emotes_install_done)
                    val failedTextTemplate = context.getString(R.string.emotes_install_failed)
                    busy = true
                    scope.launch {
                        resultText = try {
                            EmotesManager.installInto(version, build)
                            String.format(doneTextTemplate, build.versionNumber, version.getVersionName())
                        } catch (e: Exception) {
                            String.format(failedTextTemplate, e.message ?: e.javaClass.simpleName)
                        }
                        busy = false
                    }
                }
            ) {
                Text(text = stringResource(R.string.emotes_install))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss
            ) {
                Text(text = stringResource(R.string.generic_cancel))
            }
        }
    )
}
