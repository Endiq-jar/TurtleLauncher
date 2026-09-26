package com.endiq.turtlelauncher.game.plugin.renderer_v2.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File

/**
 * @param displayName               name shown to users
 * @param rendererId                renderer ID; the launcher sets it as the env var `POJAV_RENDERER`, so it **no longer needs stuffing into [env]**
 * @param rendererGLPath            concrete path of the renderer GL library
 * @param rendererEGLPath           concrete path of the renderer EGL
 * @param dlopenLibPaths            concrete paths of the libraries needing dlopen
 * @param env                       renderer env var list
 * @param minMCVer                  minimum supported Minecraft version, e.g. `1.17`; null means no lower limit
 * @param maxMCVer                  maximum supported Minecraft version, e.g. `1.17`; null means no upper limit
 */
@Serializable
data class RendererConfig(
    @SerialName("displayName")
    val displayName: String,
    @SerialName("rendererId")
    val rendererId: String,
    @SerialName("rendererGLPath")
    val rendererGLPath: String,
    @SerialName("rendererEGLPath")
    val rendererEGLPath: String,
    @SerialName("dlopenLibPaths")
    val dlopenLibPaths: List<String>,
    @SerialName("env")
    val env: List<Env>,
    @SerialName("minMCVer")
    val minMCVer: String?,
    @SerialName("maxMCVer")
    val maxMCVer: String?,
) {
    @Serializable
    sealed interface Env {
        /**
         * A plain env var: non-configurable and always present
         */
        @Serializable
        @SerialName("NormalEnv")
        data class NormalEnv(
            @SerialName("key")
            val key: String,
            @SerialName("value")
            val value: String,
        ): Env

        /**
         * Env var freely settable to one of the preset values
         * @see EnvItems
         * @param check launcher-side control of whether this env var is applied
         *
         *              When null, the launcher always applies this env var
         *              When true or false, it specifies the launcher's default enabled/disabled state for this env var
         * @param title the entry's title (meta-data index)
         * @param items the env var's config options
         */
        @Serializable
        @SerialName("SelectableEnv")
        data class SelectableEnv(
            @SerialName("key")
            val key: String,
            @SerialName("title")
            val title: MetaString? = null,
            @SerialName("check")
            val check: Boolean? = true,
            @SerialName("items")
            val items: EnvItems
        ): Env

        /**
         * Env var whose value the user can edit freely
         * @param title the entry's title (meta-data index)
         * @param defaultValue default value; when empty or null, the launcher won't use this env var
         */
        @Serializable
        @SerialName("CustomizableEnv")
        data class CustomizableEnv(
            @SerialName("key")
            val key: String,
            @SerialName("title")
            val title: MetaString? = null,
            @SerialName("defaultValue")
            val defaultValue: String? = null,
        ): Env

        /**
         * Toggleable env var (use / don't use)
         * @param title the entry's title (meta-data index)
         * @param toggle decides whether the launcher uses this env var
         */
        @Serializable
        @SerialName("ToggleableEnv")
        data class ToggleableEnv(
            @SerialName("key")
            val key: String,
            @SerialName("value")
            val value: String,
            @SerialName("title")
            val title: MetaString? = null,
            @SerialName("toggle")
            val toggle: Boolean = true,
        ): Env
    }

    /**
     * Env var config entries, from which the launcher builds
     * @param defaultValue the default env var
     * @param values the selectable env vars
     */
    @Serializable
    data class EnvItems(
        @SerialName("defaultValue")
        val defaultValue: String,
        @SerialName("values")
        val values: List<String>,
    )

    /**
     * String resources declared in meta-data, which the launcher resolves into localized text
     */
    @Serializable
    data class MetaString(
        @SerialName("key")
        val key: String
    )
}

private fun String.resolveNativePath(nativeLibDir: String): String {
    if (!startsWith("**|")) return this
    return File(nativeLibDir, removePrefix("**|")).absolutePath
}

/**
 * Replaces config paths prefixed with `**|` by the plugin's real absolute nativeLibraryDir
 */
fun RendererConfig.resolveNativePaths(nativeLibDir: String): RendererConfig {
    fun String.replacePath() = this.resolveNativePath(nativeLibDir)

    return copy(
        rendererGLPath = rendererGLPath.replacePath(),
        rendererEGLPath = rendererEGLPath.replacePath(),
        dlopenLibPaths = dlopenLibPaths.map { it.replacePath() },
        env = env.map { env ->
            when (env) {
                is RendererConfig.Env.NormalEnv -> env.copy(value = env.value.replacePath())
                is RendererConfig.Env.ToggleableEnv -> env.copy(value = env.value.replacePath())
                // The other types expose values to users; path splicing is unsupported!
                else -> env
            }
        }
    )
}
