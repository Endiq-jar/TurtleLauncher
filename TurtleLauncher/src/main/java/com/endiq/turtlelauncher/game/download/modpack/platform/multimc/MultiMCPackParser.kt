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

package com.endiq.turtlelauncher.game.download.modpack.platform.multimc

import com.endiq.turtlelauncher.game.download.modpack.platform.PackPlatform
import com.endiq.turtlelauncher.game.download.modpack.platform.SimplePackParser

/**
 * MultiMC modpack parser, trying mmc-pack.json
 */
object MultiMCPackParser : SimplePackParser<MultiMCManifest>(
    indexFilePath = "mmc-pack.json",
    manifestClass = MultiMCManifest::class.java,
    buildPack = { root, manifest ->
        //Ensure a Minecraft version is provided; sanity check: components must not be empty!
        if (manifest.getMinecraftVersion() == null || manifest.components.isEmpty()) {
            error("This MMC modpack does not provide game version information and cannot be installed!")
        }

        MultiMCPack(root = root, manifest = manifest)
    }
) {
    override fun getIdentifier(): String = PackPlatform.MultiMC.identifier
}