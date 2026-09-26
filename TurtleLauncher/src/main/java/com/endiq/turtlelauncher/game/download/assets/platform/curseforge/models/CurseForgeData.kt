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

package com.endiq.turtlelauncher.game.download.assets.platform.curseforge.models

import com.endiq.turtlelauncher.game.download.assets.platform.Platform
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformClasses
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformDisplayLabel
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformFilterCode
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformSearchData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CurseForgeData(
    /**
     * Project ID
     */
    @SerialName("id")
    val id: Int,

    /**
     * ID of the game the project belongs to
     */
    @SerialName("gameId")
    val gameId: Int,

    /**
     * The project's name
     */
    @SerialName("name")
    val name: String,

    /**
     * The project slug appearing in URLs
     */
    @SerialName("slug")
    val slug: String,

    /**
     * The project's related links
     */
    @SerialName("links")
    val links: Links,

    /**
     * The project's description
     */
    @SerialName("summary")
    val summary: String,

    /**
     * The project's status
     */
    @SerialName("status")
    val status: Int,

    /**
     * The project's total downloads
     */
    @SerialName("downloadCount")
    val downloadCount: Long,

    /**
     * Whether the project is in the featured list
     */
    @SerialName("isFeatured")
    val isFeatured: Boolean,

    /**
     * The main category designated by the project author
     */
    @SerialName("primaryCategoryId")
    val primaryCategoryId: Int,

    /**
     * Category list related to the project
     */
    @SerialName("categories")
    val categories: Array<Category>,

    /**
     * The class id this project belongs to
     */
    @SerialName("classId")
    val classId: Int? = null,

    /**
     * The project's authors
     */
    @SerialName("authors")
    val authors: Array<Author>,

    /**
     * The project's logo
     */
    @SerialName("logo")
    val logo: Asset? = null,

    /**
     * The project's screenshots
     */
    @SerialName("screenshots")
    val screenshots: Array<Asset> = emptyArray(),

    /**
     * The project's main file ID
     */
    @SerialName("mainFileId")
    val mainFileId: Int,

    /**
     * The project's newest file list
     */
    @SerialName("latestFiles")
    val latestFiles: Array<CurseForgeFile>,

    /**
     * Detailed file info list of the project's latest files
     */
    @SerialName("latestFilesIndexes")
    val latestFilesIndexes: Array<CurseForgeFileIndex>,

    /**
     * Detailed file info list of the project's latest early-access files
     */
    @SerialName("latestEarlyAccessFilesIndexes")
    val latestEarlyAccessFilesIndexes: Array<CurseForgeFileIndex>? = null,

    /**
     * The project's creation date
     */
    @SerialName("dateCreated")
    val dateCreated: String,

    /**
     * When the project was last modified
     */
    @SerialName("dateModified")
    val dateModified: String,

    /**
     * The project's release date
     */
    @SerialName("dateReleased")
    val dateReleased: String,

    /**
     * Whether the project may be distributed
     */
    @SerialName("allowModDistribution")
    val allowModDistribution: Boolean? = null,

    /**
     * Popularity ranking of the game's mods
     */
    @SerialName("gamePopularityRank")
    val gamePopularityRank: Int,

    /**
     * Whether the project is searchable
     * False when the project is experimental, deleted, or only has Alpha files
     */
    @SerialName("isAvailable")
    val isAvailable: Boolean,

    /**
     * The project's thumbs-up count
     */
    @SerialName("thumbsUpCount")
    val thumbsUpCount: Int,

    /**
     * The project's rating
     */
    @SerialName("rating")
    val rating: Double? = null
) : PlatformSearchData {
    @Serializable
    class Links(
        /**
         * Official website URL
         */
        @SerialName("websiteUrl")
        val websiteUrl: String? = null,

        /**
         * WIKI URL
         */
        @SerialName("wikiUrl")
        val wikiUrl: String? = null,

        /**
         * Issue tracker URL
         */
        @SerialName("issuesUrl")
        val issuesUrl: String? = null,

        /**
         * Source code URL
         */
        @SerialName("sourceUrl")
        val sourceUrl: String? = null
    )

    @Serializable
    class Category(
        /**
         * Category ID
         */
        @SerialName("id")
        val id: Int,

        /**
         * ID of the game the category belongs to
         */
        @SerialName("gameId")
        val gameId: Int,

        /**
         * The category's name
         */
        @SerialName("name")
        val name: String,

        /**
         * The category slug shown in URLs
         */
        @SerialName("slug")
        val slug: String,

        /**
         * The category's URL
         */
        @SerialName("url")
        val url: String,

        /**
         * The category's icon URL
         */
        @SerialName("iconUrl")
        val iconUrl: String,

        /**
         * When the category was last modified
         */
        @SerialName("dateModified")
        val dateModified: String,

        /**
         * Parent categories of other categories
         */
        @SerialName("isClass")
        val isClass: Boolean,

        /**
         * The category's class ID, i.e. the class it belongs to
         */
        @SerialName("classId")
        val classId: Int? = null,

        /**
         * This category's parent category
         */
        @SerialName("parentCategoryId")
        val parentCategoryId: Int? = null,

        /**
         * This category's display index
         */
        @SerialName("displayIndex")
        val displayIndex: Int? = null
    )

    @Serializable
    class Author(
        @SerialName("id")
        val id: Int,

        @SerialName("name")
        val name: String,

        @SerialName("url")
        val url: String,

        @SerialName("avatarUrl")
        val avatarUrl: String? = null
    )

    @Serializable
    class Asset(
        @SerialName("id")
        val id: Int,

        @SerialName("modId")
        val modId: Int,

        @SerialName("title")
        val title: String,

        @SerialName("description")
        val description: String,

        @SerialName("thumbnailUrl")
        val thumbnailUrl: String,

        @SerialName("url")
        val url: String
    )

    override fun platform(): Platform = Platform.CURSEFORGE

    override fun platformId(): String = id.toString()

    override fun platformTitle(): String = name

    override fun platformDescription(): String = summary

    override fun platformAuthor(): String = authors[0].name

    override fun platformAuthors(): List<String> = authors.map { it.name }

    override fun platformIconUrl(): String? = logo?.url

    override fun platformDownloadCount(): Long = downloadCount

    override fun platformFollows(): Long? = null

    override fun platformModLoaders(): List<PlatformDisplayLabel>? {
        return latestFilesIndexes.mapNotNull {
            it.modLoader //get loader info from the latest file
        }.toSet()
            .takeIf { it.isNotEmpty() }
            ?.sortedWith { o1, o2 -> o1.index() - o2.index() }
    }

    override fun platformCategories(classes: PlatformClasses): List<PlatformFilterCode>? {
        return categories.mapNotNull {
            it.id.toString().mapCurseForgeCategory(classes)
        }.toSet().takeIf { it.isNotEmpty() }
            ?.sortedWith { o1, o2 -> o1.index() - o2.index() }
    }
}

/**
 * @return whether the mod is visible
 */
fun CurseForgeData.isApproved(): Boolean = this.status == 4