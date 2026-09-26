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

import com.endiq.turtlelauncher.game.download.game.parseLibraryComponents
import com.endiq.turtlelauncher.game.versioninfo.models.GameManifest
import com.endiq.turtlelauncher.game.versioninfo.models.GameManifest.Features
import com.endiq.turtlelauncher.game.versioninfo.models.GameManifest.Library
import com.endiq.turtlelauncher.game.versioninfo.models.GameManifest.Os
import com.endiq.turtlelauncher.game.versioninfo.models.GameManifest.Rule
import com.endiq.turtlelauncher.utils.GSON
import org.jackhuang.hmcl.util.versioning.VersionNumber

/**
 * Deduplicates launch dependency libraries
 * Identical coordinates and rules: keep only the newest version
 * Declarations with differing rules or coordinates are all kept
 */
fun uniqueLibraries(manifest: GameManifest): GameManifest {
    val libraries = manifest.libraries ?: return manifest

    val unique = ArrayList<Library>(libraries.size)
    val indexes = HashMap<String, MutableList<Int>>()

    for (library in libraries) {
        val components = parseLibraryComponents(library.name)
        val id = "${components.groupId}:${components.artifactId}"

        val existing = indexes[id]
        if (existing == null) {
            indexes[id] = mutableListOf(unique.size)
            unique.add(library)
            continue
        }

        var duplicate = false
        for (otherIndex in existing) {
            val other = unique[otherIndex]
            // Different rules
            // Platform-specific variants
            if (rulesHash(library) != rulesHash(other)) continue

            // Keep the newest version
            val comparison = VersionNumber.compare(
                components.version,
                parseLibraryComponents(other.name).version
            )

            if (comparison > 0) {
                unique[otherIndex] = library
            } else if (comparison == 0) {
                if (library.name == other.name && library.isNative == other.isNative) {
                    // Identical coordinates
                    if (GSON.toJson(library).length > GSON.toJson(other).length) {
                        unique[otherIndex] = library
                    }
                } else {
                    // Same version but different coordinates, e.g. jar vs natives variants
                    continue
                }
            }
            duplicate = true
            break
        }

        if (!duplicate) {
            existing.add(unique.size)
            unique.add(library)
        }
    }

    if (unique.size != libraries.size) {
        manifest.libraries = unique
    }
    return manifest
}

private fun rulesHash(library: Library): Int {
    val rules = library.rules ?: return 0
    var hash = 1
    for (rule in rules) {
        hash = 31 * hash + ruleHash(rule)
    }
    return hash
}

private fun ruleHash(rule: Rule): Int {
    var hash = rule.action?.hashCode() ?: 0
    hash = 31 * hash + osHash(rule.os)
    hash = 31 * hash + featuresHash(rule.features)
    return hash
}

private fun osHash(os: Os?): Int {
    if (os == null) return 0
    var hash = 1
    hash = 31 * hash + (os.name?.hashCode() ?: 0)
    hash = 31 * hash + (os.arch?.hashCode() ?: 0)
    return hash
}

private fun featuresHash(features: Features?): Int {
    if (features == null) return 0
    var hash = 1
    hash = 31 * hash + (features.hasCustomResolution?.hashCode() ?: 0)
    hash = 31 * hash + (features.demoUser?.hashCode() ?: 0)
    hash = 31 * hash + (features.hasQuickPlaysSupport?.hashCode() ?: 0)
    hash = 31 * hash + (features.quickPlaySingleplayer?.hashCode() ?: 0)
    hash = 31 * hash + (features.quickPlayMultiplayer?.hashCode() ?: 0)
    hash = 31 * hash + (features.quickPlayRealms?.hashCode() ?: 0)
    return hash
}