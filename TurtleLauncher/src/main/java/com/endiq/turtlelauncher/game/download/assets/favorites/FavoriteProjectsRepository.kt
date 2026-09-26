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

package com.endiq.turtlelauncher.game.download.assets.favorites

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.endiq.turtlelauncher.game.download.assets.platform.Platform
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformClasses
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformProject
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformSearchData
import com.endiq.turtlelauncher.game.download.assets.platform.getProjectByVersion
import com.endiq.turtlelauncher.utils.logging.Logger
import io.ktor.client.plugins.ClientRequestException
import io.ktor.server.plugins.NotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Favorite key: platform + project ID uniquely identify one favorite
 */
data class FavoriteKey(
    val platform: Platform,
    val projectId: String
)

/**
 * Favorite project entry: local cache data plus loaded remote project data
 */
data class FavoriteEntry(
    val platform: Platform,
    val project: FavoriteProject,
    val remote: PlatformProject? = null,
    val invalid: Boolean = false
)

/**
 * Favorite projects repository
 */
object FavoriteProjectsRepository {
    private const val TAG = "FavoriteProjectsRepository"
    private const val REFRESH_CONCURRENCY = 8

    /** All favorited projects, keyed by [FavoriteKey] */
    val projects = mutableStateMapOf<FavoriteKey, FavoriteEntry>()

    /** Whether favorite data has finished loading */
    var initialized by mutableStateOf(false)
        private set

    //Repository-internal coroutine scope, hosting async work for non-suspending entries
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    //Serialize data loading and read/writes, avoiding state corruption from concurrent mutation
    private val mutex = Mutex()

    /**
     * Reads the in-memory pool only; triggers one async load when not yet loaded
     */
    fun isFavorite(platform: Platform, projectId: String): Boolean {
        if (!initialized) scope.launch { ensureLoaded() }
        return projects.containsKey(FavoriteKey(platform, projectId))
    }

    /**
     * Favorites a search result project
     */
    fun favorite(data: PlatformSearchData, classes: PlatformClasses) {
        scope.launch { saveFavorite(data.platform(), data.toFavoriteProject(classes)) }
    }

    /**
     * Favorites a remote project
     */
    fun favorite(project: PlatformProject, defaultClasses: PlatformClasses) {
        scope.launch { saveFavorite(project.platform(), project.toFavoriteProject(defaultClasses)) }
    }

    fun unfavorite(platform: Platform, projectId: String) {
        scope.launch { removeFavorite(platform, projectId) }
    }

    /**
     * Toggles a search result project's favorite state
     */
    fun toggle(data: PlatformSearchData, classes: PlatformClasses) {
        scope.launch {
            val platform = data.platform()
            val projectId = data.platformId()
            if (checkFavorite(platform, projectId)) {
                removeFavorite(platform, projectId)
            } else {
                saveFavorite(platform, data.toFavoriteProject(classes))
            }
        }
    }

    /**
     * Toggles a remote project's favorite state
     */
    fun toggle(project: PlatformProject, defaultClasses: PlatformClasses) {
        scope.launch {
            val platform = project.platform()
            val projectId = project.platformId()
            if (checkFavorite(platform, projectId)) {
                removeFavorite(platform, projectId)
            } else {
                saveFavorite(platform, project.toFavoriteProject(defaultClasses))
            }
        }
    }

    /**
     * Ensures favorite data is loaded
     */
    suspend fun ensureLoaded() {
        if (initialized) return
        mutex.withLock {
            if (!initialized) reloadLocked()
        }
    }

    /**
     * Reloads favorite data from MMKV
     */
    suspend fun reload() = mutex.withLock {
        reloadLocked()
    }

    /**
     * Concurrently refreshes the remote data of all favorited projects, updating memory and the local cache entry by entry
     */
    suspend fun refreshRemote() = coroutineScope {
        val pending = projects.values.toList()
        if (pending.isEmpty()) return@coroutineScope

        val semaphore = Semaphore(REFRESH_CONCURRENCY)
        pending.map { entry ->
            async {
                semaphore.withPermit { refreshEntry(entry) }
            }
        }.awaitAll()
    }

    /**
     * Loads the data; must be called while holding the mutex
     */
    private suspend fun reloadLocked() {
        val latest = withContext(Dispatchers.IO) { readAll() }
        if (!initialized) {
            initialized = true
            projects.clear()
            projects.putAll(latest)
        } else {
            latest.forEach { (key, entry) ->
                if (!projects.containsKey(key)) projects[key] = entry
            }
            (projects.keys - latest.keys).forEach(projects::remove)
        }
    }

