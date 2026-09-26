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

import com.endiq.turtlelauncher.game.download.assets.platform.Platform
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformClasses
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformDisplayLabel
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformFilterCode
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformSearchData
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformSearchResult
import com.endiq.turtlelauncher.game.download.assets.platform.modrinth.models.ModrinthModLoaderCategory
import com.endiq.turtlelauncher.game.download.assets.platform.modrinth.models.ModrinthSide
import com.endiq.turtlelauncher.game.download.assets.platform.modrinth.models.MonetizationStatus
import com.endiq.turtlelauncher.game.download.assets.platform.modrinth.models.mapModrinthCategory
import com.endiq.turtlelauncher.game.download.assets.platform.searchRankWithChineseBias
import com.endiq.turtlelauncher.game.download.assets.utils.getTranslations
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.AssetsPage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Project entries returned by a Modrinth search
 */
@Serializable
class ModrinthSearchResult(
    /**
     * Found projects **required**
     */
    @SerialName("hits")
    val hits: Array<ModrinthProject>,

    /**
     * Results skipped by the query **required**
     */
    @SerialName("offset")
    val offset: Int,

    /**
     * Number of results returned for the query **required**
     */
    @SerialName("limit")
    val limit: Int,

    /**
     * Total number of results matching the query **required**
     */
    @SerialName("total_hits")
    val totalHits: Int
): PlatformSearchResult {
    @Serializable
    class ModrinthProject(
        /**
         * Unique project identifier ID **required**
         */
        @SerialName("project_id")
        val projectId: String,

        /**
         * Project type **required**
         */
        @SerialName("project_type")
        val projectType: String,

        /**
         * Project slug **un-required**
         */
        @SerialName("slug")
        val slug: String? = null,

        /**
         * Username of the project's author **required**
         */
        @SerialName("author")
        val author: String,

        /**
         * The project's title **un-required**
         */
        @SerialName("title")
        val title: String? = null,

        /**
         * The project's summary **un-required**
         */
        @SerialName("description")
        val description: String? = null,

        /**
         * List of the project's categories **un-required**
         */
        @SerialName("categories")
        val categories: Array<String>? = null,

        /**
         * List of the project's non-secondary categories **un-required**
         */
        @SerialName("display_categories")
        val displayCategories: Array<String>? = null,

        /**
         * Minecraft versions supported by the project  **required**
         */
        @SerialName("versions")
        val versions: Array<String>,

        /**
         * The project's total downloads **required**
         */
        @SerialName("downloads")
        val downloads: Long,

        /**
         * Total users following the project **required**
         */
        @SerialName("follows")
        val follows: Long,

        /**
         * Project icon URL **un-required**
         */
        @SerialName("icon_url")
        val iconUrl: String? = null,

        /**
         * Date the project was added to search **required**
         */
        @SerialName("date_created")
        val dateCreated: String,

        /**
         * Date the project was last modified **required**
         */
        @SerialName("date_modified")
        val dateModified: String,

        /**
         * **un-required**
         */
        @SerialName("latest_version")
        val latestVersion: String? = null,

        /**
         * The project's SPDX license ID **required**
         */
        @SerialName("license")
        val license: String,

        /**
         * The project's client-side support **un-required**
         */
        @SerialName("client_side")
        val clientSide: ModrinthSide? = null,

        /**
         * The project's server-side support **un-required**
         */
        @SerialName("server_side")
        val serverSide: ModrinthSide? = null,

        /**
         * All gallery images attached to the project **un-required**
         */
        @SerialName("gallery")
        val gallery: Array<String>? = null,

        /**
         * The project's featured gallery image **un-required**
         */
        @SerialName("featured_gallery")
        val featuredGallery: String? = null,

        /**
         * The project's RGB color, extracted from its icon **un-required**
         */
        @SerialName("color")
        val color: Int? = null,

        /**
         * ID of the moderation thread associated with the project **un-required**
         */
        @SerialName("thread_id")
        val threadId: String? = null,

        /**
         * **un-required**
         */
        @SerialName("monetization_status")
        val monetizationStatus: MonetizationStatus? = null,
    ) : PlatformSearchData {
        override fun platform(): Platform = Platform.MODRINTH

        override fun platformId(): String = projectId

        override fun platformTitle(): String = title ?: ""

        override fun platformDescription(): String = description ?: ""

        override fun platformAuthor(): String = author

        override fun platformIconUrl(): String? = iconUrl

        override fun platformDownloadCount(): Long = downloads

        override fun platformFollows(): Long = follows

        override fun platformModLoaders(): List<PlatformDisplayLabel>? {
            val modloaders = displayCategories
                ?.mapNotNull { string ->
                    ModrinthModLoaderCategory.entries.find { it.facetValue() == string }
                }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }

            return modloaders?.sortedWith { o1, o2 -> o1.index() - o2.index() }
        }

        override fun platformCategories(classes: PlatformClasses): List<PlatformFilterCode>? {
            val categories = displayCategories
                ?.mapNotNull { string ->
                    string.mapModrinthCategory(classes)
                }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
                ?: categories
                    ?.take(4) //without main categories, show the first 4
                    ?.mapNotNull { string ->
                        string.mapModrinthCategory(classes)
                    }
                    ?.toSet()
                    ?.takeIf { it.isNotEmpty() }

            return categories?.sortedWith { o1, o2 -> o1.index() - o2.index() }
        }
    }

    override fun getAssetsPage(classes: PlatformClasses): AssetsPage {
        val mcmodData = hits.map {
            it to classes.getTranslations().getModBySlugId(it.slug)
        }

        return AssetsPage(
            pageNumber = this.offset / this.limit + 1,
            pageIndex = this.offset,
            totalPage = (this.totalHits + this.limit - 1) / this.limit,
            isLastPage = (this.offset + this.limit) >= this.totalHits,
            data = mcmodData
        )
    }

    override fun processChineseSearchResults(
        searchFilter: String,
        classes: PlatformClasses
    ): PlatformSearchResult {
        val newHits = hits.toList()
            .searchRankWithChineseBias(searchFilter, classes) { it.slug }
            .toTypedArray()
        return ModrinthSearchResult(newHits, offset, limit, totalHits)
    }
}
