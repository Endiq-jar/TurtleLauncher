package com.movtery.zalithlauncher.renderer.renderers

import com.movtery.zalithlauncher.renderer.RendererInterface

/**
 * Krypton Wrapper (NG-GL4ES) - an alternate "next-gen" GL4ES build. Library names and the
 * unique identifier are taken directly from FCL-Team/FoldCraftLauncher's own
 * RendererManager.kt (ID_NGGL4ES), not guessed. Real upstream:
 * github.com/BZLZHH/NGG-FCLRendererPlugin.
 */
class KryptonWrapperRenderer : RendererInterface {
    companion object {
        const val ID = "NGGL4ES"
    }

    override fun getRendererId(): String = ID

    override fun getUniqueIdentifier(): String = "e7b90ed6-e518-4d4e-93dc-5c7133cd5b31"

    override fun getRendererName(): String = "Krypton Wrapper"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy { emptyMap() }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libng_gl4es.so"

    override fun getRendererEGL(): String = "libEGL.so"
}
