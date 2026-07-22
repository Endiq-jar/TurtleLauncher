package com.movtery.zalithlauncher.renderer

import android.content.Context
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.renderer.renderers.FreedrenoRenderer
import com.movtery.zalithlauncher.renderer.renderers.HolyGL4ESRenderer
import com.movtery.zalithlauncher.renderer.renderers.KryptonWrapperRenderer
import com.movtery.zalithlauncher.renderer.renderers.VGPURenderer
import com.movtery.zalithlauncher.renderer.renderers.VirGLRenderer
import com.movtery.zalithlauncher.renderer.renderers.ZinkRenderer
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.Tools
import java.io.File

/**
 * 启动器所有渲染器总管理者，启动器内置的渲染器与渲染器插件加载的渲染器，都会加载到这里
 *
 * TurtleLauncher: this renderer set was fully replaced with the six renderers
 * FCL-Team/FoldCraftLauncher ships (Holy GL4ES, VirGL, VGPU, Zink, Freedreno, Krypton
 * Wrapper) - see each class in renderer/renderers/ for its FCL-sourced library names and
 * unique identifier, and [RendererCatalog] for compatibility ranges/badges.
 * The registry/plugin architecture below (RendererInterface + this object) is unchanged -
 * it was already an isolated, one-class-per-renderer design, so nothing about *how*
 * renderers plug in needed to change, only *which* renderers are registered.
 *
 * POJAV_RENDERER native-string mapping (see RendererInterface.getNativeRendererId's doc
 * comment for why this exists at all): jniLibs/*/libpojavexec.so is a prebuilt binary -
 * there's no source for it in this repo (jni/Android.mk lists .c files that aren't
 * present; externalNativeBuild is commented out in build.gradle.kts because of that) - and
 * its pojavInitOpenGL only recognizes six legacy POJAV_RENDERER values (confirmed by
 * disassembling it, not guessed): "opengles" (prefix match), "custom_gallium",
 * "vulkan_zink", "gallium_freedreno", "gallium_panfrost", "gallium_virgl". Anything else
 * falls through its whole strcmp chain into an unguarded call through an uninitialized
 * function pointer - guaranteed SIGSEGV at pc=0x0, every device, every time. None of the
 * six renderer classes' own getRendererId() values match any of those six strings, so
 * each one below overrides getNativeRendererId() to send the right one instead:
 *   HolyGL4ESRenderer     "GL4ES"     -> "opengles"           (plain GLESv2/EGL underneath)
 *   KryptonWrapperRenderer "NGGL4ES"  -> "opengles"           (same - confirmed via its
 *                                                              own launch-log output)
 *   VirGLRenderer         "VIRGL"     -> "gallium_virgl"
 *   ZinkRenderer          "ZINK"      -> "vulkan_zink"
 *   FreedrenoRenderer     "FREEDRENO" -> "gallium_freedreno"
 *   VGPURenderer          "VGPU"      -> "opengles"           (lower confidence - no VGPU
 *                                                              launch log to confirm
 *                                                              against; see its own doc
 *                                                              comment)
 * "gallium_panfrost" and "custom_gallium" have no current owner among the six renderers -
 * leave them alone unless/until a renderer that should map to one of them shows up.
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

        // Order here is display order in the picker, not priority - HolyGL4ES first since
        // it's the RECOMMENDED-badged default (see RendererCatalog), matching FCL's own
        // convention of putting Krypton Wrapper/GL4ES first.
        addRenderers(
            HolyGL4ESRenderer(),
            KryptonWrapperRenderer(),
            VirGLRenderer(),
            ZinkRenderer(),
            FreedrenoRenderer(),
            VGPURenderer()
        )
    }

    /**
     * 获取兼容当前设备的所有渲染器
     */
    fun getCompatibleRenderers(context: Context): Pair<RenderersList, List<RendererInterface>> = compatibleRenderers ?: run {
        val deviceHasVulkan = Tools.checkVulkanSupport(context.packageManager)

        val compatibleRenderers1: MutableList<RendererInterface> = mutableListOf()
        renderers.forEach { renderer ->
            // Zink is the only one of the six that's Vulkan-backed (see ZinkRenderer's doc
            // comment) - gate it on actual device Vulkan support rather than string-matching
            // the id, since "ZINK" doesn't contain "vulkan" the way the old ids did.
            if (renderer.getRendererId() == ZinkRenderer.ID && !deviceHasVulkan) return@forEach

            // TurtleLauncher: a built-in renderer entry represents a library shipped directly
            // in this APK's own jniLibs (plugin-provided renderers are a separate list,
            // resolved through RendererPluginManager). Hard-filtered out here so a renderer
            // that can't dlopen never reaches the picker and crashes the game at
            // OpenGL-init time - see each renderer class's doc comment for where its
            // library came from and which ABIs it's bundled for (e.g. VirGL's
            // libOSMesa_81.so isn't shipped for x86, so VirGL is simply absent from the
            // picker on that ABI instead of being selectable and crashing).
            if (!hasRequiredLibrary(renderer)) {
                Logging.w("Renderers", "${renderer.getRendererName()} (${renderer.getRendererId()}) references a library not found in this ABI's jniLibs - excluding it from the picker")
                return@forEach
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
        renderer.getRendererEGL()?.let { eglName ->
            // libEGL.so (unlike libEGL_mesa.so/libEGL_angle.so) is never bundled in jniLibs -
            // it's the device's own system EGL, resolved through the normal linker namespace,
            // not this app's native lib dir. Only check bundled-EGL renderers here.
            if (eglName != "libEGL.so" && !exists(eglName)) return false
        }
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
