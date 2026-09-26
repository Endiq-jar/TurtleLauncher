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

package com.endiq.turtlelauncher.game.versioninfo.models

import com.endiq.turtlelauncher.game.versioninfo.MinecraftVersion
import com.endiq.turtlelauncher.game.versioninfo.allAprilFools

/**
 * Simple version type filter: release, snapshot, ancient
 * @param release whether to keep releases
 * @param snapshot whether to keep snapshots
 * @param old whether to keep ancient versions
 */
fun List<VersionManifest.Version>.filterType(
    release: Boolean,
    snapshot: Boolean,
    old: Boolean
) = this.filter { version ->
    version.isType(release, snapshot, old)
}

/**
 * Maps a [VersionManifest.Version] list into a [MinecraftVersion] list
 */
fun List<VersionManifest.Version>.mapVersion(): List<MinecraftVersion> {
    return this.map { version ->
        //Check whether it's an April Fools version
        val aprilFoolsVersion = allAprilFools.find { it.version.equals(version.id, ignoreCase = true) }

        MinecraftVersion(
            version = version,
            type = if (aprilFoolsVersion != null) {
                //Confirmed April Fools version
                MinecraftVersion.Type.AprilFools
            } else {
                when (version.type) {
                    "release" -> MinecraftVersion.Type.Release
                    "snapshot", "pending", "unobfuscated" -> MinecraftVersion.Type.Snapshot
                    "old_beta" -> MinecraftVersion.Type.OldBeta
                    "old_alpha" -> MinecraftVersion.Type.OldAlpha
                    else -> MinecraftVersion.Type.Unknown
                }
            },
            summary = aprilFoolsVersion?.type?.summary, //Only April Fools versions get a description for now
            urlSuffix = aprilFoolsVersion?.type?.urlSuffix
        )
    }
}

/**
 * Checks whether the version type matches the given types
 * @param release returned when the version is a release
 * @param snapshot returned when the version is a snapshot
 * @param aprilFools returned when the version is an April Fools version
 * @param old returned when the version is ancient
 */
fun MinecraftVersion.isType(
    release: Boolean,
    snapshot: Boolean,
    aprilFools: Boolean,
    old: Boolean
) = when (type) {
    MinecraftVersion.Type.Release -> release
    MinecraftVersion.Type.Snapshot -> snapshot
    MinecraftVersion.Type.OldBeta -> old
    MinecraftVersion.Type.OldAlpha -> old
    MinecraftVersion.Type.AprilFools -> aprilFools
    MinecraftVersion.Type.Unknown -> old //Unknown versions default to ancient
}

/**
 * Checks whether the version type matches the given types
 * @param release returned when the version is a release
 * @param snapshot returned when the version is a snapshot
 * @param old returned when the version is ancient
 */
fun VersionManifest.Version.isType(
    release: Boolean,
    snapshot: Boolean,
    old: Boolean
) = when (type) {
    "release" -> release
    "snapshot", "pending" -> snapshot
    else -> old && type.startsWith("old")
}