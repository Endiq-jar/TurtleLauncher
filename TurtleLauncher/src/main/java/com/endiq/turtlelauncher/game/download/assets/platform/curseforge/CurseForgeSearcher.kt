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

import com.endiq.turtlelauncher.game.download.assets.platform.AbstractPlatformSearcher
import com.endiq.turtlelauncher.game.download.assets.platform.Platform
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformClasses
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformSearchFilter
import com.endiq.turtlelauncher.game.download.assets.platform.curseforge.models.CurseForgeFile
import com.endiq.turtlelauncher.game.download.assets.platform.curseforge.models.CurseForgeFingerprintsMatches
import com.endiq.turtlelauncher.game.download.assets.platform.curseforge.models.CurseForgeProject
import com.endiq.turtlelauncher.game.download.assets.platform.curseforge.models.CurseForgeVersion
import com.endiq.turtlelauncher.game.download.assets.platform.curseforge.models.CurseForgeVersions
import com.endiq.turtlelauncher.game.download.assets.platform.curseforge.models.isApproved
import com.endiq.turtlelauncher.utils.file.MurmurHash2Incremental
import com.endiq.turtlelauncher.utils.network.httpGetJson
import com.endiq.turtlelauncher.utils.network.httpPostJson
import io.ktor.http.Parameters
import io.ktor.server.plugins.NotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

class CurseForgeSearcher(
    val api: String = CURSEFORGE_API,
    source: String = "Official CurseForge"
): AbstractPlatformSearcher(
    platform = Platform.CURSEFORGE,
    source = source
) {
    override suspend fun searchAssets(
        query: String,
        searchFilter: PlatformSearchFilter,
        platformClasses: PlatformClasses
    ): CurseForgeSearchResult {
        return httpGetJson(
            url = "$api/mods/search",
            parameters = searchFilter.toCurseForgeRequest(
                query = query,
                platformClasses = platformClasses
            ).toParameters()
        )
    }

    override suspend fun getProject(projectID: String): CurseForgeProject {
        val project = httpGetJson<CurseForgeProject>(
            url = "$api/mods/$projectID"
        )
        if (!project.isApproved()) throw NotFoundException("The project {$projectID} is not in a publicly available state.")
        return project
    }

    /**
     * Fetches one file of a project from CurseForge
     */
    suspend fun getVersion(
        projectID: String,
        fileID: String,
    ): CurseForgeVersion {
        return httpGetJson(
            url = "$api/mods/$projectID/files/$fileID"
        )
    }

    /**
     * Fetches a project's version list by page from CurseForge
     * @param index start index
     * @param pageSize entries requested per page
     */
    suspend fun getVersions(
        projectID: String,
        index: Int = 0,
        pageSize: Int = 100
    ): CurseForgeVersions = httpGetJson(
        url = "$api/mods/$projectID/files",
        parameters = Parameters.build {
            append("index", index.toString())
            append("pageSize", pageSize.toString())
        }
    )

    override suspend fun getVersions(
        projectID: String,
        pageCallback: (chunk: Int, page: Int) -> Unit
    ): List<CurseForgeFile> {
        return getAllVersions(
            pageSize = 50,
            chunkSize = 20,
            maxConcurrent = 10,
            pageCallback = pageCallback,
            checkNotEmpty = { versions ->
                versions.data.isNotEmpty()
            },
            asyncVersions = { index, pageSize ->
                getVersions(
                    projectID = projectID,
                    index = index,
                    pageSize = pageSize,
                )
            },
            processVersions = { versions ->
                val files = versions?.data ?: emptyArray()
                files.toList() to files.size
            }
        )
    }

    override suspend fun getVersionByLocalFile(
        file: File,
        sha1: String
    ): CurseForgeFile? {
        val hash = MurmurHash2Incremental.computeHash(file, byteToSkip = listOf(0x9, 0xa, 0xd, 0x20))
        return httpPostJson<CurseForgeFingerprintsMatches>(
            url = "$api/fingerprints",
            body = mapOf("fingerprints" to listOf(hash))
        ).data.exactMatches
            ?.takeIf { it.isNotEmpty() }
            ?.firstOrNull()
            ?.file
    }

    /**
     * Batch-fetches the matching file info via multiple local files' CurseForge fingerprints
     * @return key = file fingerprint, value = matched file; missed fingerprints are excluded
     */
    suspend fun getFilesByFingerprints(
        fingerprints: List<Long>
    ): Map<Long, CurseForgeFile> {
        if (fingerprints.isEmpty()) return emptyMap()
        val matches = httpPostJson<CurseForgeFingerprintsMatches>(
            url = "$api/fingerprints",
            body = CurseForgeFingerprintsRequest(fingerprints = fingerprints)
        )
        return matches.data.exactMatches.orEmpty()
            .associate { it.file.fileFingerprint to it.file }
    }
}

/**
 * Request body for bulk fingerprint matching
 */
@Serializable
private data class CurseForgeFingerprintsRequest(
    @SerialName("fingerprints")
    val fingerprints: List<Long>
)

/**
 * Keeps paginating through a project's version files until everything is loaded
 * @param pageSize entries requested per page
 * @param chunkSize max pages per range
 * @param maxConcurrent max concurrent requests allowed
 * @param pageCallback callback invoked as each page loads
 * @param checkNotEmpty checks the response result is non-empty
 * @param asyncVersions fetches one block of version data asynchronously
 * @param processVersions processes returned data, also reporting the actual page size of the current result
 */
private suspend fun <E, T> getAllVersions(
    pageSize: Int = 100,
    chunkSize: Int = 10,
    maxConcurrent: Int = 5,
    pageCallback: (chunk: Int, page: Int) -> Unit = { _ , _ -> },
    checkNotEmpty: (E) -> Boolean,
    asyncVersions: suspend (index: Int, pageSize: Int) -> E,
    processVersions: suspend (E?) -> Pair<List<T>, Int>
): List<T> = withContext(Dispatchers.IO) {
    coroutineScope {
        val allVersions = mutableListOf<T>()
        /** Current range number */
        var currentChunk = 1
        /** Starting page number */
        var startPage = 0
        /** Whether the last page has been reached; controls advancing to the next range */
        var reachedEnd = false

        val semaphore = Semaphore(maxConcurrent)

        while (!reachedEnd) {
            //Create the task list for the current range
            val jobs = (0 until chunkSize).map { offset ->
                val pageIndex = startPage + offset
                val index = pageIndex * pageSize

                async {
                    semaphore.withPermit {
                        val response = asyncVersions(index, pageSize)
                        //Check the current page's result is sane
                        //Content beyond the last page yields an empty list here
                        if (checkNotEmpty(response)) {
                            //Non-empty: just invoke the callback
                            pageCallback(currentChunk, pageIndex + 1)
                            response
                        } else null
                    }
                }
            }

            for ((i, job) in jobs.withIndex()) {
                val (files, realSize) = processVersions(job.await())
                files.takeIf { it.isNotEmpty() }?.let { list ->
                    allVersions.addAll(list)
                }

                //Under pageSize: this was already the last page
                if (realSize < pageSize) {
                    reachedEnd = true
                    //Cancel the remaining pages
                    for (j in (i + 1) until jobs.size) {
                        jobs[j].cancel()
                    }
                    break
                }
            }

            //If no last page was found, advance to the next range
            if (!reachedEnd) {
                startPage += chunkSize
                currentChunk++
            }
        }

        return@coroutineScope allVersions
    }
}