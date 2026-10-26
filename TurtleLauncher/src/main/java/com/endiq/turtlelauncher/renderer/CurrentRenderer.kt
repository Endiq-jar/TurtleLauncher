package com.endiq.turtlelauncher.renderer

import android.content.Context
import com.endiq.turtlelauncher.setting.AllSettings

/**
 * Resolves the renderer selected in Settings -> Video -> Renderer.
 *
 * The setting stores the renderer's *unique identifier* (a UUID-like string such
 * as `18d93f17-...`), while [RendererCatalog] is keyed by the short renderer id
 * (`ZINK`, `MG`, ...). Passing the setting value straight into
 * `RendererCatalog.supportsShaderPacks()` therefore always answers false, which
 * is exactly the kind of silent false negative that makes "shaders don't work"
 * impossible to explain. Always go through this object instead.
 */
object CurrentRenderer {

    /** The raw value of the renderer setting (a unique identifier). */
    fun uniqueId(): String = AllSettings.renderer.getValue()

    /** The selected renderer, or null if it is not available on this device. */
    fun resolve(context: Context): RendererInterface? = runCatching {
        Renderers.getCompatibleRenderers(context).second
            .firstOrNull { it.getUniqueIdentifier() == uniqueId() }
    }.getOrNull()

    /** The short renderer id from [RendererCatalog], falling back to the raw setting value. */
    fun id(context: Context): String = resolve(context)?.getRendererId() ?: uniqueId()

    /** A user-facing renderer name (e.g. "Zink"), for warnings and summaries. */
    fun name(context: Context): String = resolve(context)?.getRendererName() ?: id(context)

    /** Whether shader packs (Iris/OptiFine) can actually render under the selected renderer. */
    fun supportsShaderPacks(context: Context): Boolean =
        RendererCatalog.supportsShaderPacks(id(context))
}
