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

package com.endiq.turtlelauncher.game.download.assets.platform.modrinth.models

import kotlinx.serialization.json.Json

/**
 * Modrinth search filters
 */
interface ModrinthFacet {
    /**
     * Filter name
     */
    fun facetName(): String

    /**
     * Filter value
     */
    fun facetValue(): String

    /**
     * Converts to the format Modrinth accepts
     */
    fun describe(): String? = "${facetName()}:${facetValue()}"
}

/**
 * Minecraft version filter
 */
class VersionFacet(val version: String) : ModrinthFacet {
    override fun facetValue(): String = version
    override fun facetName(): String = "versions"
}

/**
 * 项目类型
 */
enum class ProjectTypeFacet : ModrinthFacet {
    MOD {
        override fun facetValue(): String = "mod"
    },

    MODPACK {
        override fun facetValue(): String = "modpack"
    },

    RESOURCE_PACK {
        override fun facetValue(): String = "resourcepack"
    },

    SHADER {
        override fun facetValue(): String = "shader"
    };

    override fun facetName(): String = "project_type"
}

/**
 * Converts to the format Modrinth accepts
 */
fun List<ModrinthFacet>.toFacetsString(): String {
    val rawFacets = this.map { listOfNotNull(it.describe()) }
    return Json.encodeToString(rawFacets)
}