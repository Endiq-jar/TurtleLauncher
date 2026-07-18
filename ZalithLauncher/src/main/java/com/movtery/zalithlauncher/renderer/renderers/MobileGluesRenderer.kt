package com.movtery.zalithlauncher.renderer.renderers

import com.movtery.zalithlauncher.renderer.RendererInterface
import com.movtery.zalithlauncher.utils.path.PathManager

/**
 * Ported from Copper Launcher's "opengles_mobileglues" renderer option.
 * MobileGlues translates desktop GL calls directly to native GLES (no software rasterizer
 * layer), generally lighter weight than GL4ES on modern mobile GPU drivers.
 */
class MobileGluesRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles_mobileglues"

    override fun getUniqueIdentifier(): String = "2e8d4f6a-9b1c-4e3a-8d5f-6c2a9e4b8f13"

    override fun getRendererName(): String = "MobileGlues"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "LIBGL_ES" to "3",
            "MG_DIR_PATH" to (PathManager.DIR_DATA + "/MobileGlues")
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libmobileglues.so"

    override fun getRendererEGL(): String = "libmobileglues.so"
}
