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

package com.endiq.turtlelauncher.game.version.mod

import com.endiq.turtlelauncher.game.download.assets.platform.Platform
import com.endiq.turtlelauncher.game.download.assets.platform.getCFFilesByFingerprints
import com.endiq.turtlelauncher.game.download.assets.platform.getModrinthVersBySha1
import com.endiq.turtlelauncher.utils.logging.Logger
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "InstalledModMatcher"

/** Fingerprints per match batch; smaller batches cut per-response parsing memory significantly */
private const val MATCH_BATCH_SIZE = 25

/** Concurrent batch match requests */
private const val MATCH_PARALLELISM = 4

/**
 * Platform match result of local mod fingerprints
 * @param byProject keyed by platform project ID
 * @param byVersion keyed by platform version ID
 * @param complete whether every fingerprint chunk matched; failed chunks leave some fingerprints unmatched
 */
class MatchedInstalledMods(
    val byProject: Map<String, InstalledMod>,
    val byVersion: Map<String, InstalledMod>,
    val complete: Boolean
)

/**
 * Concurrently computes fingerprints of all files in the mods directory
 */
suspend fun scanModFingerprints(modsDir: File): List<ModFingerprints> =
    withContext(Dispatchers.IO) {
        val files = modsDir.listFiles()
            ?.filter { it.isFile && it.isModFileCandidate() }
            ?: return@withContext emptyList()

        val semaphore = Semaphore(READER_PARALLELISM)
        coroutineScope {
            files.map { file ->
                async {
                    semaphore.withPermit {
                        try {
                            computeModFingerprints(file)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            Logger.warning(TAG, "Failed to compute mod fingerprints: ${file.name}", e)
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

/**
 * Matches local mod fingerprints against the given platform, yielding locally installed mod info
 *
 * The persistent cache is read first; batch queries fire only for missed fingerprints;
 * each finished batch is written to the persistent cache at once and reported incrementally through [onCollect], so callers can sync to the UI live
 *
 * @param onCollect incremental match callback, always fired in order under the mutex, never concurrently
 */
suspend fun matchInstalledMods(
    fingerprints: List<ModFingerprints>,
    platform: Platform,
    onCollect: suspend (MatchedInstalledMods) -> Unit = {}
): MatchedInstalledMods {
    val mutex = Mutex()
    val byProject = mutableMapOf<String, InstalledMod>()
    val byVersion = mutableMapOf<String, InstalledMod>()
    if (fingerprints.isEmpty()) {
        return MatchedInstalledMods(byProject, byVersion, complete = true)
    }

    val pending = mutableListOf<InstalledMod>()
    var complete = true

    fun collect(installed: InstalledMod) {
        if (installed.notFound) return
        byProject[installed.projectId] = installed
        byVersion[installed.versionId] = installed
        pending.add(installed)
    }

    //Incrementally report matched results
    suspend fun flush() {
        if (pending.isEmpty()) return
        onCollect(
            MatchedInstalledMods(
                byProject = pending.associateBy { it.projectId },
                byVersion = pending.associateBy { it.versionId },
                complete = false
            )
        )
        pending.clear()
    }

    val cache = installedModCache()
    val uncached = mutableListOf<ModFingerprints>()
    mutex.withLock {
        for (print in fingerprints) {
            val cached = cache.decodeParcelable(print.cacheKey(platform), InstalledMod::class.java)
            if (cached != null) collect(cached) else uncached.add(print)
        }
        flush()
    }

    // Run batch queries concurrently (capped at [MATCH_PARALLELISM]); each returned batch is persisted and reported at once;
    // failed batches write no cache and wait for the next retry
    coroutineScope {
        val semaphore = Semaphore(MATCH_PARALLELISM)
        uncached.chunked(MATCH_BATCH_SIZE).map { chunk ->
            async {
                semaphore.withPermit {
                    val fetched = try {
                        when (platform) {
                            Platform.MODRINTH -> getModrinthVersBySha1(chunk.map { it.sha1 })
                            Platform.CURSEFORGE ->
                                getCFFilesByFingerprints(chunk.map { it.murmur2 }).mapKeys { it.key.toString() }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Logger.warning(TAG, "Failed to match installed mods on platform: $platform", e)
                        null
                    }

                    mutex.withLock {
                        if (fetched == null) {
                            complete = false
                        } else {
                            for (print in chunk) {
                                val installed = fetched[print.fingerprintValue(platform)]?.toInstalledMod()
                                    ?: notFoundMod(platform)
                                cache.encode(print.cacheKey(platform), installed, MMKV.ExpireInDay)
                                collect(installed)
                            }
                            flush()
                        }
                    }
                }
            }
        }.awaitAll()
    }

    val isComplete = mutex.withLock { complete }
    return MatchedInstalledMods(byProject, byVersion, isComplete)
}

/**
 * Possible mod file extensions (a .disabled suffix is stripped before checking)
 */
private val MOD_FILE_EXTENSIONS = setOf("jar", "zip", "litemod")

/**
 * Whether the file could be a mod; non-mod files skip fingerprinting
 */
private fun File.isModFileCandidate(): Boolean {
    val extension = if (isDisabled()) {
        File(nameWithoutExtension).extension
    } else {
        extension
    }
    return extension.lowercase() in MOD_FILE_EXTENSIONS
}

/**
 * The fingerprint's key in the persistent cache; fingerprints of different platforms stay independent
 */
private fun ModFingerprints.cacheKey(platform: Platform): String =
    "${platform.name}/${fingerprintValue(platform)}"

/**
 * The fingerprint's match key on its platform
 */
private fun ModFingerprints.fingerprintValue(platform: Platform): String = when (platform) {
    Platform.MODRINTH -> sha1
    Platform.CURSEFORGE -> murmur2.toString()
}

/**
 * Negative-cache flag for platform misses on the fingerprint
 */
private fun notFoundMod(platform: Platform): InstalledMod = InstalledMod(
    platform = platform,
    projectId = "",
    versionId = "",
    versionName = "",
    notFound = true
)
