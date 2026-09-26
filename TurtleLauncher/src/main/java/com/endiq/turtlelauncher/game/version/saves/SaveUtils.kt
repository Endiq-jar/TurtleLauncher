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

package com.endiq.turtlelauncher.game.version.saves

import com.github.steveice10.opennbt.NBTIO
import com.github.steveice10.opennbt.tag.builtin.CompoundTag
import com.endiq.turtlelauncher.game.version.installed.utils.isBiggerOrEqualVer
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.nbt.asBooleanNotNull
import com.endiq.turtlelauncher.utils.nbt.asCompoundTag
import com.endiq.turtlelauncher.utils.nbt.asInt
import com.endiq.turtlelauncher.utils.nbt.asLong
import com.endiq.turtlelauncher.utils.nbt.asString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "SaveUtils"

/**
 * Checks whether this save is compatible with the given version
 * @param minecraftVersion the current MC version, used for compatibility comparison
 */
fun SaveData.isCompatible(minecraftVersion: String) =
    isValid && levelMCVersion != null && minecraftVersion.isBiggerOrEqualVer(levelMCVersion)

/**
 * Parses the required info out of level.dat to build a SaveData
 * [Reference Minecraft Wiki](https://minecraft.wiki/w/%E5%AD%98%E6%A1%A3%E5%9F%BA%E7%A1%80%E6%95%B0%E6%8D%AE%E5%AD%98%E5%82%A8%E6%A0%BC%E5%BC%8F#%E5%AD%98%E5%82%A8%E6%A0%BC%E5%BC%8F)
 * @param saveFile the save's folder
 * @param levelDatFile the level.dat file
 * @param worldGenDatFile the 26.1+ save format moved world-gen settings to /data/minecraft/world_gen_settings.dat
 */
suspend fun parseLevelDatFile(
    saveFile: File,
    levelDatFile: File,
    worldGenDatFile: File? = null
): SaveData = withContext(Dispatchers.IO) {
//    val fileSize = FileUtils.sizeOf(saveFile)
    runCatching {
        if (!levelDatFile.exists()) error("The ${levelDatFile.absolutePath} file does not exist!")

        val compound = NBTIO.readFile(levelDatFile)
            ?: error("Failed to read the level.dat file as a CompoundTag.")
        val data: CompoundTag = compound.asCompoundTag("Data")
            ?: error("{level.dat} Data entry not found in the NBT structure tree.")

        //Save name; empty when absent
        val levelName = data.asString("LevelName", "")
        //The save's game version
        val levelMCVersion = data.asCompoundTag("Version")?.asString("Name", null)
        //Timestamp of the save's last save
        val lastPlayed = data.asLong("LastPlayed", 0) ?: 0 //0 means absent
        //Play time
        val playTime = data.asLong("Time", 0) ?: 0 //0 means absent
        //The save's game mode
        val gameMode = data.asInt("GameType", 0) //defaults to survival
            ?.let { levelCode -> GameMode.entries.find { it.levelCode == levelCode } }
        //Game difficulty
        val difficulty = data.asInt("Difficulty", 2) //defaults to normal
            ?.let { levelCode -> Difficulty.entries.find { it.levelCode == levelCode } }
        //Whether the game difficulty is locked
        val difficultyLocked = data.asBooleanNotNull("DifficultyLocked", false)
        //Whether hardcore mode is on
        val hardcoreMode = data.asBooleanNotNull("hardcore", false)
        //Whether commands (cheats) are enabled
        val allowCommands = if (data.contains("allowCommands")) {
            data.asBooleanNotNull("allowCommands", false)
        } else {
            //When allowCommands is absent, judge by game mode
            gameMode == GameMode.CREATIVE
        }
        //World seed
        val worldSeed = if (worldGenDatFile != null && worldGenDatFile.isFile && worldGenDatFile.exists()) {
            //26.1+
            runCatching {
                val worldGenCompound = NBTIO.readFile(worldGenDatFile)
                    ?: error("Failed to read the world_gen_settings.dat file as a CompoundTag.")
                val worldData = worldGenCompound.asCompoundTag("data")
                    ?: error("{world_gen_settings.dat} data entry not found in the NBT structure tree.")

                worldData.asLong("seed", null)
            }.onFailure {
                Logger.warning(TAG, "An exception occurred while reading and parsing the world_gen_settings.dat file (${worldGenDatFile.absolutePath}).", it)
            }.getOrNull()
        } else {
            data.asCompoundTag("WorldGenSettings")
                ?.asLong("seed", null)
            //If absent, try reading RandomSeed
                ?: data.asLong("RandomSeed", null)
        }

        SaveData(
            saveFile = saveFile,
//            saveSize = fileSize,
            isValid = true,
            levelName = levelName,
            levelMCVersion = levelMCVersion,
            lastPlayed = lastPlayed.takeIf { it != 0L },
            playTime = playTime.takeIf { it != 0L },
            gameMode = gameMode,
            //Hardcore mode: once enabled, the difficulty locks to hard (even though level.dat doesn't store it that way)
            //https://zh.minecraft.wiki/w/%E6%9E%81%E9%99%90%E6%A8%A1%E5%BC%8F#%E5%88%9B%E5%BB%BA%E6%96%B0%E7%9A%84%E4%B8%96%E7%95%8C
            difficulty = if (hardcoreMode) Difficulty.HARD else difficulty,
            difficultyLocked = difficultyLocked,
            hardcoreMode = hardcoreMode,
            allowCommands = allowCommands,
            worldSeed = worldSeed
        )
    }.onFailure {
        Logger.warning(TAG, "An exception occurred while reading and parsing the level.dat file (${levelDatFile.absolutePath}).", it)
    }.getOrElse {
        //On read failure, return invalid data
        SaveData(
            saveFile = saveFile,
//            saveSize = fileSize,
            isValid = false
        )
    }
}
