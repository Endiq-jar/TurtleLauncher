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

package com.endiq.turtlelauncher.game.version.installed

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import com.endiq.turtlelauncher.utils.GSON
import com.endiq.turtlelauncher.utils.logging.Logger
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "CurrentGameInfo"

/**
 * Current game state info (supports legacy config migration)
 * @property version currently selected version name
 * @property favoritesMap favorites map (favorites name, contained versions)
 */
@Keep
data class CurrentGameInfo(
    @SerializedName("version")
    var version: String = "",
    @SerializedName("favoritesInfo")
    val favoritesMap: MutableMap<String, MutableSet<String>> = ConcurrentHashMap()
) {
    /**
     * Atomically saves the current state to file
     * @param gameHome the game directory this info belongs to
     */
    fun saveCurrentInfo(gameHome: String) {
        val infoFile = getInfoFile(gameHome)
        runCatching {
            FileUtils.writeByteArrayToFile(infoFile, GSON.toJson(this).toByteArray(Charsets.UTF_8))
            Logger.debug(TAG, "Current version $version has been saved to the config file.")
        }.onFailure { e ->
            Logger.error(TAG, "Save failed: ${infoFile.absolutePath}", e)
        }
    }
}

private fun getInfoFile(gameHome: String) = File(gameHome, "turtle-game.cfg")

/**
 * Refreshes and returns the latest game info (auto-handling legacy config migration)
 * @param gameHome the game directory this info belongs to
 */
fun refreshCurrentInfo(gameHome: String): CurrentGameInfo {
    val infoFile = getInfoFile(gameHome)

    return runCatching {
        when {
            infoFile.exists() -> loadFromJsonFile(infoFile)
            else -> createNewConfig(gameHome)
        }
    }.getOrElse { e ->
        Logger.error(TAG, "Refresh failed", e)
        createNewConfig(gameHome)
    }
}

private fun loadFromJsonFile(infoFile: File): CurrentGameInfo {
    return GSON.fromJson(infoFile.readText(), CurrentGameInfo::class.java).also { info ->
        checkNotNull(info) { "Deserialization returned null" }
    }
}

private fun createNewConfig(gameHome: String) = CurrentGameInfo().applyPostActions(gameHome)

private fun CurrentGameInfo.applyPostActions(gameHome: String): CurrentGameInfo {
    saveCurrentInfo(gameHome)
    return this
}