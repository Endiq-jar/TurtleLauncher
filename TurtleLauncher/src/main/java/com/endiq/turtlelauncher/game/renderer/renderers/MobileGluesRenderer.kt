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

package com.endiq.turtlelauncher.game.renderer.renderers

import com.endiq.turtlelauncher.game.renderer.RendererInterface
import com.endiq.turtlelauncher.path.PathManager
import java.io.File

/**
 * MobileGlues renderer and natives, imported from
 * [Turtle-Launcher](https://github.com/Endiq-jar/Turtle-Launcher)
 * which bundles the official [MobileGL-Dev/MobileGlues](https://github.com/MobileGL-Dev/MobileGlues-release)
 * build (V2.0.0).
 *
 * Implements desktop GL 4.x (with the CS line) on top of mobile Vulkan by
 * translating GL calls to its host driver at runtime; supports shader packs.
 */
object MobileGluesRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles"

    override fun getUniqueIdentifier(): String = "4b4b8e4b-083d-429c-97e1-5e8239b6dc17"

    override fun getRendererName(): String = "MobileGlues"

    override fun getRendererSummary(): String = "Modern GL 4.x on top of Vulkan; recommended when your device has Vulkan 1.2+"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "MG_DIR_PATH" to File(PathManager.DIR_FILES_PRIVATE, "mobileglues")
                .apply { mkdirs() }
                .absolutePath
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libmobileglues.so"

    override fun getRendererEGL(): String = "libmobileglues.so"
}
