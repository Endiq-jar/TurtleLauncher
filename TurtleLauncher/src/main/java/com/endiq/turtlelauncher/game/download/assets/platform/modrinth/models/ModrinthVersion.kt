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

import com.endiq.turtlelauncher.game.download.assets.platform.Platform
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformDependencyType
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformDisplayLabel
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformReleaseType
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformVersion
import com.endiq.turtlelauncher.utils.string.parseInstant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.time.Instant

@Serializable
class ModrinthVersion(
    /** Display name of the version */
    @SerialName("name")
    val name: String,

    /** Version number */
    @SerialName("version_number")
    val versionNumber: String,

    /**
     * Changelog of the version
     *
     * **Completely unused**, but some authors make this thing enormous,
     * which could throw the launcher into an OOM, e.g. the mod [Crash Assistant](https://modrinth.com/mod/crash-assistant),
     * so this field is neither deserialized nor ever to be used by code
     */
    @Transient
    @SerialName("changelog")
    @Deprecated("Must not be used by code!")
    val changelog: String? = null,

    /** List of the specific project versions this version depends on */
    @SerialName("dependencies")
    val dependencies: Array<Dependency>,

    /** Supported game versions */
    @SerialName("game_versions")
    val gameVersions: Array<String>,

    /** Release channel of this version */
    @SerialName("version_type")
    val versionType: PlatformReleaseType,

    /** Mod loaders supported by this version. For resource packs, "minecraft" is used */
    @SerialName("loaders")
    val loaders: Array<String>,

    /** Whether this version is the recommended one */
    @SerialName("featured")
    val featured: Boolean,

    @SerialName("status")
    val status: String,

    @SerialName("requested_status")
    val requestedStatus: String? = null,

    /** Version ID, encoded as a base62 string */
    @SerialName("id")
    val id: String,

    /** ID of the project this version belongs to */
    @SerialName("project_id")
    val projectId: String,

    /** ID of the author who published this version */
    @SerialName("author_id")
    val authorId: String,

    @SerialName("date_published")
    val datePublished: String,

    /** Download count of this version */
    @SerialName("downloads")
    val downloads: Long,

    /** Link to the changelog of this version. Always null; kept only for legacy compatibility */
    @SerialName("changelog_url")
    val changelogUrl: String? = null,

    /** List of downloadable files of this version */
    @SerialName("files")
    val files: Array<ModrinthFile>
) : PlatformVersion {
    @Serializable
    class Dependency(
        /** ID of the version this version depends on */
        @SerialName("version_id")
        val versionId: String? = null,

        /** ID of the project this version depends on */
        @SerialName("project_id")
        val projectId: String? = null,

        /** File name of the dependency, mainly used to show external dependencies in modpacks */
        @SerialName("file_name")
        val fileName: String? = null,

        /** Dependency type of this version */
        @SerialName("dependency_type")
        val dependencyType: PlatformDependencyType
    )

    /**
     * The primary file of this version
     */
    @Transient
    private lateinit var thisPrimaryFile: ModrinthFile

    override suspend fun initFile(currentProjectId: String): Boolean {
        val file = files.getPrimary() ?: return false
        thisPrimaryFile = file
        return true
    }

    override fun platform(): Platform = Platform.MODRINTH

    override fun platformId(): String = id

    override fun platformProjectId(): String = projectId

    override fun platformDisplayName(): String = name

    override fun platformFileName(): String = thisPrimaryFile.fileName

    override fun platformGameVersion(): Array<String> = gameVersions

    override fun platformLoaders(): List<PlatformDisplayLabel> = loaders.mapNotNull { loaderName ->
        ModrinthModLoaderCategory.entries.find { category ->
            category.facetValue() == loaderName
        }
    }

    override fun platformReleaseType(): PlatformReleaseType = versionType

    override fun platformDependencies(): List<PlatformVersion.PlatformDependency> = dependencies.mapNotNull { dependency ->
        if (dependency.projectId == null && dependency.versionId == null) return@mapNotNull null
        PlatformVersion.PlatformDependency(
            platform = platform(),
            projectId = dependency.projectId,
            versionId = dependency.versionId,
            type = dependency.dependencyType
        )
    }

    override fun platformDownloadCount(): Long = downloads

    override fun platformDownloadUrl(): String = thisPrimaryFile.url

    override fun platformDatePublished(): Instant = parseInstant(datePublished)

    override fun platformSha1(): String? = thisPrimaryFile.hashes.sha1

    override fun platformFileSize(): Long = thisPrimaryFile.size

    override fun platformVersion(): String = versionNumber
}