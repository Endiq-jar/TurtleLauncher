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

package com.endiq.turtlelauncher.game.version.multiplayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.steveice10.opennbt.tag.builtin.ByteTag
import com.github.steveice10.opennbt.tag.builtin.CompoundTag
import com.github.steveice10.opennbt.tag.builtin.StringTag
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.network.ServerAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

private const val TAG = "ServerData"

/**
 * Core data class of a Minecraft server
 * @param name the player-defined server name
 * @param originIp the raw server IP the player entered
 * @param texturePackStatus the server's texture pack status
 * @param acceptedCodeOfConduct whether the server code of conduct was accepted
 * @param icon the server icon saved locally
 */
data class ServerData(
    var name: String,
    var originIp: String,
    var texturePackStatus: TexturePackStatus = TexturePackStatus.PROMPT,
    var acceptedCodeOfConduct: Boolean? = null,
    var icon: ByteArray? = null
) {
    enum class TexturePackStatus(
        val storageCode: Int?
    ) {
        ENABLED(1),
        DISABLED(0),
        /** Prompt the user to enable texture packs */
        PROMPT(null)
    }

    sealed interface Operation {
        data object Loading : Operation
        /** Server loaded successfully */
        data class Loaded(val result: ServerPingResult) : Operation
        /** Cannot connect to the server */
        data object Failed : Operation
    }

    var refreshUI by mutableIntStateOf(0)
        private set

    var operation by mutableStateOf<Operation>(Operation.Loading)
        private set

    /**
     * Tries to ping this server
     * @param requestSave requests saving the whole server list
     */
    suspend fun load(
        requestSave: (reason: String) -> Unit = {}
    ) {
        withContext(Dispatchers.Main) {
            operation = Operation.Loading
        }

        runCatching {
            val ip = ServerAddress.parse(originIp)

            val resolvedAddress = ip.resolve()
            val result = pingServer(resolvedAddress)

            val icon0 = result.status.favicon?.icon
            //Check whether the server-returned icon differs from the locally saved one
            val isDifferentIcon = icon0 != null && !icon0.contentEquals(icon)
            //If so, apply the new icon and request a save
            if (isDifferentIcon) {
                icon = icon0
                withContext(Dispatchers.Main) {
                    refreshUI++
                }
                requestSave("save new icon")
            }

            withContext(Dispatchers.Main) {
                operation = Operation.Loaded(result)
            }
        }.onFailure {
            Logger.warning(TAG, "Unable to load/connect to server: $originIp", it)
            withContext(Dispatchers.Main) {
                operation = Operation.Failed
            }
        }
    }

    fun save(): CompoundTag {
        return CompoundTag("").apply {
            put(StringTag("name", this@ServerData.name))
            put(StringTag("ip", this@ServerData.originIp))

            this@ServerData.icon?.let { bytes ->
                Base64.getEncoder().encodeToString(bytes)
            }?.let { iconString ->
                put(StringTag("icon", iconString))
            }

            this@ServerData.texturePackStatus.storageCode?.let { acceptTextures ->
                put(ByteTag("acceptTextures", acceptTextures.toByte()))
            }

            if (this@ServerData.acceptedCodeOfConduct == true) {
                put(ByteTag("acceptedCodeOfConduct", 1))
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ServerData

        if (acceptedCodeOfConduct != other.acceptedCodeOfConduct) return false
        if (name != other.name) return false
        if (originIp != other.originIp) return false
        if (texturePackStatus != other.texturePackStatus) return false
        if (!icon.contentEquals(other.icon)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = acceptedCodeOfConduct?.hashCode() ?: 0
        result = 31 * result + name.hashCode()
        result = 31 * result + originIp.hashCode()
        result = 31 * result + texturePackStatus.hashCode()
        result = 31 * result + (icon?.contentHashCode() ?: 0)
        return result
    }
}