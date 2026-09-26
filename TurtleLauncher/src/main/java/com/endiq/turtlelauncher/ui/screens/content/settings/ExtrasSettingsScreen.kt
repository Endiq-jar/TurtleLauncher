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

package com.endiq.turtlelauncher.ui.screens.content.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.game.emotes.EmotesSettingsSection
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.shizuku.ShizukuSettingsSection
import com.endiq.turtlelauncher.ui.base.BaseScreen
import com.endiq.turtlelauncher.ui.components.AnimatedColumn
import com.endiq.turtlelauncher.ui.components.verticalScrollWithBar
import com.endiq.turtlelauncher.ui.screens.NestedNavKey
import com.endiq.turtlelauncher.ui.screens.NormalNavKey
import com.endiq.turtlelauncher.ui.screens.TitledNavKey
import com.endiq.turtlelauncher.ui.screens.content.settings.layouts.CardPosition
import com.endiq.turtlelauncher.ui.screens.content.settings.layouts.SettingsCardColumn
import com.endiq.turtlelauncher.ui.screens.content.settings.layouts.SwitchSettingsCard
import com.endiq.turtlelauncher.viewmodel.EventViewModel

/**
 * Dedicated page for the newer accessory features:
 * the built-in screen recorder, Emotes (Emotecraft) and Shizuku.
 */
@Composable
fun ExtrasSettingsScreen(
    key: NestedNavKey.Settings,
    settingsScreenKey: TitledNavKey?,
    mainScreenKey: TitledNavKey?,
    eventViewModel: EventViewModel
) {
    BaseScreen(
        Triple(key, mainScreenKey, false),
        Triple(NormalNavKey.Settings.Extras, settingsScreenKey, false)
    ) { isVisible ->
        AnimatedColumn(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScrollWithBar(state = rememberScrollState())
                .padding(all = 12.dp),
            isVisible = isVisible
        ) { scope ->
            //Built-in screen recorder options
            AnimatedItem(scope) { yOffset ->
                SettingsCardColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = yOffset.roundToPx()) }
                ) {
                    SwitchSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Top,
                        unit = AllSettings.screenRecorder,
                        title = stringResource(R.string.settings_screen_recorder_title),
                        summary = stringResource(R.string.settings_screen_recorder_summary)
                    )

                    SwitchSettingsCard(
                        modifier = Modifier.fillMaxWidth(),
                        position = CardPosition.Bottom,
                        unit = AllSettings.recorderHideControls,
                        title = stringResource(R.string.settings_recorder_hide_controls_title),
                        summary = stringResource(R.string.settings_recorder_hide_controls_summary)
                    )
                }
            }

            //Emotes (Emotecraft mod integration)
            AnimatedItem(scope) { yOffset ->
                EmotesSettingsSection(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = yOffset.roundToPx()) }
                )
            }

            //Shizuku integration (ADB-level file access)
            AnimatedItem(scope) { yOffset ->
                ShizukuSettingsSection(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(x = 0, y = yOffset.roundToPx()) }
                )
            }
        }
    }
}
