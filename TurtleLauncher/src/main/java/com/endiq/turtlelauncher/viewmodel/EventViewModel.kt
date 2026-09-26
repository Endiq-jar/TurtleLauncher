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

import android.net.Uri
import android.view.KeyEvent
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.endiq.turtlelauncher.game.version.installed.Version
import com.endiq.turtlelauncher.ui.AndroidStringText
import com.endiq.turtlelauncher.ui.control.input.TextInputMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

class EventViewModel : ViewModel() {
    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    /**
     * Posts an event
     */
    fun sendEvent(event: Event) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }

    sealed interface Event {
        sealed interface Key : Event {
            /** Tell MainActivity to start key capture */
            data object StartKeyCapture : Key
            /** Tell MainActivity to stop key capture */
            data object StopKeyCapture : Key
            /** Key capture result posted by MainActivity */
            data class OnKeyDown(val key: KeyEvent) : Key
        }
        sealed interface Game : Event {
            /** Disable/enable VMActivity key handling */
            data class KeyHandle(val handle: Boolean): Game
            /** Summon the IME */
            data class SwitchIme(val mode: TextInputMode?) : Game
            /** Refresh the game surface resolution */
            data object RefreshSize : Game
            /** The user pressed the system back key */
            data object OnBack : Game
            /** [com.endiq.turtlelauncher.game.launch.handler.AbstractHandler.onResume] */
            data object OnResume: Game
        }
        sealed interface Terracotta : Event {
            /** Request VPN permission */
            data object RequestVPN : Terracotta
            /** Update the VPN status text */
            data class VPNUpdateState(val stringRes: Int): Terracotta
            /** Shut the VPN down */
            data object StopVPN : Terracotta
        }
        /** Game-launch-related events */
        sealed interface Launch : Event {
            /** Main-menu game launch */
            data class Game(val version: Version?) : Launch
            /** Quick-launch the game into a server */
            data class PlayServer(val version: Version, val address: String): Launch
            /** Quick-launch the game into a save */
            data class PlaySave(val version: Version, val saveName: String): Launch
        }
        /** Check for updates */
        data object CheckUpdate : Event
        /** Open a link in the browser */
        data class OpenLink(val url: String) : Event
        /** Make MainActivity keep the screen on */
        data class KeepScreen(val on: Boolean) : Event
        /** Imports a control layout */
        data class ImportControls(val uris: List<Uri>) : Event
        /** Open the plugin download link window */
        data class DownloadPlugins(val link: Links): Event {
            data class Links(
                val github: String,
                val cloudDrives: List<CloudDrive> = emptyList()
            )
            /**
             * Netdisk links, keyed by language
             * @param language the language tag
             * @param link the netdisk link
             */
            data class CloudDrive(
                val language: String,
                val link: String
            )
        }
        /** Share the game log */
        sealed interface LogShare : Event {
            data class ShareGameLog(val logFile: File) : LogShare
        }
        /** Device Vulkan check */
        data class VulkanCheck(val version: Version): Event

        /** Show a Toast inside MainActivity */
        data class ShowToast(
            val text: AndroidStringText,
            val duration: Int = Toast.LENGTH_SHORT
        ) : Event

        /** Open the file manager */
        data class OpenFileManager(
            val rootPath: String,
            val currentPath: String? = null,
        ) : Event
    }
}

fun EventViewModel.sendKeepScreen(
    on: Boolean
) {
    sendEvent(EventViewModel.Event.KeepScreen(on))
}

fun EventViewModel.sendToast(
    text: AndroidStringText,
    duration: Int = Toast.LENGTH_SHORT,
) {
    sendEvent(EventViewModel.Event.ShowToast(text, duration))
}

fun EventViewModel.sendDLPlugin(
    githubLink: String,
    cloudDrives: List<EventViewModel.Event.DownloadPlugins.CloudDrive> = emptyList()
) {
    sendEvent(
        EventViewModel.Event.DownloadPlugins(
            link = EventViewModel.Event.DownloadPlugins.Links(
                github = githubLink,
                cloudDrives = cloudDrives
            )
        )
    )
}