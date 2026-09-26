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

package com.endiq.turtlelauncher.game.version.export

import com.endiq.turtlelauncher.game.addons.modloader.ModLoader
import java.io.File

/**
 * All info needed to export a modpack
 * @param name user-chosen name of the modpack to export
 * @param summary user-chosen modpack description
 * @param author modpack author name
 * @param version user-chosen modpack version
 * @param mcVersion Minecraft version
 * @param loader mod loaders carried by the version
 * @param selectedFiles user-picked files to export
 * @param minMemory user-chosen minimum memory for the modpack
 * @param maxMemory user-chosen maximum memory for the modpack
 * @param gameArgs game arguments
 * @param javaArgs JVM arguments
 * @param fileApi modpack download URL prefix
 * @param url modpack official website
 * @param forceUpdate forces modpack updates
 * @param packType the modpack export type
 * @param packModrinth whether to pack Modrinth remote resources
 * @param packCurseForge whether to pack CurseForge remote resources
 */
data class ExportInfo(
    val gamePath: File,
    val name: String = "",
    val summary: String? = null,
    val author: String = "",
    val version: String = "",
    val mcVersion: String = "",
    val loader: LoaderVersion? = null,
    val selectedFiles: List<File> = emptyList(),
    val minMemory: Int = 0,
    val maxMemory: Int = 0,
    val gameArgs: String = "",
    val javaArgs: String = "",
    val fileApi: String? = null,
    val url: String = "",
    val forceUpdate: Boolean = false,
    val packType: PackType = PackType.Modrinth,
    val packModrinth: Boolean = false,
    val packCurseForge: Boolean = false
) {
    /**
     * Mod loader info
     * @param version Loader version
     */
    data class LoaderVersion(
        val loader: ModLoader,
        val version: String
    )
}
