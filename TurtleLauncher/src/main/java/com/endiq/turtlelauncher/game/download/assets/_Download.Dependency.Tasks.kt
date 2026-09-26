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

package com.endiq.turtlelauncher.game.download.assets

import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.coroutine.Task
import com.endiq.turtlelauncher.coroutine.TaskSystem
import com.endiq.turtlelauncher.game.addons.modloader.ModLoader
import com.endiq.turtlelauncher.game.download.assets.platform.Platform
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformClasses
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformDependencyType
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformDisplayLabel
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformProject
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformVersion
import com.endiq.turtlelauncher.game.download.assets.platform.curseforge.models.CurseForgeModLoader
import com.endiq.turtlelauncher.game.download.assets.platform.getProjectByVersion
import com.endiq.turtlelauncher.game.download.assets.platform.getVersionById
import com.endiq.turtlelauncher.game.download.assets.platform.getVersions
import com.endiq.turtlelauncher.game.download.assets.platform.modrinth.models.ModrinthModLoaderCategory
import com.endiq.turtlelauncher.game.version.installed.Version
import com.endiq.turtlelauncher.game.version.installed.VersionFolders
import com.endiq.turtlelauncher.game.version.mod.matchInstalledMods
import com.endiq.turtlelauncher.game.version.mod.scanModFingerprints
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.initAll
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.viewmodel.ErrorViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "DownloadDependency"

/** Max dependencies processed within one dependency install, preventing a dependency explosion from abnormal metadata */
private const val MAX_DEPENDENCY_PROJECTS = 64

/** Max dependencies resolved concurrently */
private const val DEPENDENCY_PARALLELISM = 4

/**
 * Dependencies that must be installed alongside
 * @param projectId the dependency project's platform ID
 * @param versionId exact version ID of the dependency; null means only the project was specified
 * @param classes resource class of the dependency, deciding its install directory
 * @param projectTitle dependency project title, used in notices
 */
class DependencyRequest(
    val platform: Platform,
    val projectId: String,
    val versionId: String?,
    val classes: PlatformClasses,
    val projectTitle: String
)

/**
 * Installs the selected dependencies for the given game version, recursively expanding the required dependencies they declare
 * @param requests the dependencies to install
 * @param versions the target game versions of the dependencies
 */
fun downloadDependenciesForVersions(
    requests: List<DependencyRequest>,
    versions: List<Version>,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
) {
    if (requests.isEmpty() || versions.isEmpty()) return

    // Deduplicate task IDs
    val distinctRequests = requests.distinctBy { "${it.platform.name}/${it.projectId}/${it.versionId.orEmpty()}" }
    val taskId = distinctRequests
        .map { "${it.projectId}@${it.versionId.orEmpty()}" }
        .sorted()
        .joinToString(",")
        .hashCode()

    TaskSystem.submitTask(
        Task.runTask(
            id = "dependency/${distinctRequests.first().platform.name}/$taskId",
            task = { task ->
                task.updateMessage(
                    androidText(R.string.download_assets_loading_dep_project, distinctRequests.first().projectId)
                )

                val context = DependencyContext(
                    task = task,
                    semaphore = Semaphore(DEPENDENCY_PARALLELISM),
                    processed = ConcurrentHashMap<String, MutableSet<String>>(),
                    projectCache = ConcurrentHashMap<String, PlatformProject>(),
                    budget = AtomicInteger(MAX_DEPENDENCY_PROJECTS),
                    downloadGroups = ConcurrentHashMap<String, DownloadGroup>(),
                    failures = DependencyFailures()
                )

                coroutineScope {
                    distinctRequests.forEach { request ->
                        async { expand(request, versions, context) }
                    }
                }

                context.publishDownloads(submitError)
                context.failures.submit(submitError)
            }
        )
    )
}

/**
 * Shared context while expanding the dependency graph
 */
