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

import com.endiq.turtlelauncher.game.renderer.renderers.FreedrenoRenderer
import com.endiq.turtlelauncher.game.renderer.renderers.GL4ESRenderer
import com.endiq.turtlelauncher.game.renderer.renderers.KopperZinkRenderer
import com.endiq.turtlelauncher.game.renderer.renderers.NGGL4ESRenderer
import com.endiq.turtlelauncher.game.renderer.renderers.NWRenderer
import com.endiq.turtlelauncher.game.renderer.renderers.PanfrostRenderer
import com.endiq.turtlelauncher.game.renderer.renderers.VirGLRenderer
import com.endiq.turtlelauncher.utils.logging.Logger

private const val TAG = "Renderers"

/**
 * Central manager of all launchers renderers: both built-in renderers and those loaded via renderer plugins end up here
 */
object Renderers {
    private val renderers: MutableList<RendererInterface> = mutableListOf()
    private var currentRenderer: RendererInterface? = null
    private var isInitialized: Boolean = false

    fun init(
        reset: Boolean = false
    ) {
        if (isInitialized && !reset) return
        isInitialized = true

        if (reset) {
            renderers.clear()
            currentRenderer = null
        }

        addRenderers(
            NGGL4ESRenderer,
            GL4ESRenderer,
            NWRenderer,
            KopperZinkRenderer,
            VirGLRenderer,
            FreedrenoRenderer,
            PanfrostRenderer
        )
    }

    /**
     * Returns the current renderer list
     */
    fun getRenderers(): List<RendererInterface> = renderers

    /**
     * Registers several renderers
     */
    fun addRenderers(vararg renderers: RendererInterface) {
        renderers.forEach { renderer ->
            addRenderer(renderer)
        }
    }

    /**
     * Registers a single renderer
     */
    fun addRenderer(renderer: RendererInterface): Boolean {
        return if (renderers.any { it.getUniqueIdentifier() == renderer.getUniqueIdentifier() }) {
            Logger.warning(TAG, "The unique identifier of this renderer (${renderer.getRendererName()} - ${renderer.getUniqueIdentifier()}) conflicts with an already loaded renderer. " +
                    "Normally, this shouldn't happen. You deliberately caused this conflict, didn't you, user?")
            false
        } else {
            renderers.add(renderer)
            Logger.info(TAG, "Renderer loaded: ${renderer.getRendererName()} (${renderer.getRendererId()} - ${renderer.getUniqueIdentifier()})")
            true
        }
    }

    /**
     * Sets the current renderer
     * @param uniqueIdentifier the unique identifier of the renderer, used to find the one to set
     * @param retryToFirstOnFailure whether to fall back to the first renderer in the list when no match is found
     */
    fun setCurrentRenderer(uniqueIdentifier: String, retryToFirstOnFailure: Boolean = true) {
        if (!isInitialized) throw IllegalStateException("Uninitialized renderer!")
        currentRenderer = renderers.find { it.getUniqueIdentifier() == uniqueIdentifier } ?: run {
            if (retryToFirstOnFailure) {
                val renderer = renderers[0]
                Logger.warning(TAG, "Incompatible renderer $uniqueIdentifier will be replaced with ${renderer.getUniqueIdentifier()} (${renderer.getRendererName()})")
                renderer
            } else null
        }
    }

    /**
     * Returns the current renderer
     */
    fun getCurrentRenderer(): RendererInterface {
        if (!isInitialized) throw IllegalStateException("Uninitialized renderer!")
        return currentRenderer ?: throw IllegalStateException("Current renderer not set")
    }

    /**
     * Whether a renderer is currently set
     */
    fun isCurrentRendererValid(): Boolean = isInitialized && currentRenderer != null
}