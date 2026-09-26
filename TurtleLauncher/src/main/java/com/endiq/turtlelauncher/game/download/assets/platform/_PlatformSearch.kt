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

import android.util.Log
import com.endiq.turtlelauncher.game.addons.mirror.MirrorPriority
import com.endiq.turtlelauncher.game.addons.mirror.resolveMirrorPriority
import com.endiq.turtlelauncher.game.download.assets.mapExceptionToMessage
import com.endiq.turtlelauncher.game.download.assets.platform.curseforge.CurseForgeSearcher
import com.endiq.turtlelauncher.game.download.assets.platform.curseforge.MCIM_CURSEFORGE_API
import com.endiq.turtlelauncher.game.download.assets.platform.curseforge.models.CurseForgeFile
import com.endiq.turtlelauncher.game.download.assets.platform.modrinth.MCIM_MODRINTH_API
import com.endiq.turtlelauncher.game.download.assets.platform.modrinth.ModrinthSearcher
import com.endiq.turtlelauncher.game.download.assets.platform.modrinth.models.ModrinthVersion
import com.endiq.turtlelauncher.game.download.assets.utils.localizedModSearchKeywords
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.DownloadAssetsState
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.SearchAssetsState
import com.endiq.turtlelauncher.utils.isChinaMainland
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.network.isInterruptedIOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

private const val TAG = "PlatformSearch"

private val modrinthSearcher = ModrinthSearcher()
private val mirrorModrinthSearcher = ModrinthSearcher(
    api = MCIM_MODRINTH_API,
    source = "MCIM Modrinth"
)

private val curseForgeSearcher = CurseForgeSearcher()
private val mirrorCurseForgeSearcher = CurseForgeSearcher(
    api = MCIM_CURSEFORGE_API,
    source = "MCIM CurseForge"
)

/**
 * Runs resource platform searches through the mirror-source mechanism
 */
suspend fun <E: AbstractPlatformSearcher, T> mirroredPlatformSearcher(
    searchers: List<E>,
    printLog: Boolean = true,
    block: suspend (E) -> T
): T {
    require(searchers.isNotEmpty()) { "Searcher list must not be empty." }

    val errors = mutableListOf<Exception>()
    var lastException: Exception? = null

    for (searcher in searchers) {
        try {
            if (printLog) {
                Logger.debug(TAG, "Starting to attempt to perform the operation on source: {${searcher.source}}")
            }
            return block(searcher)
        } catch (e: Exception) {
            Log.w("PlatformSearcher", "Failed to perform the operation on source: {${searcher.source}}", e)
            lastException = e

            if (e.isInterruptedIOException()) {
                throw e
            } else if (e is FileNotFoundException) {
                errors.add(e)
                break
            } else {
                errors.add(e)
            }
        }
    }

    if (printLog) {
        Logger.warning(TAG, 
            msg = "An error occurred during this search.",
            t = IOException("All sources have failed to attempt", lastException).apply {
                errors.forEachIndexed { i, e ->
                    addSuppressed(Exception("Mirror error #${i + 1}: ${e.message}"))
                }
            }
        )
    }
    throw lastException ?: IllegalStateException("Should not have executed to this stage.")
}

/**
 * Mirror sources are only usable in China
 */
fun mirroredCurseForgeSource(
    enabledMirror: Boolean = isChinaMainland()
): List<CurseForgeSearcher> {
    val source = resolveMirrorPriority(AllSettings.assetPlatformSource.getValue(), mainland = enabledMirror)
    val mirrorSource = mirrorCurseForgeSearcher.takeIf { enabledMirror }
    return when (source) {
        MirrorPriority.OFFICIAL -> listOf(curseForgeSearcher)
        MirrorPriority.MIRROR_FIRST ->
            listOfNotNull(mirrorSource, curseForgeSearcher)
    }
}

/**
 * Mirror sources are only usable in China
 */
fun mirroredModrinthSource(
    enabledMirror: Boolean = isChinaMainland()
): List<ModrinthSearcher> {
    val source = resolveMirrorPriority(AllSettings.assetPlatformSource.getValue(), mainland = enabledMirror)
    val mirrorSource = mirrorModrinthSearcher.takeIf { enabledMirror }
    return when (source) {
        MirrorPriority.OFFICIAL -> listOf(modrinthSearcher)
        MirrorPriority.MIRROR_FIRST ->
            listOfNotNull(mirrorSource, modrinthSearcher)
    }
}

