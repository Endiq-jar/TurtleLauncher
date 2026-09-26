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

package com.endiq.turtlelauncher.game.path

import java.io.File

private fun String.replaceSeparator(): String = this.replace("/", File.separator)

/** Currently selected game directory */
fun getGameHome(): String = GamePathManager.currentPath.value

/** versions folder under the given game directory */
fun getVersionsHome(gameHome: String): String = "${gameHome}/versions".replaceSeparator()

fun getVersionsHome(): String = getVersionsHome(getGameHome())

/** libraries folder under the given game directory */
fun getLibrariesHome(gameHome: String): String = "${gameHome}/libraries".replaceSeparator()

fun getLibrariesHome(): String = getLibrariesHome(getGameHome())

/** assets folder under the given game directory */
fun getAssetsHome(gameHome: String): String = "${gameHome}/assets".replaceSeparator()

fun getAssetsHome(): String = getAssetsHome(getGameHome())

/** resources folder under the given game directory */
fun getResourcesHome(gameHome: String): String = "${gameHome}/resources".replaceSeparator()

fun getResourcesHome(): String = getResourcesHome(getGameHome())