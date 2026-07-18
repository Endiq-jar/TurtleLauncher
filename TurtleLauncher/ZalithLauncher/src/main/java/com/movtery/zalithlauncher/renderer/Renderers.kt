package com.movtery.zalithlauncher.renderer

import android.content.Context
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.renderer.renderers.AngleLtwRenderer
import com.movtery.zalithlauncher.renderer.renderers.DesktopGLZinkKopperRenderer
import com.movtery.zalithlauncher.renderer.renderers.DesktopGLZinkRenderer
import com.movtery.zalithlauncher.renderer.renderers.GL4ES25Renderer
import com.movtery.zalithlauncher.renderer.renderers.GL4ES3Renderer
import com.movtery.zalithlauncher.renderer.renderers.GL4ESRenderer
import com.movtery.zalithlauncher.renderer.renderers.MobileGluesRenderer
import com.movtery.zalithlauncher.renderer.renderers.NgGL4ESRenderer
import com.movtery.zalithlauncher.renderer.renderers.VirglRenderer
import com.movtery.zalithlauncher.renderer.renderers.VulkanZinkRenderer
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.Architecture
import net.kdt.pojavlaunch.Tools
import java.io.File

/**
 * 启动器所有渲染器总管理者，启动器内置的渲染器与渲染器插件加载的渲染器，都会加载到这里
 */
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

        // Full renderer set ported from Copper Launcher, replacing the previous
        // vendor-specific gallium driver set (Freedreno/Panfrost/VirGL) — VirGL is back
        // as VirglRenderer, but via the vtest transport rather than the old direct
        // gallium driver; see VirglTestServerManager for why, and why it's marked
        // experimental.
        addRenderers(
            GL4ESRenderer(),
            GL4ES25Renderer(),
            GL4ES3Renderer(),
            NgGL4ESRenderer(),
            MobileGluesRenderer(),
            AngleLtwRenderer(),
            VulkanZinkRenderer(),
            DesktopGLZinkRenderer(),
            DesktopGLZinkKopperRenderer(),
            VirglRenderer()
        )
    }

    /**
     * 获取兼容当前设备的所有渲染器
     */
    fun getCompatibleRenderers(context: Context): Pair<RenderersList, List<RendererInterface>> = compatibleRenderers ?: run {
        val deviceHasVulkan = Tools.checkVulkanSupport(context.packageManager)
        // Currently, only 32-bit x86 does not have the Zink binary
        val deviceHasZinkBinary = !(Architecture.is32BitsDevice() && Architecture.isx86Device())

        val compatibleRenderers1: MutableList<RendererInterface> = mutableListOf()
        renderers.forEach { renderer ->
            if (renderer.getRendererId().contains("vulkan") && !deviceHasVulkan) return@forEach
            if (renderer.getRendererId().contains("zink") && !deviceHasZinkBinary) return@forEach
            // TurtleLauncher: a built-in renderer entry represents a library shipped directly
            // in this APK's own jniLibs (plugin-provided renderers are a separate list,
            // resolved through RendererPluginManager). Diagnostic only, NOT filtered out here:
            // an audit found most built-in renderers on arm64-v8a reference a filename that
            // isn't actually bundled (libgl4es_115.so vs the shipped libgl4es_114.so;
            // libmobileglues.so, libltw.so, libOSMesa.so, libglxshim.so, libEGL_mesa.so all
            // absent) - hard-filtering on that would collapse the picker to effectively one
            // working option (Krypton Wrapper), which is a bigger, more surprising change
            // than "add renderer options" asked for. Logged so it's visible without silently
            // deciding for the user; the actual fix is aligning each getRendererLibrary()/
            // getRendererEGL() to the real shipped filename (or shipping the missing ones).
            if (!hasRequiredLibrary(renderer)) {
                Logging.w("Renderers", "${renderer.getRendererName()} (${renderer.getRendererId()}) references a library not found in this ABI's jniLibs - selecting it will likely crash with UnsatisfiedLinkError")
            }
            compatibleRenderers1.add(renderer)
        }

        val rendererIdentifiers: MutableList<String> = mutableListOf()
        val rendererNames: MutableList<String> = mutableListOf()
        compatibleRenderers1.forEach { renderer ->
            rendererIdentifiers.add(renderer.getUniqueIdentifier())
            rendererNames.add(renderer.getRendererName())
        }

        val rendererPair = Pair(RenderersList(rendererIdentifiers, rendererNames), compatibleRenderers1)
        compatibleRenderers = rendererPair
        rendererPair
    }

    private fun hasRequiredLibrary(renderer: RendererInterface): Boolean {
        fun exists(libName: String): Boolean =
            !libName.startsWith("/") && File(PathManager.DIR_NATIVE_LIB, libName).exists()
        if (!exists(renderer.getRendererLibrary())) return false
        renderer.getRendererEGL()?.let { eglName -> if (!exists(eglName)) return false }
        return true
    }

    /**
     * 加入一些渲染器
     */
    @JvmStatic
    fun addRenderers(vararg renderers: RendererInterface) {
        renderers.forEach { renderer ->
            addRenderer(renderer)
        }
    }

    /**
     * 加入单个渲染器
     */
    @JvmStatic
    fun addRenderer(renderer: RendererInterface): Boolean {
        return if (this.renderers.any { it.getUniqueIdentifier() == renderer.getUniqueIdentifier() }) {
            Logging.w("Renderers", "The unique identifier of this renderer (${renderer.getRendererName()} - ${renderer.getUniqueIdentifier()}) conflicts with an already loaded renderer. " +
                    "Normally, this shouldn't happen. You deliberately caused this conflict, didn't you, user?")
            false
        } else {
            this.renderers.add(renderer)
            Logging.i("Renderers", "Renderer loaded: ${renderer.getRendererName()} (${renderer.getRendererId()} - ${renderer.getUniqueIdentifier()})")
            true
        }
    }

    /**
     * 设置当前的渲染器
     * @param context 用于初始化适配当前设备的渲染器
     * @param uniqueIdentifier 渲染器的唯一标识符，用于找到当前想要设置的渲染器
     * @param retryToFirstOnFailure 如果未找到匹配的渲染器，是否跳回渲染器列表的首个渲染器
     */
    fun setCurrentRenderer(context: Context, uniqueIdentifier: String, retryToFirstOnFailure: Boolean = true) {
        if (!isInitialized) throw IllegalStateException("Uninitialized renderer!")
        val compatibleRenderers = getCompatibleRenderers(context).second
        currentRenderer = compatibleRenderers.find { it.getUniqueIdentifier() == uniqueIdentifier } ?: run {
            if (retryToFirstOnFailure) {
                val renderer = compatibleRenderers[0]
                Logging.w("Renderers", "Incompatible renderer $uniqueIdentifier will be replaced with ${renderer.getUniqueIdentifier()} (${renderer.getRendererName()})")
                renderer
            } else null
        }
    }

    /**
     * 获取当前的渲染器
     */
    fun getCurrentRenderer(): RendererInterface {
        if (!isInitialized) throw IllegalStateException("Uninitialized renderer!")
        return currentRenderer ?: throw IllegalStateException("Current renderer not set")
    }

    /**
     * 当前是否设置了渲染器
     */
    fun isCurrentRendererValid(): Boolean = isInitialized && this.currentRenderer != null
}