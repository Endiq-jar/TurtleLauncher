package com.movtery.zalithlauncher.renderer

import com.movtery.zalithlauncher.renderer.renderers.FreedrenoRenderer
import com.movtery.zalithlauncher.renderer.renderers.HolyGL4ESRenderer
import com.movtery.zalithlauncher.renderer.renderers.LTWRenderer
import com.movtery.zalithlauncher.renderer.renderers.MobileGluesRenderer
import com.movtery.zalithlauncher.renderer.renderers.VGPURenderer
import com.movtery.zalithlauncher.renderer.renderers.VirGLRenderer
import com.movtery.zalithlauncher.renderer.renderers.ZinkRenderer

/**
 * Cross-cutting renderer metadata that doesn't belong on [RendererInterface] itself -
 * compatibility plugins don't have it, and it's about how renderers relate to each other
 * (recommendation badge), not any one renderer's own behavior.
 *
 * Adding a new built-in renderer: one new RendererInterface class, one entry here (or
 * none, if it doesn't need a version range/badge), one line in Renderers.addRenderers.
 * Nothing else needs to change.
 */
object RendererCatalog {
    enum class Badge { RECOMMENDED, STABLE, EXPERIMENTAL }

    data class Entry(
        /** Inclusive lower bound, e.g. "1.16.5" for VGPU. Null = no lower bound. */
        val minMinecraftVersion: String? = null,
        /** Inclusive upper bound, e.g. "1.21.4" for Holy GL4ES. Null = no upper bound. */
        val maxMinecraftVersion: String? = null,
        val badge: Badge
    )

    private val entries: Map<String, Entry> = mapOf(
        HolyGL4ESRenderer.ID to Entry(
            maxMinecraftVersion = "1.21.4",
            badge = Badge.RECOMMENDED
        ),
        // LTW: actively maintained upstream (MojoLauncher's own featured renderer,
        // ongoing changelog entries), badged the same as Krypton Wrapper's old slot.
        LTWRenderer.ID to Entry(badge = Badge.STABLE),
        // MobileGlues: real, actively developed (1.3.4 -> 2.0.0 in this round alone), but
        // its own changelog still describes features graduating out of "experimental" as
        // of 2.0.0 - no TurtleLauncher-specific track record yet, so kept cautious.
        MobileGluesRenderer.ID to Entry(badge = Badge.EXPERIMENTAL),
        VirGLRenderer.ID to Entry(badge = Badge.EXPERIMENTAL),
        ZinkRenderer.ID to Entry(badge = Badge.STABLE),
        FreedrenoRenderer.ID to Entry(badge = Badge.EXPERIMENTAL),
        VGPURenderer.ID to Entry(
            minMinecraftVersion = "1.16.5",
            badge = Badge.EXPERIMENTAL
        )
    )

    fun get(rendererId: String): Entry? = entries[rendererId]
}
