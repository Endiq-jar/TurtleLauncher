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

package com.endiq.turtlelauncher.game.version.mod

import android.os.Parcelable
import com.endiq.turtlelauncher.game.download.assets.platform.Platform
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformVersion
import com.endiq.turtlelauncher.game.download.assets.platform.curseforge.models.CurseForgeFile
import com.endiq.turtlelauncher.game.download.assets.platform.modrinth.models.ModrinthVersion
import kotlinx.parcelize.Parcelize

/**
 * Platform-matched install info of a local mod file
 * @param platform Owning platform
 * @param projectId platform project ID
 * @param versionId platform version (file) ID
 * @param versionName platform version name
 * @param notFound whether the project is absent from the platform; serves as the negative cache flag for fingerprint misses
 */
@Parcelize
data class InstalledMod(
    val platform: Platform,
    val projectId: String,
    val versionId: String,
    val versionName: String,
    val notFound: Boolean = false
) : Parcelable

/**
 * Converts a fingerprint-matched platform version into local install info
 */
fun PlatformVersion.toInstalledMod(): InstalledMod = when (this) {
    is ModrinthVersion -> InstalledMod(
        platform = Platform.MODRINTH,
        projectId = projectId,
        versionId = id,
        versionName = versionNumber
    )
    is CurseForgeFile -> InstalledMod(
        platform = Platform.CURSEFORGE,
        projectId = modId.toString(),
        versionId = id.toString(),
        versionName = displayName
    )
    else -> error("Unknown version type: $this")
}
