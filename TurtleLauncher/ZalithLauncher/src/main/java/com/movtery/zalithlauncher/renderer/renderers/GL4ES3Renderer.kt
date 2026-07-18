package com.movtery.zalithlauncher.renderer.renderers

import com.movtery.zalithlauncher.renderer.RendererInterface

/**
 * Ported from Copper Launcher's "opengles3" renderer option.
 * Same GL4ES build, GLES3 codepath (more extensions, may be less stable on old drivers).
 */
class GL4ES3Renderer : RendererInterface {
    override fun getRendererId(): String = "opengles3"

    override fun getUniqueIdentifier(): String = "5a7e9c31-4f2b-4d6e-8a1c-3b9f7e2d5c60"

    override fun getRendererName(): String = "GL4ES 3"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "LIBGL_ES" to "3"
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libgl4es_115.so"
}
