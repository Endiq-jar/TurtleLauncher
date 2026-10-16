package com.endiq.turtlelauncher.renderer.renderers

import com.endiq.turtlelauncher.utils.path.PathManager
import com.endiq.turtlelauncher.renderer.RendererInterface
import java.io.File

class MobileGluesRenderer : RendererInterface {
    companion object {
        const val ID = "MOBILEGLUES"
    }

    override fun getRendererId(): String = ID

    override fun getNativeRendererId(): String = "opengles"

    override fun getUniqueIdentifier(): String = "4b4b8e4b-083d-429c-97e1-5e8239b6dc17"

    override fun getRendererName(): String = "MobileGlues"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf("MG_DIR_PATH" to com.endiq.turtlelauncher.renderer.mobileglues.MobileGluesSettings.prepareDirectory())
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libmobileglues.so"

    override fun getRendererEGL(): String = "libmobileglues.so"
}
