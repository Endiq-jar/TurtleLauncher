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
 * Platform project implementation
 */
interface PlatformProject {
    /**
     * Owning platform
     */
    fun platform(): Platform

    /**
     * The project's platform ID
     */
    fun platformId(): String

    /**
     * The project's type
     */
    fun platformClasses(defaultClasses: PlatformClasses): PlatformClasses

    /**
     * The project's alias (slug)
     */
    fun platformSlug(): String

    /**
     * The project's icon URL
     */
    fun platformIconUrl(): String?

    /**
     * The project's title on the platform
     */
    fun platformTitle(): String

    /**
     * The project's description on the platform
     */
    fun platformSummary(): String?

    /**
     * The project's main author on the platform
     */
    fun platformAuthor(): String?

    /**
     * The project's author list on the platform
     */
    fun platformAuthors(): List<String> = listOfNotNull(platformAuthor()?.takeIf { it.isNotBlank() })

    /**
     * The project's total downloads on the platform
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
     * Whether the project is still available on the platform
     */
    fun platformAvailable(): Boolean = true

    /**
     * Checks whether the project category is supported
     */
    fun checkClasses()

    /**
     * Category info marked on the platform
     */
    fun platformCategories(classes: PlatformClasses): List<PlatformFilterCode>?

    /**
     * All related links of the project
     */
    fun platformUrls(defaultClasses: PlatformClasses): Urls

    /**
     * All screenshots uploaded for the project on the platform
     */
    fun platformScreenshots(): List<Screenshot>

    /**
     * Various external links of the resource project
     * @param projectUrl platform project URL
     * @param sourceUrl source repository URL
     * @param issuesUrl issue tracker URL
     * @param wikiUrl wiki URL
     */
    class Urls(
        val projectUrl: String? = null,
        val sourceUrl: String? = null,
        val issuesUrl: String? = null,
        val wikiUrl: String? = null
    )

    /**
     * Screenshot
     * @param imageUrl image URL
     * @param title screenshot title
     * @param description screenshot description
     */
    class Screenshot(
        val imageUrl: String,
        val title: String? = null,
        val description: String? = null
    )
}

/**
 * Whether all links are null
 */
fun PlatformProject.Urls.isAllNull(): Boolean {
    return projectUrl == null &&
            sourceUrl == null &&
            issuesUrl == null &&
            wikiUrl   == null
}