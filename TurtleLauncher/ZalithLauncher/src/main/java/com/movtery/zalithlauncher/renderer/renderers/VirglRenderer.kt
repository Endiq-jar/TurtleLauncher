package com.movtery.zalithlauncher.renderer.renderers

import com.movtery.zalithlauncher.launch.VirglTestServerManager
import com.movtery.zalithlauncher.renderer.RendererInterface

/**
 * VirGL over Mesa's software vtest transport — see [VirglTestServerManager] for exactly
 * why this is marked experimental/untested rather than a normal renderer option: there's
 * no dedicated "VirGL" plugin anywhere upstream in this ecosystem the way there is for
 * MobileGlues/LTW/Krypton Wrapper, so this reuses the vtest server binary and Mesa gallium
 * driver already bundled in this APK instead. LaunchGame starts/stops the actual server
 * process around the game process's lifetime, keyed off this renderer's id.
 *
 * id deliberately doesn't contain "opengles" or "zink" so it falls into JREUtils' generic
 * Mesa driver branch (MESA_GLSL_CACHE_DIR + LIB_MESA_NAME), the same branch the old
 * Freedreno/Panfrost/VirGL gallium driver set used before this project's renderer port.
 */
class VirglRenderer : RendererInterface {
    override fun getRendererId(): String = "virgl_vtest"

    override fun getUniqueIdentifier(): String = "9c3e7a2d-4f1b-4a6e-8d5c-2b7f9e1a3c6d"

    override fun getRendererName(): String = "VirGL (experimental, untested)"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "GALLIUM_DRIVER" to "virpipe",
            "VTEST_SOCKET_NAME" to VirglTestServerManager.socketPath
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libgallium_dri.so"
}
