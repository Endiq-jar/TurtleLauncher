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

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.endiq.turtlelauncher.game.launch.GameLaunchFlow
import com.endiq.turtlelauncher.game.version.installed.Version
import com.endiq.turtlelauncher.game.version.installed.VersionsManager
import com.endiq.turtlelauncher.ui.screens.content.elements.LaunchGameOperation
import com.endiq.turtlelauncher.ui.screens.content.elements.QuickPlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class LaunchGameViewModel : ViewModel() {
    private val _launchFlow = MutableStateFlow<GameLaunchFlow?>(null)
    /**
     * Game launch flow
     */
    val launchFlow = _launchFlow.asStateFlow()

    private val _launchGameOperation = MutableStateFlow<LaunchGameOperation>(LaunchGameOperation.None)
    /**
     * Game launch operation state
     */
    val launchGameOperation = _launchGameOperation.asStateFlow()

    fun start(
        activity: Activity,
        version: Version,
        exitActivity: () -> Unit,
        submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
        quickPlay: QuickPlay?,
        skipAccountRefresh: Boolean
    ) {
        _launchFlow.update {
            GameLaunchFlow(viewModelScope).also {
                it.launch(
                    context = activity,
                    version = version,
                    exitActivity = exitActivity,
                    submitError = submitError,
                    onReloginRequired = { account ->
                        activity.runOnUiThread {
                            updateOperation(
                                LaunchGameOperation.AccountRelogin(account, version, quickPlay)
                            )
                        }
                        cancel()
                    },
                    onRefreshFailed = { account, th ->
                        activity.runOnUiThread {
                            updateOperation(
                                LaunchGameOperation.AccountRefreshFailed(account, th, version, quickPlay)
                            )
                        }
                        cancel()
                    },
                    onComplete = {
                        _launchFlow.update { null }
                    },
                    skipAccountRefresh = skipAccountRefresh
                )
            }
        }
    }

    fun cancel() {
        _launchFlow.value?.cancel()
        _launchFlow.update { null }
    }

    /**
     * Attempts to launch the game
     */
    fun tryLaunch(
        version: Version? = null
    ) {
        if (launchGameOperation.value == LaunchGameOperation.None && _launchFlow.value == null) {
            updateOperation(
                LaunchGameOperation.TryLaunch(
                    version ?: VersionsManager.currentVersion.value
                )
            )
        }
    }

    /**
     * Quick launch (playing a save straight from save management)
     * @param saveName the save's file name
     */
    fun quickPlaySave(
        version: Version,
        saveName: String
    ) {
        if (launchGameOperation.value == LaunchGameOperation.None && _launchFlow.value == null) {
            updateOperation(
                LaunchGameOperation.TryLaunch(
                    version = version,
                    quickPlay = QuickPlay.Save(saveName),
                )
            )
        }
    }

    /**
     * Attempts to quick-launch the game into a server
     * @param address the server address
     */
    fun tryPlayServer(address: String) {
        val version = VersionsManager.currentVersion.value ?: return
        quickPlayServer(version, address)
    }

    /**
     * Quick-joins a server from the server list
     * @param address the server address
     */
    fun quickPlayServer(
        version: Version,
        address: String
    ) {
        if (launchGameOperation.value == LaunchGameOperation.None && _launchFlow.value == null) {
            updateOperation(
                LaunchGameOperation.TryLaunch(
                    version = version,
                    quickPlay = QuickPlay.Server(address),
                )
            )
        }
    }

    fun updateOperation(operation: LaunchGameOperation) {
        this._launchGameOperation.update { operation }
    }
}