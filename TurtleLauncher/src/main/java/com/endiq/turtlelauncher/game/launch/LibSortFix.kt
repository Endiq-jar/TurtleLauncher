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

package com.endiq.turtlelauncher.game.launch

import com.endiq.turtlelauncher.game.addons.modloader.ModLoader
import com.endiq.turtlelauncher.game.version.installed.VersionInfo
import com.endiq.turtlelauncher.game.versioninfo.models.GameManifest

/**
 * Native library load-order fixing & dependency checking
 * Born out of future extension needs (
 */
class LibSortFix(
    versionInfo: VersionInfo?
) {
    val isCleanroom = versionInfo?.loaderInfo?.loader == ModLoader.CLEANROOM

    private val icu4jLib = "com.ibm.icu:icu4j:"
    private val mojangICU4jLib = "com.ibm.icu:icu4j-core-mojang:"

    /**
     * Checks and inserts dependency libraries
     */
    fun LinkedHashMap<GameManifest.Library, String>.insertLib(
        libItem: GameManifest.Library,
        path: String
    ) {
        val name = libItem.name
        if (isCleanroom) {
            when {
                name.startsWith(icu4jLib) -> {
                    insertBefore(libItem, path) { (key, _) ->
                        key.name.startsWith(mojangICU4jLib)
                    }
                    return
                }
                name.startsWith(mojangICU4jLib) -> {
                    insertAfter(libItem, path) { (key, _) ->
                        key.name.startsWith(icu4jLib)
                    }
                    return
                }
            }
        }

        if (name.startsWith("org.ow2.asm:asm:")) {
            removeIf { (key, _) ->
                key.name.startsWith("org.ow2.asm:asm-all:")
            }
        }

        this[libItem] = path
    }

    private fun <K, V> LinkedHashMap<K, V>.removeIf(predicate: (Map.Entry<K, V>) -> Boolean) {
        val iterator = this.entries.iterator()
        while (iterator.hasNext()) {
            if (predicate(iterator.next())) {
                iterator.remove()
            }
        }
    }

    private fun <K, V> LinkedHashMap<K, V>.insertBefore(
        key: K,
        value: V,
        find: (Map.Entry<K, V>) -> Boolean
    ) {
        val newMap = LinkedHashMap<K, V>()
        var inserted = false

        for (entry in this) {
            if (!inserted && find(entry)) {
                newMap[key] = value
                inserted = true
            }
            newMap[entry.key] = entry.value
        }

        if (!inserted) {
            newMap[key] = value
        }

        clear()
        putAll(newMap)
    }

    private fun <K, V> LinkedHashMap<K, V>.insertAfter(
        key: K,
        value: V,
        find: (Map.Entry<K, V>) -> Boolean
    ) {
        val newMap = LinkedHashMap<K, V>()
        var inserted = false

        for (entry in this) {
            newMap[entry.key] = entry.value

            if (!inserted && find(entry)) {
                newMap[key] = value
                inserted = true
            }
        }

        if (!inserted) {
            newMap[key] = value
        }

        clear()
        putAll(newMap)
    }
}