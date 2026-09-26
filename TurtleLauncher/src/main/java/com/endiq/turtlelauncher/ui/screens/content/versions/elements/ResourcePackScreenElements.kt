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

package com.endiq.turtlelauncher.ui.screens.content.versions.elements

import com.endiq.turtlelauncher.game.version.resource_pack.ResourcePackInfo

/** Resource pack operation states */
sealed interface ResourcePackOperation {
    data object None : ResourcePackOperation
    /** Running a task */
    data object Progress : ResourcePackOperation
    /** Rename resource pack input dialog */
    data class RenamePack(val packInfo: ResourcePackInfo) : ResourcePackOperation
    /** Delete resource pack dialog */
    data class DeletePack(val packInfo: ResourcePackInfo) : ResourcePackOperation
}

/**
 * A simple resource pack filter
 */
data class ResourcePackFilter(
    val onlyShowValid: Boolean,
    val filterName: String
)

/**
 * A simple filter matching resource packs by name
 */
fun List<ResourcePackInfo>.filterPacks(filter: ResourcePackFilter) = this.filter {
    val valid = !filter.onlyShowValid || it.isValid
    val nameMatched = filter.filterName.isEmpty() ||
            //Judge by the format-code-stripped name
            it.rawName.contains(filter.filterName, true)
    valid && nameMatched
}
