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

package com.endiq.turtlelauncher.game.renderer

/**
 * Launcher renderer implementation
 */
interface RendererInterface {
    /**
     * Returns the renderer's ID
     */
    fun getRendererId(): String

    /**
     * Returns the renderer's unique identifier ID
     */
    fun getUniqueIdentifier(): String

    /**
     * Returns the renderer's name
     */
    fun getRendererName(): String

    /**
     * Returns the renderer's description
     */
    fun getRendererSummary(): String? = null

    /**
     * Returns the renderer's minimum compatible version
     */
    fun getMinMCVersion(): String? = null

    /**
     * Returns the renderer's maximum compatible version
     */
    fun getMaxMCVersion(): String? = null

    /**
     * Returns the display version number of the minimum compatible version
     */
    fun getDisplayMinMCVersion(): String? = getMinMCVersion()

    /**
     * Display version number of the renderer's maximum compatible version
     */
    fun getDisplayMaxMCVersion(): String? = getMaxMCVersion()

    /**
     * Returns the renderer's environment variables
     */
    fun getRendererEnv(): Lazy<Map<String, String>>

    /**
     * Returns the libraries needing dlopen
     */
    fun getDlopenLibrary(): Lazy<List<String>>

    /**
     * Returns the renderer's library
     */
    fun getRendererLibrary(): String

    /**
     * Returns the EGL name
     */
    fun getRendererEGL(): String? = null
}