package com.endiq.turtlelauncher.renderer.renderers

import com.endiq.turtlelauncher.renderer.RendererInterface

/** Experimental Mesa Freedreno renderer for supported Android/Adreno builds. */
class FreedrenoRenderer : RendererInterface {
    companion object {
        const val ID = "gallium_freedreno"
    }

    override fun getRendererId(): String = ID
    override fun getUniqueIdentifier(): String = "1ad7249f-5784-4f00-bc72-174b3578ee46"
    override fun getRendererName(): String = "Freedreno (Adreno, Experimental)"
    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy { emptyMap() }
    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }
    override fun getRendererLibrary(): String = "libOSMesa_8.so"
}
