package com.movtery.zalithlauncher.renderer.renderers

import com.movtery.zalithlauncher.renderer.RendererInterface

/**
 * VirGL, via Mesa's OSMesa software rasterizer build. Library names and the unique
 * identifier are taken directly from FCL-Team/FoldCraftLauncher's own RendererManager.kt
 * (ID_VIRGL), not guessed.
 *
 * NOT bundled by this project yet: libOSMesa_81.so specifically (this project ships
 * libOSMesa_8.so, _2121, and _2300d - none of which are that exact file). Selecting this
 * renderer will fail to load until that exact build is sourced and added to jniLibs.
 */
class VirGLRenderer : RendererInterface {
    companion object {
        const val ID = "VIRGL"
    }

    override fun getRendererId(): String = ID

    override fun getUniqueIdentifier(): String = "417a7a93-d9b4-98b9-ec6e-1ea400259c1f"

    override fun getRendererName(): String = "VirGLRenderer"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy { emptyMap() }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libOSMesa_81.so"

    override fun getRendererEGL(): String = "libEGL.so"
}
