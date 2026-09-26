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

/**
 * LTW (Large Thin Wrapper) renderer and natives, imported from
 * [Turtle-Launcher](https://github.com/Endiq-jar/Turtle-Launcher)
 * which in turn integrates [MojoLauncher/LTW](https://github.com/MojoLauncher/LTW).
 *
 * A very thin OpenGL ES wrapper: it implements just the LWJGL-relevant GL
 * functions on top of the Android system GLES/EGL drivers, translating the
 * few bits Minecraft actually needs.
 */
object LTWRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles"

    override fun getUniqueIdentifier(): String = "e7dcb6d0-bf40-44f0-9703-791c7b24c69e"

    override fun getRendererName(): String = "LTW (Large Thin Wrapper)"

    override fun getRendererSummary(): String = "Stable, lightweight OpenGL ES wrapper from the MojoLauncher project"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy { emptyMap() }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libltw.so"
}
