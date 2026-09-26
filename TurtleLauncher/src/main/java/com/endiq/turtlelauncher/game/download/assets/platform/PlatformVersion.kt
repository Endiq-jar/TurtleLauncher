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

import java.time.Instant

/**
 * Platform version implementation; [initFile] must be called before use
 */
interface PlatformVersion {
    /**
     * Initializes version data; some versions need extra steps to finish
     * @param currentProjectId the project ID this version belongs to, assisting initialization
     * @return whether initialization succeeded; failed versions may be skipped
     */
    suspend fun initFile(currentProjectId: String): Boolean

    /**
     * Owning platform
     */
    fun platform(): Platform

    /**
     * The version's platform ID
     */
    fun platformId(): String

    /**
     * The project ID the version belongs to on the platform
     */
    fun platformProjectId(): String

    /**
     * The version's display name on the platform
     */
    fun platformDisplayName(): String

    /**
     * The version's file name
     */
    fun platformFileName(): String

    /**
     * Compatible major game versions
     */
    fun platformGameVersion(): Array<String>

    /**
     * Supported loaders marked on the platform
     */
    fun platformLoaders(): List<PlatformDisplayLabel>

    /**
     * The version's release type on the platform
     */
    fun platformReleaseType(): PlatformReleaseType

    /**
     * Projects this version depends on
     */
    fun platformDependencies(): List<PlatformDependency>

    /**
     * The version's total downloads on the platform
     */
    fun platformDownloadCount(): Long

    /**
     * The version's download URL
     */
    fun platformDownloadUrl(): String

    /**
     * The version's release date on the platform
     */
    fun platformDatePublished(): Instant

    /**
     * The version's file SHA-1
     */
    fun platformSha1(): String?

    /**
     * The version's file size
     */
    fun platformFileSize(): Long

    /**
     * The version number name marked on the platform
     */
    fun platformVersion(): String

    /**
     * Platform version dependency, keeping the key info of a dependency
     * @param projectId dependency project ID; null when the platform only gave an exact version ID
     * @param versionId exact version ID of the dependency; null means only the project was specified
     * @param type dependency type
     */
    class PlatformDependency(
        val platform: Platform,
        val projectId: String?,
        val versionId: String? = null,
        val type: PlatformDependencyType
    )
}

/**
 * Key used when caching and deduping dependencies
 */
fun PlatformVersion.PlatformDependency.cacheKey(): String {
    return "${platform.name}/${projectId.orEmpty()}/${versionId.orEmpty()}"
}