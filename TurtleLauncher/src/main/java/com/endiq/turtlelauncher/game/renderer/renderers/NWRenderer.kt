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
 * NW (Native Wrapper) renderer, ported from Turtle-Launcher.
 * A GLESv2/EGL wrapper; experimental.
 */
object NWRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles2"

    override fun getUniqueIdentifier(): String = "03543a99-7f9b-47ba-8ae3-1e5162db9fa6"

    override fun getRendererName(): String = "NW (Wrapper, Experimental)"

    override fun getRendererSummary(): String = "Experimental GLES2 native wrapper renderer"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "NGG_DIR_PATH" to File(PathManager.DIR_FILES_PRIVATE, "nw")
                .apply { mkdirs() }
                .absolutePath
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libnw.so"
}
