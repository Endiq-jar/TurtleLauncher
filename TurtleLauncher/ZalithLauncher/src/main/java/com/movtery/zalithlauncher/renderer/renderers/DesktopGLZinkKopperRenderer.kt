package com.movtery.zalithlauncher.renderer.renderers

import com.movtery.zalithlauncher.renderer.RendererInterface
import net.kdt.pojavlaunch.Tools

/**
 * Ported from Copper Launcher's "opengles3_desktopgl_zink_kopper" renderer option.
 * Desktop-GL Zink path using Mesa's native Kopper (direct-to-Vulkan WSI) EGL implementation
 * instead of a glX shim. Includes the Turnip/UBWC fix for OneUI devices from the upstream source.
 */
class DesktopGLZinkKopperRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles3_desktopgl_zink_kopper"

    override fun getUniqueIdentifier(): String = "9c3e7b1a-5d8f-4c2e-a196-3f7b8d2c5e94"

    override fun getRendererName(): String = "Desktop GL Zink (Kopper)"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        val env = mutableMapOf(
            "LIBGL_ES" to "3",
            "MESA_GL_VERSION_OVERRIDE" to "4.6COMPAT",
            "MESA_GLSL_VERSION_OVERRIDE" to "460"
        )
        if (Tools.shouldUseUBWC()) env["FD_DEV_FEATURES"] = "enable_tp_ubwc_flag_hint=1"
        env
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libglxshim.so"

    override fun getRendererEGL(): String = "libEGL_mesa.so"
}
