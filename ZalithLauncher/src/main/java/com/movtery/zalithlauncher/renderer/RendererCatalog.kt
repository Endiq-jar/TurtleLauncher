package com.movtery.zalithlauncher.renderer

import com.movtery.zalithlauncher.renderer.renderers.FreedrenoRenderer
import com.movtery.zalithlauncher.renderer.renderers.HolyGL4ESRenderer
import com.movtery.zalithlauncher.renderer.renderers.KryptonWrapperRenderer
import com.movtery.zalithlauncher.renderer.renderers.VGPURenderer
import com.movtery.zalithlauncher.renderer.renderers.VirGLRenderer
import com.movtery.zalithlauncher.renderer.renderers.ZinkRenderer

/**
 * Cross-cutting renderer metadata that doesn't belong on [RendererInterface] itself -
 * compatibility plugins don't have it, and it's about how renderers relate to each other
 * (fallback order, recommendation badge), not any one renderer's own behavior.
 *
 * Adding a new built-in renderer: one new RendererInterface class, one entry here (or
 * none, if it doesn't need a version range/badge/fallback slot), one line in
 * Renderers.addRenderers. Nothing else needs to change.
 */
object RendererCatalog {
    enum class Badge { RECOMMENDED, STABLE, EXPERIMENTAL }

    data class Entry(
        /** Inclusive lower bound, e.g. "1.16.5" for VGPU. Null = no lower bound. */
        val minMinecraftVersion: String? = null,
        /** Inclusive upper bound, e.g. "1.21.4" for Holy GL4ES. Null = no upper bound. */
        val maxMinecraftVersion: String? = null,
        val badge: Badge,
        /** Position in the automatic fallback chain - lower tries first. Null = never
         *  auto-substituted, only ever picked manually (that's VGPU here; the spec's
         *  fallback chain names five renderers and VGPU isn't one of them). */
        val fallbackPriority: Int? = null
    )

    private val entries: Map<String, Entry> = mapOf(
        // Fallback chain per spec: Holy GL4ES -> Krypton Wrapper -> VirGL -> Zink -> Freedreno.
        HolyGL4ESRenderer.ID to Entry(
            maxMinecraftVersion = "1.21.4",
            badge = Badge.RECOMMENDED,
            fallbackPriority = 0
        ),
        KryptonWrapperRenderer.ID to Entry(badge = Badge.STABLE, fallbackPriority = 1),
        VirGLRenderer.ID to Entry(badge = Badge.EXPERIMENTAL, fallbackPriority = 2),
        ZinkRenderer.ID to Entry(badge = Badge.STABLE, fallbackPriority = 3),
        FreedrenoRenderer.ID to Entry(badge = Badge.EXPERIMENTAL, fallbackPriority = 4),
        VGPURenderer.ID to Entry(
            minMinecraftVersion = "1.16.5",
            badge = Badge.EXPERIMENTAL,
            fallbackPriority = null
        )
    )

    fun get(rendererId: String): Entry? = entries[rendererId]

    /** The fallback chain in try-order, per the spec: GL4ES, NGGL4ES, VIRGL, ZINK, FREEDRENO. */
    fun fallbackChain(): List<String> = entries.entries
        .filter { it.value.fallbackPriority != null }
        .sortedBy { it.value.fallbackPriority }
        .map { it.key }
}
