package com.movtery.zalithlauncher.renderer.renderers

import com.movtery.zalithlauncher.renderer.RendererInterface
import com.movtery.zalithlauncher.utils.path.PathManager
import java.io.File

/**
 * Ported from Copper Launcher's "vulkan_zink" renderer option.
 * Mesa Zink (OpenGL-over-Vulkan) via the OSMesa software/driver bridge.
 */
class VulkanZinkRenderer : RendererInterface {
    override fun getRendererId(): String = "vulkan_zink"

    override fun getUniqueIdentifier(): String = "0fa435e2-46df-45c9-906c-b29606aaef00"

    override fun getRendererName(): String = "Vulkan Zink"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "MESA_LOADER_DRIVER_OVERRIDE" to "zink",
            // Full GL 4.6 compat — Zink advertises this and Minecraft 26.x needs the
            // DSA texture entry points it brings (glTextureStorage2D/glTextureSubImage2D).
            "MESA_GL_VERSION_OVERRIDE" to "4.6COMPAT",
            "MESA_GLSL_VERSION_OVERRIDE" to "460",
            "VTEST_SOCKET_NAME" to File(PathManager.DIR_CACHE, ".virgl_test").absolutePath
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libOSMesa.so"
}
