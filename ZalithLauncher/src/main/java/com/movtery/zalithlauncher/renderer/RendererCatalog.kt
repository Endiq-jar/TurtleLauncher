package com.movtery.zalithlauncher.renderer

import com.movtery.zalithlauncher.renderer.renderers.AngleRenderer
import com.movtery.zalithlauncher.renderer.renderers.FreedrenoRenderer
import com.movtery.zalithlauncher.renderer.renderers.HolyGL4ESRenderer
import com.movtery.zalithlauncher.renderer.renderers.LTWRenderer
import com.movtery.zalithlauncher.renderer.renderers.MobileGluesRenderer
import com.movtery.zalithlauncher.renderer.renderers.NWRenderer
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
        val badge: Badge,
        /**
         * Whether shader packs (Iris/OptiFine-style) actually render under this renderer.
         * Not string-verifiable the way library exports are - this is an architectural claim
         * from FCL-Team/FoldCraftLauncher's own README ("光影支持(需VirGL/Zink/MG渲染器)" -
         * shader support requires the VirGL/Zink/MobileGlues renderer), which is a reasonable
         * source here since this launcher's VirGL/Zink renderer classes are themselves sourced
         * directly from FCL's RendererManager.kt (see their own doc comments). Plain GL4ES-family
         * translation (Holy GL4ES/LTW/NW/ANGLE) and the other Mesa gallium backends
         * (Freedreno/VGPU) are left false pending their own confirmation - false is the safe
         * default (an incorrectly-false renderer just gets an unnecessary warning; an
         * incorrectly-true one sends someone chasing a shader bug that isn't fixable).
         */
        val supportsShaderPacks: Boolean = false
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
        // Shader-pack support per FCL's README - see supportsShaderPacks doc comment above.
        MobileGluesRenderer.ID to Entry(badge = Badge.EXPERIMENTAL, supportsShaderPacks = true),
        // NW: GL4ES-family translator, no prior TurtleLauncher track record and no
        // upstream source/changelog to check maturity claims against - kept cautious
        // like MobileGlues/VirGL/Freedreno/VGPU until it's been run on real devices.
        NWRenderer.ID to Entry(badge = Badge.EXPERIMENTAL),
        VirGLRenderer.ID to Entry(badge = Badge.EXPERIMENTAL, supportsShaderPacks = true),
        ZinkRenderer.ID to Entry(badge = Badge.STABLE, supportsShaderPacks = true),
        FreedrenoRenderer.ID to Entry(badge = Badge.EXPERIMENTAL),
        VGPURenderer.ID to Entry(
            minMinecraftVersion = "1.16.5",
            badge = Badge.EXPERIMENTAL
        ),
        // ANGLE: .so pair was already bundled (jniLibs) but had no renderer class wiring it
        // up until now - no TurtleLauncher track record at all, so EXPERIMENTAL like the
        // other newly-registered/unproven renderers.
        AngleRenderer.ID to Entry(badge = Badge.EXPERIMENTAL)
    )

    fun get(rendererId: String): Entry? = entries[rendererId]

    /** False (including for unknown/unlisted renderer ids) is the safe default - see
     *  [Entry.supportsShaderPacks]'s doc comment for why. */
    fun supportsShaderPacks(rendererId: String): Boolean = entries[rendererId]?.supportsShaderPacks ?: false
}
