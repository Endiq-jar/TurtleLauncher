package com.movtery.zalithlauncher.renderer.renderers

import com.movtery.zalithlauncher.renderer.RendererInterface

/**
 * Ported from Copper Launcher's "opengles3_KW" renderer option.
 * "KW" = Krypton Wrapper, an alternate "next-gen" GL4ES build, GLES3 codepath.
 * Real upstream: github.com/BZLZHH/NGG-FCLRendererPlugin (NG-GL4ES).
 */
class NgGL4ESRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles3_KW"

    override fun getUniqueIdentifier(): String = "f4c9e3a1-7b2d-4e5f-9a8c-1d6b3e0f2c4a"

    override fun getRendererName(): String = "GL4ES (Krypton Wrapper)"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "LIBGL_ES" to "3"
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libng_gl4es.so"
}
