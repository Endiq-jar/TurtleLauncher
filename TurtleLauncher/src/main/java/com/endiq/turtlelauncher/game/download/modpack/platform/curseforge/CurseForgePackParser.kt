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

package com.endiq.turtlelauncher.game.download.modpack.platform.curseforge

import com.endiq.turtlelauncher.game.download.modpack.platform.PackPlatform
import com.endiq.turtlelauncher.game.download.modpack.platform.SimplePackParser
import com.endiq.turtlelauncher.game.download.modpack.platform.mcbbs.MCBBSManifest
import com.endiq.turtlelauncher.game.download.modpack.platform.mcbbs.MCBBSPackMetaParser
import com.endiq.turtlelauncher.game.download.modpack.platform.multimc.MultiMCManifest
import com.endiq.turtlelauncher.game.download.modpack.platform.multimc.MultiMCPackParser
import com.endiq.turtlelauncher.utils.GSON
import com.endiq.turtlelauncher.utils.logging.Logger
import java.io.File

private const val TAG = "CurseForgePackParser"

/**
 * CurseForge modpack parser, trying the CurseForge format
 */
object CurseForgePackParser : SimplePackParser<CurseForgeManifest>(
    indexFilePath = "manifest.json",
    manifestClass = CurseForgeManifest::class.java,
    extraProcess = extraProcess@{ root ->
        //Rule out MultiMC misidentification
        val mccManifest = File(root, MultiMCPackParser.indexFilePath)
        if (mccManifest.exists()) {
            try {
                GSON.fromJson(mccManifest.readText(), MultiMCManifest::class.java)
                //Recognized as a MultiMC pack: it was misidentified as CurseForge
                return@extraProcess false
            } catch (th: Throwable) {
                Logger.warning(TAG, "An exception occurred while trying to exclude the MultiMC modpack.", th)
            }
        }
        //Rule out MCBBS misidentification
        val mcbbsMeta = File(root, MCBBSPackMetaParser.indexFilePath)
        if (mcbbsMeta.exists()) {
            try {
                GSON.fromJson(mcbbsMeta.readText(), MCBBSManifest::class.java)
                //Recognized as an MCBBS pack: it was misidentified as CurseForge
                return@extraProcess false
            } catch (th: Throwable) {
                Logger.warning(TAG, "An exception occurred while trying to exclude the MCBBS modpack.", th)
            }
        }
        true
    },
    buildPack = { root, manifest ->
        CurseForgePack(root = root, manifest = manifest)
    }
) {
    override fun getIdentifier(): String = PackPlatform.CurseForge.identifier
}