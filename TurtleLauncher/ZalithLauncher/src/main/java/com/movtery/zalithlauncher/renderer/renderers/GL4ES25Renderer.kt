package com.movtery.zalithlauncher.renderer.renderers

import com.movtery.zalithlauncher.renderer.RendererInterface

/**
 * Ported from Copper Launcher's "opengles2_5" renderer option.
 * Same GL4ES build as [GL4ESRenderer], with the extended 2.x extension set enabled.
 */
class GL4ES25Renderer : RendererInterface {
    override fun getRendererId(): String = "opengles2_5"

    override fun getUniqueIdentifier(): String = "c1d4a8b0-2e5f-4b8a-9c3d-7f1e6a2b9d40"

    override fun getRendererName(): String = "GL4ES 2.5"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "LIBGL_ES" to "2"
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libgl4es_115.so"
}