private class DependencyContext(
    val task: Task,
    val semaphore: Semaphore,
    /** Target game versions already processed per dependency project, keyed by project key */
    val processed: ConcurrentHashMap<String, MutableSet<String>>,
    /** Dependency project info cache, avoiding repeated queries */
    val projectCache: ConcurrentHashMap<String, PlatformProject>,
    /** Remaining processable dependency count */
    val budget: AtomicInteger,
    /** Resolved download groups, keyed by platform version key */
    val downloadGroups: ConcurrentHashMap<String, DownloadGroup>,
    val failures: DependencyFailures
) {
    /** Locally installed mod projects per target game version */
    val installedMemo = ConcurrentHashMap<String, Set<String>>()
    val installedMutex = Mutex()

    /**
     * Claims the unprocessed target game versions of a dependency project
     * @return the unprocessed target game versions; empty means the project needs no further work
     */
    fun claimTargets(key: String, targets: List<Version>): List<Version> {
        val claimed = processed[key] ?: run {
            if (budget.getAndDecrement() <= 0) {
                Logger.warning(TAG, "The dependency limit of $MAX_DEPENDENCY_PROJECTS has been reached, stopping the expansion.")
                return emptyList()
            }
            processed.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }
        }
        return targets.filter { claimed.add(it.getVersionName()) }
    }
}

/**
 * Dependency versions downloaded alongside, with their target game versions
 * @param folder path relative to the game directory to install into
 */
private class DownloadGroup(
    val version: PlatformVersion,
    val folder: String
) {
    private val targets = mutableListOf<Version>()

    fun addTargets(versions: List<Version>) = synchronized(targets) {
        versions.forEach { if (it !in targets) targets.add(it) }
    }

    fun targetList(): List<Version> = synchronized(targets) { targets.toList() }
}

/**
 * Dependency failure info: key = project title, value = the target game version for which no compatible version was found
 */
private class DependencyFailures {
    private val byProject = ConcurrentHashMap<String, MutableList<String>>()

    fun record(title: String, versionName: String) {
        val names = byProject.computeIfAbsent(title) { mutableListOf() }
        synchronized(names) {
            if (!names.contains(versionName)) names.add(versionName)
        }
    }

    fun submit(submitError: (ErrorViewModel.ThrowableMessage) -> Unit) {
        val snapshot = byProject.mapValues { it.value.toList() }.toSortedMap()
        if (snapshot.isEmpty()) return

        snapshot.forEach { (title, versionNames) ->
            Logger.warning(TAG, "No compatible version found for the dependency: $title, ${versionNames.joinToString()}")
        }

        val texts = snapshot.keys.flatMapIndexed { index, title ->
            buildList {
                if (index > 0) add(androidText("\n"))
                add(androidText(R.string.download_assets_dependency_no_compatible_version, title))
            }
        }
        submitError(
            ErrorViewModel.ThrowableMessage(
                title = androidText(R.string.download_assets_install_failed),
                message = androidText(*texts.toTypedArray())
            )
        )
    }
}

private fun projectKey(
    platform: Platform,
    projectId: String
): String = "${platform.name}/$projectId"

/**
 * Runs a dependency resolution step; logs and returns null on failure
 */
private suspend fun <T> resolveOrNull(
    description: String,
    block: suspend () -> T
): T? {
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Logger.warning(TAG, description, e)
        null
    }
}

/**
 * Expands one dependency
 * Resolves the concrete version, registers download tasks, and recursively expands that version's required dependencies
 */
