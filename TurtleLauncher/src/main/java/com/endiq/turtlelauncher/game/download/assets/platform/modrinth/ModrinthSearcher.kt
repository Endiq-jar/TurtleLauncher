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

package com.endiq.turtlelauncher.game.download.assets.platform.modrinth

import com.endiq.turtlelauncher.game.download.assets.platform.AbstractPlatformSearcher
import com.endiq.turtlelauncher.game.download.assets.platform.Platform
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformClasses
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformSearchFilter
import com.endiq.turtlelauncher.game.download.assets.platform.modrinth.models.ModrinthSingleProject
import com.endiq.turtlelauncher.game.download.assets.platform.modrinth.models.ModrinthVersion
import com.endiq.turtlelauncher.utils.network.httpGetJson
import com.endiq.turtlelauncher.utils.network.httpPostJson
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.Parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

class ModrinthSearcher(
    val api: String = MODRINTH_API,
    source: String = "Official Modrinth"
): AbstractPlatformSearcher(
    platform = Platform.MODRINTH,
    source = source
) {
    override suspend fun searchAssets(
        query: String,
        searchFilter: PlatformSearchFilter,
        platformClasses: PlatformClasses
    ): ModrinthSearchResult {
        return httpGetJson(
            url = "$api/search",
            parameters = searchFilter.toModrinthRequest(
                query = query,
                platformClasses = platformClasses
            ).toParameters()
        )
    }

    override suspend fun getProject(projectID: String): ModrinthSingleProject {
        val project = httpGetJson<ModrinthSingleProject>(
            url = "$api/project/$projectID"
        )
        // No handling by default; if unreachable, Modrinth itself returns 404
//        if (!project.isPublic()) throw NotFoundException("The project {$projectID} is not in a publicly available state.")
        return project
    }

    /**
     * Fetches a Modrinth project's version list (optional range)
     * @param pageSize entries requested per page; null fetches all versions
     * @param offset start index; null fetches all versions
     */
    suspend fun getVersionsChunk(
        projectID: String,
        pageSize: Int? = null,
        offset: Int? = null,
    ): List<ModrinthVersion> {
        return httpGetJson(
            url = "$api/project/$projectID/version",
            parameters = if (pageSize != null && offset != null) {
                Parameters.build {
                    append("limit", pageSize.toString())
                    append("offset", offset.toString())
                    append("include_changelog", "false")
                }
            } else null
        )
    }

    override suspend fun getVersions(
        projectID: String,
        pageCallback: (chunk: Int, page: Int) -> Unit
    ): List<ModrinthVersion> {
        return getVersionsChunk(
            projectID = projectID
        )
    }

    /**
     * Fetches a single Modrinth version by ID
     */
    suspend fun getVersion(versionID: String): ModrinthVersion {
        return httpGetJson(
            url = "$api/version/$versionID"
        )
    }

    override suspend fun getVersionByLocalFile(
        file: File,
        sha1: String
    ): ModrinthVersion? {
        return try {
            httpGetJson(
                url = "$api/version_file/$sha1",
                parameters = Parameters.build {
                    append("algorithm", "sha1")
                }
            )
        } catch (_: ClientRequestException) {
            null
        }
    }

    /**
     * Batch-fetches version info via multiple local files' SHA-1 values
     * @return key = SHA-1, value = matched version; missed fingerprints are excluded
     */
    suspend fun getVersionFiles(
        sha1List: List<String>
    ): Map<String, ModrinthVersion> {
        if (sha1List.isEmpty()) return emptyMap()
        return httpPostJson(
            url = "$api/version_files",
            body = ModrinthVersionFilesRequest(hashes = sha1List)
        )
    }
}

/**
 * Request body for bulk version info
 */
@Serializable
private data class ModrinthVersionFilesRequest(
    @SerialName("hashes")
    val hashes: List<String>,
    @SerialName("algorithm")
    val algorithm: String = "sha1"
)