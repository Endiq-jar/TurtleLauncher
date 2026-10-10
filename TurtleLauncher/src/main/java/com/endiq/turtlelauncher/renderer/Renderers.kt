package com.endiq.turtlelauncher.renderer

import android.content.Context
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.renderer.renderers.AngleRenderer
import com.endiq.turtlelauncher.renderer.renderers.FreedrenoRenderer
import com.endiq.turtlelauncher.renderer.renderers.HolyGL4ESRenderer
import com.endiq.turtlelauncher.renderer.renderers.NWRenderer
import com.endiq.turtlelauncher.renderer.renderers.VGPURenderer
import com.endiq.turtlelauncher.renderer.renderers.VirGLRenderer
import com.endiq.turtlelauncher.renderer.renderers.ZinkRenderer
import com.endiq.turtlelauncher.utils.path.PathManager
import net.endiq.launcher.Tools
import java.io.File

object Renderers {
    private val renderers: MutableList<RendererInterface> = mutableListOf()
    private var compatibleRenderers: Pair<RenderersList, MutableList<RendererInterface>>? = null
    private var currentRenderer: RendererInterface? = null
    private var isInitialized: Boolean = false

    fun init(reset: Boolean = false) {
        if (isInitialized && !reset) return
        isInitialized = true
        if (reset) {
            renderers.clear()
            compatibleRenderers = null
            currentRenderer = null
        }
        // Only register renderer implementations that still exist in this source tree.
        addRenderers(
            HolyGL4ESRenderer(),
            NWRenderer(),
            AngleRenderer(),
            VirGLRenderer(),
            ZinkRenderer(),
            FreedrenoRenderer(),
            VGPURenderer()
        )
    }

    fun getCompatibleRenderers(context: Context): Pair<RenderersList, List<RendererInterface>> {
        val cached = compatibleRenderers
        if (cached != null) return Pair(cached.first, cached.second)

        val deviceHasVulkan = Tools.checkVulkanSupport(context.packageManager)
        val compatible: MutableList<RendererInterface> = mutableListOf()
        for (renderer in renderers) {
            if (renderer.getRendererId() == ZinkRenderer.ID && !deviceHasVulkan) continue
            if (!hasRequiredLibrary(renderer)) {
                Logging.w(
                    "Renderers",
                    "${renderer.getRendererName()} (${renderer.getRendererId()}) references a library not found in this ABI's jniLibs - excluding it from the picker"
                )
                continue
            }
            compatible.add(renderer)
        }

        val identifiers = compatible.map { it.getUniqueIdentifier() }
        val names = compatible.map { it.getRendererName() }
        val result = Pair(RenderersList(identifiers, names), compatible)
        compatibleRenderers = result
        return Pair(result.first, result.second)
    }

    private fun hasRequiredLibrary(renderer: RendererInterface): Boolean {
        fun exists(libName: String): Boolean =
            if (libName.startsWith("/")) File(libName).exists()
            else File(PathManager.DIR_NATIVE_LIB, libName).exists()

        if (!exists(renderer.getRendererLibrary())) return false
        renderer.getRendererEGL()?.let { eglName ->
            if (eglName != "libEGL.so" && !exists(eglName)) return false
        }
        return true
    }

    @JvmStatic
    fun addRenderers(vararg renderers: RendererInterface) {
        renderers.forEach { addRenderer(it) }
    }

    @JvmStatic
    fun addRenderer(renderer: RendererInterface): Boolean {
        if (this.renderers.any { it.getUniqueIdentifier() == renderer.getUniqueIdentifier() }) {
            Logging.w(
                "Renderers",
                "The unique identifier of this renderer (${renderer.getRendererName()} - ${renderer.getUniqueIdentifier()}) conflicts with an already loaded renderer."
            )
            return false
        }
        this.renderers.add(renderer)
        Logging.i("Renderers", "Renderer loaded: ${renderer.getRendererName()} (${renderer.getRendererId()} - ${renderer.getUniqueIdentifier()})")
        compatibleRenderers = null
        return true
    }

    fun setCurrentRenderer(context: Context, uniqueIdentifier: String, retryToFirstOnFailure: Boolean = true) {
        if (!isInitialized) throw IllegalStateException("Uninitialized renderer!")
        val compatible = getCompatibleRenderers(context).second
        currentRenderer = compatible.find { it.getUniqueIdentifier() == uniqueIdentifier } ?: run {
            if (retryToFirstOnFailure) {
                val renderer = compatible.firstOrNull()
                    ?: throw IllegalStateException("No compatible renderer is available for this device")
                Logging.w("Renderers", "Incompatible renderer $uniqueIdentifier will be replaced with ${renderer.getUniqueIdentifier()} (${renderer.getRendererName()})")
                renderer
            } else null
        }
    }

    fun removeRenderers(uniqueIdentifiers: Collection<String>) {
        if (uniqueIdentifiers.isEmpty()) return
        renderers.removeAll { it.getUniqueIdentifier() in uniqueIdentifiers }
        currentRenderer?.let { current ->
            if (current.getUniqueIdentifier() in uniqueIdentifiers) currentRenderer = null
        }
        compatibleRenderers = null
    }

    fun getCurrentRenderer(): RendererInterface {
        if (!isInitialized) throw IllegalStateException("Uninitialized renderer!")
        return currentRenderer ?: throw IllegalStateException("Current renderer not set")
    }

    fun isCurrentRendererValid(): Boolean = isInitialized && currentRenderer != null
}