suspend fun searchAssets(
    searchPlatform: Platform,
    searchFilter: PlatformSearchFilter,
    platformClasses: PlatformClasses,
    onSuccess: suspend (PlatformSearchResult) -> Unit,
    onError: (SearchAssetsState.Error) -> Unit
) {
    runCatching {
        val (containsChinese, englishKeywords) = searchFilter.searchName.localizedModSearchKeywords(platformClasses)
        //Referenced source: [HMCL Github](https://github.com/HMCL-dev/HMCL/blob/d295e60/HMCL/src/main/java/org/jackhuang/hmcl/game/LocalizedRemoteModRepository.java#L56-L68)
        //Try each English phrase in turn; take the first with non-empty results
        val queries = englishKeywords?.takeIf { it.isNotEmpty() }?.toList()
            ?: listOf(searchFilter.searchName)
        //Referenced source: [HMCL Github](https://github.com/HMCL-dev/HMCL/blob/8767cc0e/HMCL/src/main/java/org/jackhuang/hmcl/game/LocalizedRemoteAddonRepository.java)
        //Translated English phrases always search with relevance sorting, so the chosen order can't push the target resource off the page
        val searchFilterForQuery = englishKeywords?.takeIf { it.isNotEmpty() }
            ?.let { searchFilter.copy(sortField = PlatformSortField.RELEVANCE) }
            ?: searchFilter

        var lastResult: PlatformSearchResult? = null
        var lastException: Exception? = null
        for (query in queries) {
            try {
                val r = when (searchPlatform) {
                    Platform.CURSEFORGE -> mirroredPlatformSearcher(
                        searchers = mirroredCurseForgeSource(),
                        printLog = false
                    ) { searcher ->
                        searcher.searchAssets(
                            query = query,
                            searchFilter = searchFilterForQuery,
                            platformClasses = platformClasses
                        )
                    }
                    Platform.MODRINTH -> mirroredPlatformSearcher(
                        searchers = mirroredModrinthSource(),
                        printLog = false
                    ) { searcher ->
                        searcher.searchAssets(
                            query = query,
                            searchFilter = searchFilterForQuery,
                            platformClasses = platformClasses
                        )
                    }
                }
                lastResult = r
                if (r.getAssetsPage(platformClasses).data.isNotEmpty()) break
            } catch (e: Exception) {
                //The current keyword failed; log it and try the next one
                lastException = e
            }
        }

        val result = lastResult ?: throw lastException ?: IOException("Failed to search for all queries")

        onSuccess(
            if (containsChinese) result.processChineseSearchResults(searchFilter.searchName, platformClasses)
            else result
        )
    }.onFailure { e ->
        if (e !is CancellationException) {
            Logger.error(TAG, "An exception occurred while searching for assets.", e)
            val state = SearchAssetsState.Error(mapExceptionToMessage(e))
            onError(state)
        } else {
            Logger.debug(TAG, "The search task has been cancelled.")
        }
    }
}

suspend fun getVersions(
    projectID: String,
    platform: Platform,
    pageCallback: (chunk: Int, page: Int) -> Unit = { _, _ -> },
) = when (platform) {
    Platform.CURSEFORGE -> mirroredPlatformSearcher(
        searchers = mirroredCurseForgeSource()
    ) { searcher ->
        searcher.getVersions(
            projectID = projectID,
            pageCallback = pageCallback
        )
    }
    Platform.MODRINTH -> mirroredPlatformSearcher(
        searchers = mirroredModrinthSource()
    ) { searcher ->
        searcher.getVersions(
            projectID = projectID,
            pageCallback = pageCallback
        )
    }
}

suspend fun <E> getVersions(
    projectID: String,
    platform: Platform,
    pageCallback: (chunk: Int, page: Int) -> Unit = { _, _ -> },
    onSuccess: suspend (List<PlatformVersion>) -> Unit,
    onError: (DownloadAssetsState<List<E>>) -> Unit
) {
    runCatching {
        val result = getVersions(projectID, platform, pageCallback)
        onSuccess(result)
    }.onFailure { e ->
        if (e !is CancellationException) {
            Logger.error(TAG, "An exception occurred while retrieving the project version.", e)
            val state = DownloadAssetsState.Error<List<E>>(mapExceptionToMessage(e))
            onError(state)
        } else {
            Logger.debug(TAG, "The version retrieval task has been cancelled.")
        }
    }
}

