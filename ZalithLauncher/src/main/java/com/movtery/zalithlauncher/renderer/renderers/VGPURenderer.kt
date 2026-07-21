package com.movtery.zalithlauncher.renderer.renderers

import com.movtery.zalithlauncher.renderer.RendererInterface

/**
 * VGPU. Library names and the unique identifier are taken directly from
 * FCL-Team/FoldCraftLauncher's own RendererManager.kt (ID_VGPU), not guessed.
 * Recommended for Minecraft 1.16.5 and up (see [com.movtery.zalithlauncher.renderer.RendererCatalog]).
 *
 * NOT bundled by this project yet: libvgpu.so isn't present anywhere in jniLibs. Selecting
 * this renderer will fail to load until that build is sourced and added.
 */
class VGPURenderer : RendererInterface {
    companion object {
        const val ID = "VGPU"
    }

    override fun getRendererId(): String = ID

    override fun getUniqueIdentifier(): String = "0fb718e4-64e3-83d4-a974-8204ea1d9f9f"

    override fun getRendererName(): String = "VGPU"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy { emptyMap() }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libvgpu.so"

    override fun getRendererEGL(): String = "libEGL.so"
}
