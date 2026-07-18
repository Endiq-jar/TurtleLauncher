package com.movtery.zalithlauncher.renderer.renderers

import com.movtery.zalithlauncher.renderer.RendererInterface

/**
 * Ported from Copper Launcher's "opengles2" renderer option.
 * Classic GL4ES translation layer, GLES2 codepath.
 */
class GL4ESRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles2"

    override fun getUniqueIdentifier(): String = "8b52d82d-8f6d-4d3a-a767-dc93f8b72fc7"

    override fun getRendererName(): String = "GL4ES"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            // Krypton Wrapper crashes with LIBGL_ES=1, force GLES2 explicitly
            "LIBGL_ES" to "2"
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libgl4es_115.so"
}
