package com.endiq.turtlelauncher.renderer.renderers

import com.endiq.turtlelauncher.renderer.RendererInterface
import com.endiq.turtlelauncher.renderer.mobileglues.MobileGluesSettings
import java.io.File

/** TeamPojavLauncher's SFPEW/MobileGlues chain. See docs/UPSTREAM_PORTS.md. */
class SFPEWRenderer : RendererInterface {
    companion object { const val ID = "SFPEW" }
    @Volatile private var angelica = false
    fun prepareForGame(directory: File) {
        angelica = File(directory, "mods").listFiles()?.any {
            it.isFile && it.extension.equals("jar", true) && it.name.contains("angelica", true)
        } == true
    }
    override fun getRendererId() = ID
    override fun getNativeRendererId() = "opengles"
    override fun getUniqueIdentifier() = "0fed5da3-0eb4-4c45-99e2-1d88f50dbef2"
    override fun getRendererName() = "SFPEW / MobileGlues"
    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf("MG_DIR_PATH" to MobileGluesSettings.prepareDirectory()) +
            if (angelica) emptyMap() else mapOf("SFPEW_EGL" to "libmobileglues.so")
    }
    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { listOf("libmobileglues.so") }
    override fun getRendererLibrary() = if (angelica) "libmobileglues.so" else "libSimpleFPEWrapper.so"
    override fun getRendererEGL() = getRendererLibrary()
}
