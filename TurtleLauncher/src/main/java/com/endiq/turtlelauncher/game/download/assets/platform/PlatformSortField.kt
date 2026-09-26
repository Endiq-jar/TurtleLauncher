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

import com.endiq.turtlelauncher.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PlatformSortField(
    val curseforge: String,
    val modrinth: String
): PlatformFilterCode {
    /** Relevance */
    @SerialName("RELEVANCE")
    RELEVANCE("1", "relevance") {
        override fun getDisplayName(): Int = R.string.download_assets_filter_sort_by_relevant
    },

    /** Downloads */
    @SerialName("DOWNLOADS")
    DOWNLOADS("6", "downloads") {
        override fun getDisplayName(): Int = R.string.download_assets_filter_sort_by_total_downloads
    },

    /** Popularity */
    @SerialName("POPULARITY")
    POPULARITY("2", "follows") {
        override fun getDisplayName(): Int = R.string.download_assets_filter_sort_by_popularity
    },

    /** Newest */
    @SerialName("NEWEST")
    NEWEST("11", "newest") {
        override fun getDisplayName(): Int = R.string.download_assets_filter_sort_by_recently_created
    },

    /** Recently updated */
    @SerialName("UPDATED")
    UPDATED("3", "updated") {
        override fun getDisplayName(): Int = R.string.download_assets_filter_sort_by_recently_updated
    };

    override fun index(): Int = this.ordinal
}