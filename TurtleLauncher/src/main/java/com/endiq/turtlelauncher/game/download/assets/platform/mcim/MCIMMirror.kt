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

package com.endiq.turtlelauncher.game.download.assets.platform.mcim

import com.endiq.turtlelauncher.game.addons.mirror.MirrorPriority
import com.endiq.turtlelauncher.game.addons.mirror.orderCandidates
import com.endiq.turtlelauncher.game.addons.mirror.resolveMirrorPriority
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.isChinaMainland

private const val ROOT = "https://mod.mcimirror.top"

private val REPLACE_MIRROR_HOLDERS = listOf(
    //CurseForge
    "https://edge.forgecdn.net",
    //Modrinth
    "https://cdn.modrinth.com"
)

private fun assetPlatformPriority(): MirrorPriority =
    resolveMirrorPriority(AllSettings.assetPlatformSource.getValue(), mainland = true)

/**
 * Decides whether to enable the MCIM mirror based on mainland-China status, generating ordered candidate links per preference
 */
fun String.mapMCIMMirrorUrls(): List<String> {
    if (!isChinaMainland()) return listOf(this)

    val mirroredUrl = REPLACE_MIRROR_HOLDERS.find { key ->
        startsWith(key)
    }?.let { origin ->
        replaceFirst(origin, ROOT)
    }

    return orderCandidates(official = this, mirror = mirroredUrl, priority = assetPlatformPriority())
}

/**
 * Multi-link form: generates mirror versions of the replaceable links in the array, interleaving them before/after the originals per preference
 */
fun Array<String>.mapMCIMMirrorUrls(): List<String> {
    if (!isChinaMainland()) return toList()

    val sources = mapNotNull { url ->
        REPLACE_MIRROR_HOLDERS.find { key ->
            url.startsWith(key)
        }?.let { origin ->
            url.replaceFirst(origin, ROOT)
        }
    }
    if (sources.isEmpty()) return toList()

    return when (assetPlatformPriority()) {
        MirrorPriority.OFFICIAL -> this.toList()
        MirrorPriority.MIRROR_FIRST -> sources + this.toList()
    }
}
