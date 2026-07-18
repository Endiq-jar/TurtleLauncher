package com.movtery.zalithlauncher.renderer.renderers

import com.movtery.zalithlauncher.renderer.RendererInterface

/**
 * Ported from Copper Launcher's "opengles3_ltw" renderer option.
 * Google ANGLE, loaded through the LTW (LibANGLE Translation Wrapper) EGL entry point.
 */
class AngleLtwRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles3_ltw"

    override fun getUniqueIdentifier(): String = "4b6f9d2c-8e1a-4f5b-9c3d-7a2e5f8b1d60"

    override fun getRendererName(): String = "ANGLE (LTW)"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "LIBGL_ES" to "3"
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libltw.so"

    override fun getRendererEGL(): String = "libltw.so"
}
