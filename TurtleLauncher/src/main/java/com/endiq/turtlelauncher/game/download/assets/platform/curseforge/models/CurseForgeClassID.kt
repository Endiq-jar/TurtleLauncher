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

package com.endiq.turtlelauncher.game.download.assets.platform.curseforge.models

import com.endiq.turtlelauncher.game.download.assets.platform.PlatformClasses

/**
 * CurseForge resource search categories
 */
enum class CurseForgeClassID(val classID: Int, val slug: String) {
    /** Mod */
    MOD(6, "mc-mods"),

    /** Modpack */
    MOD_PACK(4471, "modpacks"),

    /** Resource pack */
    RESOURCE_PACK(12, "texture-packs"),

    /** Save (world) */
    SAVES(17, "worlds"),

    /** Shader pack */
    SHADERS(6552, "shaders")
}

/**
 * Fetches CurseForge resource category info
 */
fun CurseForgeData.getClassIdOrNull(): CurseForgeClassID? {
    return if (classId == null) {
        null
    } else {
        CurseForgeClassID.entries.find { id ->
            id.classID == classId
        }
    }
}

/**
 * Fetches CurseForge platform category info
 */
fun CurseForgeData.getPlatformClassesOrNull(): PlatformClasses? {
    val classIdType = getClassIdOrNull() ?: return null
    return when (classIdType) {
        CurseForgeClassID.MOD -> PlatformClasses.MOD
        CurseForgeClassID.MOD_PACK -> PlatformClasses.MOD_PACK
        CurseForgeClassID.RESOURCE_PACK -> PlatformClasses.RESOURCE_PACK
        CurseForgeClassID.SAVES -> PlatformClasses.SAVES
        CurseForgeClassID.SHADERS -> PlatformClasses.SHADERS
    }
}