    private suspend fun checkFavorite(platform: Platform, projectId: String): Boolean {
        ensureLoaded()
        return projects.containsKey(FavoriteKey(platform, projectId))
    }

    private suspend fun saveFavorite(platform: Platform, project: FavoriteProject) {
        mutex.withLock {
            if (!initialized) reloadLocked()
            withContext(Dispatchers.IO) {
                favoritesMMKV(platform).encode(project.projectId, project)
            }
            projects[FavoriteKey(platform, project.projectId)] = FavoriteEntry(platform, project)
        }
    }

    private suspend fun removeFavorite(platform: Platform, projectId: String) {
        mutex.withLock {
            if (!initialized) reloadLocked()
            withContext(Dispatchers.IO) {
                favoritesMMKV(platform).remove(projectId)
            }
            projects.remove(FavoriteKey(platform, projectId))
        }
    }

    private suspend fun refreshEntry(entry: FavoriteEntry) {
        val key = FavoriteKey(entry.platform, entry.project.projectId)
        try {
            val remote = getProjectByVersion(
                projectId = entry.project.projectId,
                platform = entry.platform,
                printLog = false
            )
            //Entries may be removed during the refresh; only update those still present
            mutex.withLock {
                projects[key]?.let { current ->
                    if (!remote.platformAvailable()) {
                        //The project was marked invisible by the platform (e.g. deleted); mark the entry stale
                        markInvalid(key, current)
                    } else {
                        val merged = mergeCache(current.project, remote)
                        projects[key] = current.copy(project = merged, remote = remote, invalid = false)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (e.isProjectNotFound()) {
                //The remote project is no longer accessible; mark the entry stale
                mutex.withLock {
                    projects[key]?.let { current -> markInvalid(key, current) }
                }
            } else {
                Logger.warning(TAG, "Failed to refresh favorite project: ${key.platform}/${key.projectId}", e)
            }
        }
    }

    private fun markInvalid(key: FavoriteKey, current: FavoriteEntry) {
        if (current.invalid) return
        projects[key] = current.copy(invalid = true)
    }

    /**
     * Whether the platform API returned project-not-found
     */
    private fun Throwable.isProjectNotFound(): Boolean =
        this is NotFoundException || (this is ClientRequestException && response.status.value == 404)

    /**
     * Corrects the local cache with remote data and writes back to MMKV on changes; the favorite time is kept
     */
    private suspend fun mergeCache(cached: FavoriteProject, remote: PlatformProject): FavoriteProject {
        val classes = remote.platformClasses(cached.classes)
        val iconUrl = remote.platformIconUrl()
        val title = remote.platformTitle()
        val description = remote.platformSummary() ?: ""
        val authors = remote.platformAuthors()

        val unchanged = classes == cached.classes &&
                iconUrl == cached.iconUrl &&
                title == cached.title &&
                description == cached.description &&
                authors == cached.authors
        if (unchanged) return cached

        val merged = FavoriteProject(
            projectId = cached.projectId,
            iconUrl = iconUrl,
            title = title,
            description = description,
            authors = authors,
            classes = classes,
            followTime = cached.followTime
        )
        withContext(Dispatchers.IO) {
            favoritesMMKV(remote.platform()).encode(merged.projectId, merged)
        }
        return merged
    }

    private fun readAll(): Map<FavoriteKey, FavoriteEntry> {
        val result = mutableMapOf<FavoriteKey, FavoriteEntry>()
        Platform.entries.forEach { platform ->
            val mmkv = favoritesMMKV(platform)
            mmkv.allKeys()?.forEach { key ->
                runCatching {
                    mmkv.decodeParcelable(key, FavoriteProject::class.java)
                }.getOrNull()?.let { project ->
                    result[FavoriteKey(platform, project.projectId)] = FavoriteEntry(platform, project)
                }
            }
        }
        return result
    }
}

/**
 * Builds the favorite cache from search result data
 */
fun PlatformSearchData.toFavoriteProject(classes: PlatformClasses): FavoriteProject = FavoriteProject(
    projectId = platformId(),
    iconUrl = platformIconUrl(),
    title = platformTitle(),
    description = platformDescription(),
    authors = platformAuthors(),
    classes = classes,
    followTime = System.currentTimeMillis()
)

/**
 * Builds the favorite cache from remote project data
 */
fun PlatformProject.toFavoriteProject(defaultClasses: PlatformClasses): FavoriteProject = FavoriteProject(
    projectId = platformId(),
    iconUrl = platformIconUrl(),
    title = platformTitle(),
    description = platformSummary() ?: "",
    authors = platformAuthors(),
    classes = platformClasses(defaultClasses),
    followTime = System.currentTimeMillis()
)
