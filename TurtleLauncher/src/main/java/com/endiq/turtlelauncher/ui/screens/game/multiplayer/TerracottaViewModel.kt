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

package com.endiq.turtlelauncher.ui.screens.game.multiplayer

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.ActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.context.COPY_LABEL_TERRACOTTA_INVITE_CODE
import com.endiq.turtlelauncher.context.COPY_LABEL_TERRACOTTA_SERVER_ADDRESS
import com.endiq.turtlelauncher.game.launch.handler.GameHandler
import com.endiq.turtlelauncher.terracotta.Terracotta
import com.endiq.turtlelauncher.terracotta.TerracottaState
import com.endiq.turtlelauncher.terracotta.TerracottaVPNService
import com.endiq.turtlelauncher.terracotta.profile.TerracottaProfile
import com.endiq.turtlelauncher.utils.copyText
import com.endiq.turtlelauncher.viewmodel.EventViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class TerracottaViewModel(
    val gameHandler: GameHandler,
    val eventViewModel: EventViewModel,
    val getUserName: () -> String?
): ViewModel() {
    var operation by mutableStateOf<TerracottaOperation>(TerracottaOperation.None)

    /**
     * Multiplayer menu state
     */
    var dialogState by mutableStateOf<TerracottaState.Ready?>(null)

    /**
     * Multiplayer menu log display state
     */
    var dialogLogOperation by mutableStateOf<TerracottaLogOperation>(TerracottaLogOperation.None)
        private set

    /**
     * Terracotta core version; non-null once initialization completes
     */
    var terracottaVer by mutableStateOf<String?>(null)

    /**
     * EasyTier version; non-null once initialization completes
     */
    var easyTierVer by mutableStateOf<String?>(null)

    /**
     * VPN permission request, set by TerracottaOperation
     */
    var vpnLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>? = null

    /**
     * Whether interaction is allowed on the waiting page
     */
    var isWaitingInteractive by mutableStateOf(false)

    private val _profiles = MutableStateFlow<List<TerracottaProfile>>(emptyList())
    /**
     * Player list of the current Terracotta room
     */
    val profiles = _profiles.asStateFlow()

    private val allJobs: MutableList<Job> = mutableListOf()

    /**
     * Opens the Terracotta menu
     */
    fun openMenu() {
        if (operation !is TerracottaOperation.None) return

        if (!Terracotta.initialized) initialize()
        if (allJobs.isEmpty()) initJobs()

        operation = TerracottaOperation.ShowMenu
    }

    private val logMutex = Mutex()
    /**
     * Shows the current core's logs inside the menu
     */
    fun showLog() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = logMutex.withLock {
                if (dialogLogOperation is TerracottaLogOperation.CollectingLog) return@withLock null
                TerracottaLogOperation.CollectingLog
            }

            result ?: return@launch
            dialogLogOperation = result

            val finalState = Terracotta.collectLogs()?.let { logString ->
                TerracottaLogOperation.EnableLog(logString)
            } ?: TerracottaLogOperation.None //collection failed: fall back to normal state

            dialogLogOperation = finalState
        }
    }

    /**
     * Makes the menu leave log state
     */
    fun hideLog() {
        dialogLogOperation = TerracottaLogOperation.None
    }

    /**
     * Copies the room invite code to the clipboard
     */
    fun copyInviteCode(
        state: TerracottaState.HostOK
    ) {
        val code = state.code ?: return //never null in theory
        copyText(label = COPY_LABEL_TERRACOTTA_INVITE_CODE, text = code) {
            it.getString(R.string.terracotta_status_host_ok_code_copy_toast)
        }
    }

    /**
     * Copies the room fallback link to the clipboard
     */
    fun copyServerAddress(
        state: TerracottaState.GuestOK
    ) {
        val address = state.url ?: return
        copyText(label = COPY_LABEL_TERRACOTTA_SERVER_ADDRESS, text = address) {
            it.getString(R.string.terracotta_status_guest_ok_address_copy_toast)
        }
    }

    private fun copyText(
        label: String,
        text: String,
        toast: (Context) -> String
    ) {
        val context = gameHandler.activity
        copyText(
            label = label,
            text = text,
            context = context,
            showToast = false
        )
        Toast.makeText(context, toast(context), Toast.LENGTH_SHORT).show()
    }

    /**
     * Updates the current Terracotta player list
     */
    private fun updateProfiles(profiles: List<TerracottaProfile>?) {
        //Update only the player list here, avoiding constant dialogState churn and mass recompositions
        _profiles.update {
            profiles ?: emptyList()
        }
    }

    /**
     * Initializes Terracotta
     */
    private fun initialize() {
        Terracotta.initialize(viewModelScope, eventViewModel)
        Terracotta.setWaiting(true)

        val metadata = Terracotta.getMetadata()
        terracottaVer = metadata.terracottaVersion
        easyTierVer = metadata.easyTierVersion
    }

    private fun initJobs() {
        val activity = gameHandler.activity

        val stateChangeJob = viewModelScope.launch {
            Terracotta.stateChanges.collect { (old, new) ->
                when (new) {
                    is TerracottaState.Waiting -> {
                        if (old !is TerracottaState.Waiting) {
                            //Entering the waiting lobby, first time or again
                            isWaitingInteractive = true
                        }
                    }
                    is TerracottaState.HostOK -> {
                        if (old !is TerracottaState.HostOK) {
                            //Freshly entered this state: copy the invite code once by default
                            copyInviteCode(new)
                            //Then update the player list state for the first time
                            updateProfiles(new.profiles)
                        }
                        if (new.isForkOf(old)) {
                            updateProfiles(new.profiles)
                            return@collect
                        }
                    }
                    is TerracottaState.GuestOK -> {
                        if (old !is TerracottaState.GuestOK) {
                            updateProfiles(new.profiles)
                        }
                        if (new.isForkOf(old)) {
                            updateProfiles(new.profiles)
                            return@collect
                        }
                    }
                    else -> {
                        if (_profiles.value.isNotEmpty()) {
                            //No longer in a room: clear all player profiles
                            updateProfiles(emptyList())
                        }
                    }
                }
                dialogState = new
            }
        }

        val eventJob = viewModelScope.launch {
            eventViewModel.events
                .filterIsInstance<EventViewModel.Event.Terracotta>()
                .collect { event ->
                    when (event) {
                        is EventViewModel.Event.Terracotta.RequestVPN -> {
                            withContext(Dispatchers.Main) {
                                val intent = VpnService.prepare(activity)
                                if (intent != null) {
                                    vpnLauncher?.launch(intent)
                                } else {
                                    val vpnIntent = Intent(activity, TerracottaVPNService::class.java)
                                        .setAction(TerracottaVPNService.ACTION_START)
                                    activity.startForegroundService(vpnIntent)
                                }
                            }
                        }
                        is EventViewModel.Event.Terracotta.VPNUpdateState -> {
                            withContext(Dispatchers.Main) {
                                if (TerracottaVPNService.isRunning()) {
                                    //The service already runs in foreground: just post the status text;
                                    //when it isn't running, skip, never spawning via startForegroundService a service that can't reach foreground in time
                                    activity.startService(
                                        Intent(activity, TerracottaVPNService::class.java)
                                            .setAction(TerracottaVPNService.ACTION_UPDATE_STATE)
                                            .putExtra(TerracottaVPNService.EXTRA_STATE_TEXT, event.stringRes)
                                    )
                                }
                            }
                        }
                        is EventViewModel.Event.Terracotta.StopVPN -> {
                            withContext(Dispatchers.Main) {
                                val activity = gameHandler.activity
                                if (TerracottaVPNService.isRunning()) {
                                    //Stop via stopService only, avoiding the FGS start-timeout crash
                                    activity.stopService(
                                        Intent(activity, TerracottaVPNService::class.java)
                                    )
                                }
                            }
                        }
                    }
                }
        }

        allJobs.add(stateChangeJob)
        allJobs.add(eventJob)
    }

    override fun onCleared() {
        allJobs.forEach { it.cancel() }
        allJobs.clear()
    }
}

@Composable
fun rememberTerracottaViewModel(
    keyTag: String,
    gameHandler: GameHandler,
    eventViewModel: EventViewModel,
    getUserName: () -> String?
): TerracottaViewModel {
    return viewModel(
        key = keyTag
    ) {
        TerracottaViewModel(
            gameHandler = gameHandler,
            eventViewModel = eventViewModel,
            getUserName = getUserName
        )
    }
}