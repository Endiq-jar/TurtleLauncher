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

package com.endiq.turtlelauncher.game.download.modpack.platform

import com.endiq.turtlelauncher.game.download.modpack.platform.curseforge.CurseForgePackParser
import com.endiq.turtlelauncher.game.download.modpack.platform.mcbbs.MCBBSPackMetaParser
import com.endiq.turtlelauncher.game.download.modpack.platform.modrinth.ModrinthPackParser
import com.endiq.turtlelauncher.game.download.modpack.platform.multimc.MultiMCPackParser
import java.io.File

/**
 * Generic modpack parser interface, trying to parse an unpacked modpack's format
 */
interface PackParser {
    /**
     * Tries to parse the format of an unpacked modpack
     * @param packFolder the unpacked modpack folder
     */
    suspend fun parse(packFolder: File): AbstractPack?

    /**
     * Returns this parser's identifier
     */
    fun getIdentifier(): String
}

/**
 * All modpack format parsers supported by the launcher
 */
val ALL_PACK_PARSER = listOf(
    CurseForgePackParser,
    ModrinthPackParser,
    MultiMCPackParser,
    MCBBSPackMetaParser
)