private suspend fun expand(
    request: DependencyRequest,
    targets: List<Version>,
    context: DependencyContext
) {
    // Only process target game versions not yet processed for this project: avoids duplicate work and breaks dependency cycles
    val pendingTargets = context.claimTargets(projectKey(request.platform, request.projectId), targets)
    if (pendingTargets.isEmpty()) return

    try {
        val folder = request.classes.versionFolder
        if (folder == VersionFolders.NONE) {
            // The dependency's install directory can't be determined
            pendingTargets.forEach {
                context.failures.record(request.projectTitle, it.getVersionName())
            }
            return
        }

        // Only mod-type dependencies skip locally installed targets; an installed dependency's own dependencies are not expanded
        val installTargets = if (request.classes == PlatformClasses.MOD) {
            pendingTargets.filterNot { context.isInstalled(request, it) }
        } else {
            pendingTargets
        }
        if (installTargets.isEmpty()) return

        val resolved = context.semaphore.withPermit {
            context.resolve(request, installTargets)
        }
        resolved.forEach { (version, group) ->
            context.registerDownload(request, version, group)
        }

        // Recursively expand the required dependencies of resolved versions
        // The semaphore is only held during resolution and released before entering subtrees, preventing deadlocks from recursive permit waits
        resolved.forEach { (version, group) ->
            val dependencies = resolveOrNull("Failed to read the dependencies of: ${request.projectTitle}") {
                version.platformDependencies()
            } ?: return@forEach
            val required = dependencies.filter { it.type == PlatformDependencyType.REQUIRED }
            if (required.isEmpty()) return@forEach

            coroutineScope {
                required.forEach { dependency ->
                    val child = context.expandChild(dependency, request) ?: return@forEach
                    async { expand(child, group, context) }
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Logger.warning(TAG, "An error occurred while installing the dependency: ${request.projectTitle}", e)
        pendingTargets.forEach {
            context.failures.record(request.projectTitle, it.getVersionName())
        }
    }
}

/**
 * Resolves the concrete version of a dependency on the target game version
 * @return each concrete version with its corresponding target game version
 */
private suspend fun DependencyContext.resolve(
    request: DependencyRequest,
    targets: List<Version>
): List<Pair<PlatformVersion, List<Version>>> {
    task.updateMessage(
        androidText(R.string.download_assets_loading_dep_project, request.projectId)
    )

    if (request.versionId != null) {
        // The main resource pinned an exact version: download that version directly
        val version = resolveOrNull("Failed to retrieve the dependency version: ${request.versionId}") {
            getVersionById(
                versionId = request.versionId,
                platform = request.platform,
                printLog = false
            )
        }
        if (version == null || !version.initFile(request.projectId)) {
            targets.forEach {
                failures.record(request.projectTitle, it.getVersionName())
            }
            return emptyList()
        }
        return listOf(version to targets)
    }

    // Fetch all versions of the dependency project, then pick locally per target game version
    // initAll sorts by release time descending, so the first compatible one is the newest
    val allVersions = resolveOrNull("Failed to retrieve the dependency versions: ${request.projectId}") {
        getVersions(
            projectID = request.projectId,
            platform = request.platform
        ).initAll(request.projectId)
    }
    if (allVersions == null) {
        targets.forEach {
            failures.record(request.projectTitle, it.getVersionName())
        }
        return emptyList()
    }

    return targets
        .groupBy { target -> allVersions.firstOrNull { it.isCompatibleWith(target, request.classes) } }
        .mapNotNull { (version, group) ->
            if (version == null) {
                group.forEach {
                    failures.record(request.projectTitle, it.getVersionName())
                }
                null
            } else {
                version to group
            }
        }
}

/**
 * Converts discovered transitive dependencies into installable ones
 * Each project is processed only once across the whole dependency graph
 */
private suspend fun DependencyContext.expandChild(
    dependency: PlatformVersion.PlatformDependency,
    parent: DependencyRequest
): DependencyRequest? = semaphore.withPermit {
    val projectId = dependency.projectId ?: run {
        val versionId = dependency.versionId ?: return@withPermit null
        resolveOrNull("Failed to resolve the project of the dependency version: $versionId") {
            getVersionById(versionId, dependency.platform, printLog = false).platformProjectId()
        } ?: return@withPermit null
    }

    val (classes, title) = resolveProject(dependency.platform, projectId, parent.classes)
    if (classes.versionFolder == VersionFolders.NONE) return@withPermit null

    DependencyRequest(
        platform = dependency.platform,
        projectId = projectId,
        versionId = dependency.versionId,
        classes = classes,
        projectTitle = title
    )
}

/**
 * Fetches the dependency project's category and title
 * On lookup failure fall back to the parent dependency's category; the category only affects the install directory and must not break the chain
 */
private suspend fun DependencyContext.resolveProject(
    platform: Platform,
    projectId: String,
    fallbackClasses: PlatformClasses
): Pair<PlatformClasses, String> {
    projectCache[projectId]?.let { project ->
        return project.platformClasses(fallbackClasses) to project.platformTitle()
    }

    val project = resolveOrNull("Failed to retrieve the dependency project information: $projectId") {
        getProjectByVersion(projectId, platform, printLog = false)
    } ?: return fallbackClasses to projectId

    projectCache[projectId] = project
    return project.platformClasses(fallbackClasses) to project.platformTitle()
}

private fun DependencyContext.registerDownload(
    request: DependencyRequest,
    version: PlatformVersion,
    targets: List<Version>
) {
    val key = "${version.platform().name}/${version.platformId()}"
    downloadGroups.computeIfAbsent(key) {
        DownloadGroup(version, request.classes.versionFolder.folderName)
    }.addTargets(targets)
}

/**
 * After the dependency graph is fully expanded, publish the download tasks together
 */
private fun DependencyContext.publishDownloads(
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit
) {
    downloadGroups.toSortedMap().values.forEach { group ->
        val targets = group.targetList()
        if (targets.isEmpty()) return@forEach
        downloadSingleForVersions(
            version = group.version,
            versions = targets,
            folder = group.folder,
            submitError = submitError
        )
    }
}

/**
 * Whether the dependency project is already installed in the target version's mods directory
 * Check failures are treated as not-installed, to avoid missing installations
 */
private suspend fun DependencyContext.isInstalled(
    request: DependencyRequest,
    target: Version
): Boolean {
    val key = "${request.platform.name}/${target.getVersionName()}"
    installedMemo[key]?.let { return request.projectId in it }

    val projects = installedMutex.withLock {
        installedMemo[key] ?: resolveOrNull("Failed to check whether the dependency is installed locally: ${request.projectTitle}") {
            val modsDir = VersionFolders.MOD.getDir(target.getGameDir())
            matchInstalledMods(
                fingerprints = scanModFingerprints(modsDir),
                platform = request.platform
            ).byProject.keys.toSet()
        }?.also { installedMemo[key] = it }
    }
    return projects?.contains(request.projectId) == true
}

/**
 * Whether the dependency version fits the target game version
 */
private fun PlatformVersion.isCompatibleWith(
    target: Version,
    classes: PlatformClasses
): Boolean {
    val info = target.getVersionInfo() ?: return true
    if (info.minecraftVersion !in platformGameVersion()) return false

    // Only mod dependencies need loader validation
    if (classes != PlatformClasses.MOD) return true
    val targetLoader = info.loaderInfo?.loader?.toPlatformLoader(platform()) ?: return true
    val versionLoaders = platformLoaders()
    return versionLoaders.isEmpty() || targetLoader in versionLoaders
}

/**
 * Converts a local mod loader into the platform's loader identifier
 */
private fun ModLoader.toPlatformLoader(platform: Platform): PlatformDisplayLabel? {
    if (displayName.isEmpty()) return null
    return when (platform) {
        Platform.MODRINTH -> ModrinthModLoaderCategory.entries.find {
            it.getDisplayName().equals(displayName, true)
        }
        Platform.CURSEFORGE -> CurseForgeModLoader.entries.find {
            it.getDisplayName().equals(displayName, true)
        }
    }
}
