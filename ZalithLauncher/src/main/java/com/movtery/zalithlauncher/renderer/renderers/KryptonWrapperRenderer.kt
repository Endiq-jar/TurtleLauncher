package com.movtery.zalithlauncher.renderer.renderers

import com.movtery.zalithlauncher.renderer.RendererInterface

/**
 * Krypton Wrapper (NG-GL4ES) - an alternate "next-gen" GL4ES build. Library names and the
 * unique identifier are taken directly from FCL-Team/FoldCraftLauncher's own
 * RendererManager.kt (ID_NGGL4ES), not guessed. Real upstream:
 * github.com/BZLZHH/NGG-FCLRendererPlugin.
 */
class KryptonWrapperRenderer : RendererInterface {
    companion object {
        const val ID = "NGGL4ES"
    }

    override fun getRendererId(): String = ID

    // See RendererInterface.getNativeRendererId's doc comment. Krypton Wrapper's own
    // launch-log output confirms it goes through plain libGLESv2.so/libEGL.so at the
    // native layer ("Using GLES 3.x backend", "loaded libGLESv2.so", "loaded libEGL.so") -
    // exactly what pojavInitOpenGL's "opengles"-prefixed branch sets up. "NGGL4ES" itself
    // isn't one of the strings that branch (or any other) matches, which is what was
    // crashing every Krypton Wrapper launch at pojavInitOpenGL+0x44c (SIGSEGV, pc=0x0 -
    // the unguarded fallback for an unrecognized POJAV_RENDERER value).
    override fun getNativeRendererId(): String = "opengles"

    override fun getUniqueIdentifier(): String = "e7b90ed6-e518-4d4e-93dc-5c7133cd5b31"

    override fun getRendererName(): String = "Krypton Wrapper"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy { emptyMap() }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libng_gl4es.so"

    override fun getRendererEGL(): String = "libEGL.so"
}
