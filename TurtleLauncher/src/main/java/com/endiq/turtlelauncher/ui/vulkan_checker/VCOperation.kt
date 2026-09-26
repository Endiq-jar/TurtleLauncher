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

package com.endiq.turtlelauncher.ui.vulkan_checker

import com.endiq.turtlelauncher.game.version.installed.Version
import com.endiq.turtlelauncher.utils.device.VulkanCapabilities

/**
 * Vulkan checker UI operation state
 */
sealed interface VCOperation {
    data object None: VCOperation

    /**
     * Vulkan check hint dialog
     */
    data class Tip(val version: Version): VCOperation

    /**
     * @param data the check result
     * @param useTurnip whether Turnip is used
     */
    data class Result(
        val data: VulkanCapabilities?,
        val useTurnip: Boolean = false
    ): VCOperation
}