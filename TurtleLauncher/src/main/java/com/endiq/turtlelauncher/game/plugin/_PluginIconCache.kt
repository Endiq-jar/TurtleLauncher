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

package com.endiq.turtlelauncher.game.plugin

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import com.endiq.turtlelauncher.path.PathManager
import com.endiq.turtlelauncher.utils.image.toBitmap
import com.endiq.turtlelauncher.utils.logging.Logger
import org.apache.commons.io.FileUtils
import java.io.File

private const val TAG = "PluginIconCache"

fun appCacheIcon(packageName: String): File = File(PathManager.DIR_CACHE_APP_ICON, "$packageName.png")

/**
 * Caches app icons into a local cache directory, for quick loading by package name
 */
fun cacheAppIcon(context: Context, appInfo: ApplicationInfo) {
    val packageName = appInfo.packageName
    val iconFile = appCacheIcon(packageName)

    if (iconFile.exists()) return

    runCatching {
        context.packageManager.let { manager ->
            //Read the icon and convert it to a bitmap
            val icon = appInfo.loadIcon(manager).toBitmap()
            //Start caching
            iconFile.outputStream().use { stream ->
                icon.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
        }
    }.onFailure {
        FileUtils.deleteQuietly(iconFile)
        Logger.warning(TAG, "Failed to cache icon for $packageName at ${iconFile.absolutePath}", it)
    }
}