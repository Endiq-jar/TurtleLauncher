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

/**
 * Platform search result item data implementation
 */
interface PlatformSearchData {
    /**
     * Owning platform
     */
    fun platform(): Platform

    /**
     * Project ID
     */
    fun platformId(): String

    /**
     * Title on the platform
     */
    fun platformTitle(): String

    /**
     * Description on the platform
     */
    fun platformDescription(): String

    /**
     * Main author on the platform
     */
    fun platformAuthor(): String

    /**
     * Author list on the platform
     */
    fun platformAuthors(): List<String> = listOfNotNull(platformAuthor().takeIf { it.isNotBlank() })

    /**
     * Icon URL
     */
    fun platformIconUrl(): String?

    /**
     * Download count on the platform
     */
    fun platformDownloadCount(): Long

    /**
     * Favorite count on the platform (Modrinth)
     */
    fun platformFollows(): Long?

    /**
     * Mod loader info marked on the platform
     */
    fun platformModLoaders(): List<PlatformDisplayLabel>?

    /**
     * Category info marked on the platform
     */
    fun platformCategories(classes: PlatformClasses): List<PlatformFilterCode>?
}