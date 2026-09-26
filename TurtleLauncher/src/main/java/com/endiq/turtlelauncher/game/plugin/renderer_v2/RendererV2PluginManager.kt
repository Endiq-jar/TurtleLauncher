/*
 * Turtle Launcher
 * Copyright (C) 2025 Endiq <endiq228@qq> and contributors
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

package com.endiq.turtlelauncher.game.plugin.renderer_v2

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Bundle
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.game.plugin.ApkPlugin
import com.endiq.turtlelauncher.game.plugin.ApkPluginManager
import com.endiq.turtlelauncher.game.plugin.cacheAppIcon
import com.endiq.turtlelauncher.game.plugin.renderer_v2.data.RendererConfig
import com.endiq.turtlelauncher.game.plugin.renderer_v2.data.resolveNativePaths
import com.endiq.turtlelauncher.path.GLOBAL_JSON
import com.endiq.turtlelauncher.utils.logging.Logger

object RendererV2PluginManager : ApkPluginManager() {
    private const val TAG = "RendererV2Plugin"
    private val rendererPluginList: MutableList<RendererV2Data> = mutableListOf()

    fun getRendererList(): List<RendererV2Data> = rendererPluginList


    fun clearPlugin() {
        rendererPluginList.clear()
    }

    /**
     * Recognizes plugins and stashes their [ApplicationInfo] without loading
     */
    override fun parseApkPlugin(
        context: Context,
        info: ApplicationInfo,
        loaded: (ApkPlugin) -> Unit
    ) {
        if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) return
        val metaData = info.metaData ?: return

        // Read the launcher config asset
        val configRes = metaData.getStringRes("fclPlugin_V2") ?: return
        val configString = context.getString(info, configRes) ?: return

        val pm = context.packageManager
        val packageName = info.packageName

        // Deserialize the renderer config
        val config = runCatching {
            GLOBAL_JSON.decodeFromString<RendererConfig>(configString)
        }.onFailure { e ->
            Logger.error(TAG, "Failed to parse config JSON from $packageName", e)
        }.getOrNull() ?: return

        // Fetch the plugin's application info
        val appLabel = info.loadLabel(pm).toString()
        val appVersion = runCatching {
            pm.getPackageInfo(packageName, 0).versionName ?: ""
        }.getOrDefault("")

        rendererPluginList.add(
            RendererV2Data(
                packageName = packageName,
                nativePath = info.nativeLibraryDir,
                summary = context.getString(R.string.settings_renderer_from_plugins, appLabel),
                renderer = config.resolveNativePaths(info.nativeLibraryDir)
            ) { metaString ->
                context.getMetaString(info, metaString)
            }
        )

        // The target plugin loaded successfully
        runCatching {
            cacheAppIcon(context, info)
            ApkPlugin(
                packageName = packageName,
                appName = appLabel,
                appVersion = appVersion
            )
        }.getOrNull()?.let { loaded(it) }
    }

    private fun Bundle.getStringRes(key: String): Int? {
        return runCatching {
            getInt(key, -1).takeIf { it > 0 }
        }.getOrNull()
    }

    private fun Context.getString(info: ApplicationInfo, path: Int): String? {
        return runCatching {
            packageManager.getResourcesForApplication(info).getString(path)
        }.getOrNull()
    }

    private fun Context.getMetaString(info: ApplicationInfo, key: String): String? {
        return runCatching {
            val metaData = info.metaData ?: return null
            val path = metaData.getStringRes(key) ?: return null
            packageManager.getResourcesForApplication(info).getString(path)
        }.getOrNull()
    }

    /**
     * Removes renderers that failed to load
     */
    fun removeRenderer(failedToLoadList: List<RendererV2Data>) {
        rendererPluginList.removeAll { it in failedToLoadList }
    }
}
