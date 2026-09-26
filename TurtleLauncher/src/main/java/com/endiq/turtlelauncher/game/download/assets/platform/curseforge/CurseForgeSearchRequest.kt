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

package com.endiq.turtlelauncher.game.download.assets.platform.curseforge

import com.endiq.turtlelauncher.game.download.assets.platform.PlatformSortField
import com.endiq.turtlelauncher.game.download.assets.platform.curseforge.models.CurseForgeCategory
import com.endiq.turtlelauncher.game.download.assets.platform.curseforge.models.CurseForgeClassID
import com.endiq.turtlelauncher.game.download.assets.platform.curseforge.models.CurseForgeModLoader
import io.ktor.http.Parameters

/**
 * [CurseForge api](https://docs.curseforge.com/rest-api/?shell#search-mods)
 */
data class CurseForgeSearchRequest(
    /** Game ID */
    val gameId: Int = 432, //Minecraft game ID

    val classId: Int = CurseForgeClassID.MOD.classID,

    /** Category of the searched resource */
    val categories: Set<CurseForgeCategory>? = null,

    /** Resource name filter */
    val searchFilter: String? = null,

    /** Game version filter */
    val gameVersion: String? = null,

    /** Sort order */
    val sortField: PlatformSortField = PlatformSortField.RELEVANCE,

    val sortOrder: String = "desc",

    /** Mod loader filter */
    val modLoader: CurseForgeModLoader? = null,

    /** Number of result pages to skip (pagination) */
    val index: Int = 0,

    /** Number of result pages to return, max 50 */
    val pageSize: Int = 20,
) {
    /**
     * Converts to GET parameters
     */
    fun toParameters(): Parameters = Parameters.build {
        append("gameId", gameId.toString())
        append("classId", classId.toString())
        categories.mutableParameters(
            singleName = "categoryId",
            mutableName = "categoryIds",
            toString = { it.describe() },
        ) { name, value ->
            append(name, value)
        }
        searchFilter?.let {
            append("searchFilter", it)
        }
        gameVersion?.let {
            append("gameVersion", it)
        }
        modLoader?.let {
            append("modLoaderType", it.code.toString())
        }
        append("sortField", sortField.curseforge)
        append("sortOrder", sortOrder)
        append("index", index.toString())
        append("pageSize", pageSize.toString())
    }

    /**
     * Picks parameter names case by case, serializing parameter lists as string values
     * Currently only categoryIds works properly
     */
    private fun <E> Collection<E>?.mutableParameters(
        singleName: String,
        mutableName: String,
        toString: E.(E) -> String?,
        append: (name: String, value: String) -> Unit
    ) {
        this?.takeIf { it.isNotEmpty() }?.let { c1 ->
            if (c1.size > 1) {
                c1.mapNotNull { it.toString(it) }
                    .takeIf { it.isNotEmpty() }
                    ?.let { categoryStrings ->
                        //Multi-filter format: name=[aaa,bbb,...]
                        append(mutableName, categoryStrings.joinToString(separator = ",", prefix = "[", postfix = "]") { it })
                    }
            } else {
                val value = c1.first()
                value.toString(value)
                    .takeIf { !it.isNullOrEmpty() }
                    ?.let { categoryString ->
                        append(singleName, categoryString)
                    }
            }
        }
    }
}