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

import com.endiq.turtlelauncher.game.download.assets.utils.getTranslations
import com.endiq.turtlelauncher.ui.screens.content.download.assets.elements.AssetsPage
import com.endiq.turtlelauncher.utils.string.LevCalculator
import com.endiq.turtlelauncher.utils.string.containsChinese
import kotlin.math.max

private const val CONTAIN_CHINESE_WEIGHT = 10

/**
 * Platform search result implementation
 */
interface PlatformSearchResult {
    /**
     * Packages platform project search results together with mcmod info as one page
     */
    fun getAssetsPage(classes: PlatformClasses): AssetsPage

    /**
     * Referenced source: [HMCL Github](https://github.com/HMCL-dev/HMCL/blob/57018be/HMCL/src/main/java/org/jackhuang/hmcl/game/LocalizedRemoteModRepository.java#L65-L103)
     * Copyright of the original project belongs to its authors; licensed under GPL v3
     * @return the priority ordering for Chinese search results
     */
    fun processChineseSearchResults(searchFilter: String, classes: PlatformClasses): PlatformSearchResult
}

/**
 * Priority ordering for Chinese search results
 * Referenced source: [HMCL Github](https://github.com/HMCL-dev/HMCL/blob/57018be/HMCL/src/main/java/org/jackhuang/hmcl/game/LocalizedRemoteModRepository.java#L65-L103)
 * Copyright of the original project belongs to its authors; licensed under GPL v3
 */
fun <T> List<T>.searchRankWithChineseBias(
    searchFilter: String,
    classes: PlatformClasses,
    getSlug: (T) -> String?
): List<T> {
    val (chineseResults, englishResults) = partition { mod ->
        classes.getTranslations()
            .getModBySlugId(getSlug(mod))
            ?.name
            ?.takeIf { it.isNotBlank() && it.containsChinese() } != null
    }

    val levCalculator = LevCalculator()
    val sortedChineseResults = chineseResults.map { mod ->
        val translation = classes.getTranslations()
            .getModBySlugId(getSlug(mod))!!
        val modName = translation.name

        val relevanceScore = when {
            searchFilter.isEmpty() || modName.isEmpty() ->
                max(searchFilter.length, modName.length)
            else -> {
                var levDistance = levCalculator.calc(searchFilter, modName)
                searchFilter.forEach { char ->
                    if (modName.contains(char)) levDistance -= CONTAIN_CHINESE_WEIGHT
                }
                levDistance
            }
        }
        mod to relevanceScore
    }.sortedBy { it.second }
        .map { it.first }

    return sortedChineseResults + englishResults
}