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

package com.endiq.turtlelauncher.game.download.assets.platform

import java.io.File

/**
 * Abstract platform resource searcher
 * @param platform target platform type (identifier only)
 * @param source source name (official vs mirror); only needed for logging
 */
abstract class AbstractPlatformSearcher(
    val platform: Platform,
    val source: String
) {
    /**
     * Resource search result list
     */
    abstract suspend fun searchAssets(
        query: String,
        searchFilter: PlatformSearchFilter,
        platformClasses: PlatformClasses
    ): PlatformSearchResult

    /**
     * Fetches a single project's info
     */
    abstract suspend fun getProject(
        projectID: String,
    ): PlatformProject

    /**
     * Fetches all versions of a single project
     */
    abstract suspend fun getVersions(
        projectID: String,
        pageCallback: (chunk: Int, page: Int) -> Unit = { _, _ -> },
    ): List<PlatformVersion>

    /**
     * Tries to find matching version info via a local file's SHA value
     */
    abstract suspend fun getVersionByLocalFile(
        file: File,
        sha1: String,
    ): PlatformVersion?
}