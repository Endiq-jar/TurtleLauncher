package com.endiq.turtlelauncher.renderer

import com.endiq.turtlelauncher.renderer.renderers.AngleRenderer
import com.endiq.turtlelauncher.renderer.renderers.FreedrenoRenderer
import com.endiq.turtlelauncher.renderer.renderers.HolyGL4ESRenderer
import com.endiq.turtlelauncher.renderer.renderers.NWRenderer
import com.endiq.turtlelauncher.renderer.renderers.VGPURenderer
import com.endiq.turtlelauncher.renderer.renderers.VirGLRenderer
import com.endiq.turtlelauncher.renderer.renderers.ZinkRenderer

object RendererCatalog {
    enum class Badge { RECOMMENDED, STABLE, EXPERIMENTAL }

    data class Entry(
        val minMinecraftVersion: String? = null,
        val maxMinecraftVersion: String? = null,
        val badge: Badge,
        val supportsShaderPacks: Boolean = false
    )

    private val entries: Map<String, Entry> = mapOf(
        HolyGL4ESRenderer.ID to Entry(maxMinecraftVersion = "1.21.4", badge = Badge.STABLE),
        NWRenderer.ID to Entry(badge = Badge.EXPERIMENTAL),
        VirGLRenderer.ID to Entry(badge = Badge.EXPERIMENTAL, supportsShaderPacks = true),
        ZinkRenderer.ID to Entry(badge = Badge.STABLE, supportsShaderPacks = true),
        FreedrenoRenderer.ID to Entry(badge = Badge.EXPERIMENTAL),
        VGPURenderer.ID to Entry(minMinecraftVersion = "1.16.5", badge = Badge.EXPERIMENTAL),
        AngleRenderer.ID to Entry(badge = Badge.EXPERIMENTAL)
    )

    fun get(rendererId: String): Entry? = entries[rendererId]
    fun supportsShaderPacks(rendererId: String): Boolean = entries[rendererId]?.supportsShaderPacks ?: false
}
