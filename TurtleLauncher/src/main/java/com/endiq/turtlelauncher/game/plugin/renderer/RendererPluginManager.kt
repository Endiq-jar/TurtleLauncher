/*
 * Turtle Launcher
 * Copyright (C) 2025 Endiq and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.endiq.turtlelauncher.game.plugin.renderer

import android.content.Context
import android.content.pm.ApplicationInfo
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.game.plugin.ApkPlugin
import com.endiq.turtlelauncher.game.plugin.ApkPluginManager
import com.endiq.turtlelauncher.game.plugin.cacheAppIcon
import com.endiq.turtlelauncher.game.plugin.renderer_v2.RendererV2PluginManager
import com.endiq.turtlelauncher.game.renderer.Renderers

/**
 * FCL and TurtleLauncher renderer plugins; local renderer plugins are also supported
 * [FCL Renderer Plugin](https://github.com/FCL-Team/FCLRendererPlugin)
 */
object RendererPluginManager: ApkPluginManager() {
    private val rendererPluginList: MutableList<RendererPlugin> = mutableListOf()

    /**
     * Returns all renderers loaded by the current renderer plugin
     */
    fun getRendererList(): List<RendererPlugin> = rendererPluginList

    /**
     * Removes specific loaded renderers
     */
    fun removeRenderer(rendererPlugins: Collection<RendererPlugin>) {
        rendererPluginList.removeAll(rendererPlugins)
    }

    /**
     * Renderers loaded by the currently selected renderer plugin
     * Decided by the unique identifier of the renderer picked in the main renderer manager
     */
    val selectedRendererPlugin: RendererPlugin?
        get() {
            val currentRenderer = runCatching {
                Renderers.getCurrentRenderer().getUniqueIdentifier()
            }.getOrNull()
            return rendererPluginList.find { it.packageName == currentRenderer }
        }

    /**
     * Clears renderer plugins
     */
    fun clearPlugin() {
        rendererPluginList.clear()
    }

    /**
     * Whether the current renderer plugin carries config entries (software-style plugins, whitelisted package names)
     */
    @JvmStatic
    fun isConfigurablePlugin(rendererUniqueIdentifier: String): Boolean {
        val renderer = rendererPluginList.find { it.packageName == rendererUniqueIdentifier }
        return renderer?.isConfigurable == true
    }

    /**
     * Parses TurtleLauncher and FCL renderer plugins
     */
    override fun parseApkPlugin(
        context: Context,
        info: ApplicationInfo,
        loaded: (ApkPlugin) -> Unit
    ) {
        if (info.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
            val metaData = info.metaData ?: return
            if (
                metaData.getBoolean("fclPlugin", false) ||
                metaData.getBoolean("turtleRendererPlugin", false)
            ) {
                val packageManager = context.packageManager
                val packageName = info.packageName
                val appName = info.loadLabel(packageManager).toString()

                // If a new-architecture renderer plugin was loaded, don't keep loading the old-architecture one it also provides
                if (
                    RendererV2PluginManager.getRendererList().any { v2Plugin ->
                        v2Plugin.packageName == packageName
                    }
                ) return

                val rendererString = metaData.getString("renderer") ?: return
                val des = metaData.getString("des") ?: return
                val pojavEnvString = metaData.getString("pojavEnv") ?: return
                val nativeLibraryDir = info.nativeLibraryDir
                val renderer = rendererString.split(":")

                var rendererId: String = renderer[0]
                val envList = mutableMapOf<String, String>()
                val dlopenList = mutableListOf<String>()
                pojavEnvString.split(":").forEach { envString ->
                    if (envString.contains("=")) {
                        val stringList = envString.split("=")
                        val key = stringList[0]
                        val value = stringList[1]
                        when (key) {
                            "POJAV_RENDERER" -> rendererId = value
                            "DLOPEN" -> {
                                value.split(",").forEach { lib ->
                                    dlopenList.add(lib)
                                }
                            }
                            "LIB_MESA_NAME", "MESA_LIBRARY" -> envList[key] = "$nativeLibraryDir/$value"
                            else -> envList[key] = value
                        }
                    }
                }

                val plugin = RendererPlugin(
                    packageName = packageName,
                    id = rendererId,
                    displayName = des,
                    summary = context.getString(R.string.settings_renderer_from_plugins, appName),
                    minMCVer = metaData.getVersionString("minMCVer"),
                    maxMCVer = metaData.getVersionString("maxMCVer"),
                    glName = renderer[1],
                    eglName = renderer[2].progressEglName(nativeLibraryDir),
                    path = nativeLibraryDir,
                    env = envList,
                    dlopen = dlopenList,
                    isConfigurable = packageName in setOf(
                        "com.bzlzhh.plugin.ngg",
                        "com.bzlzhh.plugin.ngg.angleless",
                        "com.fcl.plugin.mobileglues"
                    )
                )

                rendererPluginList.add(plugin)

                runCatching {
                    cacheAppIcon(context, info)
                    ApkPlugin(
                        packageName = packageName,
                        appName = appName,
                        appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
                    )
                }.getOrNull()?.let { loaded(it) }
            }
        }
    }

    private fun String.progressEglName(libPath: String): String =
        if (startsWith("/")) "$libPath$this"
        else this
}