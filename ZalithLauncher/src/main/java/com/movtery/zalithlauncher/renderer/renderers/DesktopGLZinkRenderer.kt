package com.movtery.zalithlauncher.renderer.renderers

import com.movtery.zalithlauncher.renderer.RendererInterface

/**
 * Ported from Copper Launcher's "opengles3_desktopgl_zink" renderer option.
 * Desktop-GL-flavoured Zink path through a glX shim.
 */
class DesktopGLZinkRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles3_desktopgl_zink"

    override fun getUniqueIdentifier(): String = "6d2f8a4c-1b9e-4a7d-b3c5-8e0f2a6d4b71"

    override fun getRendererName(): String = "Desktop GL Zink"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "LIBGL_ES" to "3",
            "MESA_GL_VERSION_OVERRIDE" to "4.6COMPAT",
            "MESA_GLSL_VERSION_OVERRIDE" to "460"
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libglxshim.so"
}