suspend fun <E> getProject(
    projectID: String,
    platform: Platform,
    onSuccess: (PlatformProject) -> Unit,
    onError: (DownloadAssetsState<E>, Throwable) -> Unit
) {
    runCatching {
        when (platform) {
            Platform.CURSEFORGE -> mirroredPlatformSearcher(
                searchers = mirroredCurseForgeSource()
            ) { searcher ->
                searcher.getProject(projectID)
            }
            Platform.MODRINTH -> mirroredPlatformSearcher(
                searchers = mirroredModrinthSource()
            ) { searcher ->
                searcher.getProject(projectID)
            }
        }
    }.fold(
        onSuccess = onSuccess,
        onFailure = { e ->
            if (e !is CancellationException) {
                Logger.error(TAG, "An exception occurred while retrieving project information.", e)
                val state = DownloadAssetsState.Error<E>(mapExceptionToMessage(e))
                onError(state, e)
            } else {
                Logger.debug(TAG, "The project retrieval task has been cancelled.")
            }
        }
    )
}

suspend fun getProjectByVersion(
    projectId: String,
    platform: Platform,
    printLog: Boolean = true
): PlatformProject = withContext(Dispatchers.IO) {
    when (platform) {
        Platform.MODRINTH -> mirroredPlatformSearcher(
            searchers = mirroredModrinthSource(),
            printLog = printLog
        ) { searcher ->
            searcher.getProject(projectId)
        }
        Platform.CURSEFORGE -> mirroredPlatformSearcher(
            searchers = mirroredCurseForgeSource(),
            printLog = printLog
        ) { searcher ->
            searcher.getProject(projectId)
        }
    }
}

/**
 * Fetches a single version from the given platform
 * @param versionId the version's platform ID
 */
suspend fun getVersionById(
    versionId: String,
    platform: Platform,
    printLog: Boolean = true
): PlatformVersion = withContext(Dispatchers.IO) {
    when (platform) {
        Platform.MODRINTH -> mirroredPlatformSearcher(
            searchers = mirroredModrinthSource(),
            printLog = printLog
        ) { searcher ->
            searcher.getVersion(versionId)
        }
        Platform.CURSEFORGE -> error("CurseForge dependencies do not carry a version id.")
    }
}

suspend fun getVersionByLocalFile(file: File, sha1: String): PlatformVersion? = withContext(Dispatchers.IO) {
    coroutineScope {
        val modrinthDeferred = async(Dispatchers.IO) {
            runCatching {
                mirroredPlatformSearcher(
                    searchers = mirroredModrinthSource(),
                    printLog = false
                ) { searcher ->
                    searcher.getVersionByLocalFile(file, sha1)
                }
            }.getOrNull()
        }

        val curseForgeDeferred = async(Dispatchers.IO) {
            runCatching {
                mirroredPlatformSearcher(
                    searchers = mirroredCurseForgeSource(),
                    printLog = false
                ) { searcher ->
                    searcher.getVersionByLocalFile(file, sha1)
                }
            }.getOrNull()
        }

        val result = select {
            modrinthDeferred.onAwait { result ->
                if (result != null) {
                    curseForgeDeferred.cancel()
                    result
                } else {
                    null
                }
            }
            curseForgeDeferred.onAwait { result ->
                if (result != null) {
                    modrinthDeferred.cancel()
                    result
                } else {
                    null
                }
            }
        }

        result ?: run {
            if (!modrinthDeferred.isCompleted) modrinthDeferred.await()
            else if (!curseForgeDeferred.isCompleted) curseForgeDeferred.await()
            else null
        }
    }
}

/** Max fingerprints per batch match, keeping single requests reasonably sized */
const val FINGERPRINT_BATCH_SIZE = 100

/**
 * Batch-fetches matching Modrinth versions via local files' SHA-1 values
 * @param sha1List SHA-1 list, at most [FINGERPRINT_BATCH_SIZE] entries
 * @return key = SHA-1, value = matched version; missed fingerprints are excluded
 */
suspend fun getModrinthVersBySha1(
    sha1List: List<String>
): Map<String, ModrinthVersion> = mirroredPlatformSearcher(
    searchers = mirroredModrinthSource(),
    printLog = false
) { searcher ->
    searcher.getVersionFiles(sha1List = sha1List)
}

/**
 * Batch-fetches matching CurseForge files via local files' CurseForge fingerprints
 * @param fingerprints fingerprint list, at most [FINGERPRINT_BATCH_SIZE] entries
 * @return key = file fingerprint, value = matched file; missed fingerprints are excluded
 */
suspend fun getCFFilesByFingerprints(
    fingerprints: List<Long>
): Map<Long, CurseForgeFile> = mirroredPlatformSearcher(
    searchers = mirroredCurseForgeSource(),
    printLog = false
) { searcher ->
    searcher.getFilesByFingerprints(fingerprints = fingerprints)
}