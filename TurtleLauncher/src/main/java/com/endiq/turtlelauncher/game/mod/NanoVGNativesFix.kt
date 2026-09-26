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

package com.endiq.turtlelauncher.game.mod

import android.content.Context
import com.endiq.turtlelauncher.game.addons.modloader.ModLoader
import com.endiq.turtlelauncher.utils.logging.Logger
import java.io.File

/**
 * Installs the bundled lwjgl-nanovg natives compatibility mod, imported from
 * [Turtle-Launcher](https://github.com/Endiq-jar/Turtle-Launcher).
 *
 * Fabric and Quilt resolve mod jars strictly from the mods folder, so this jar
 * (which simply ships the Android lwjgl-nanovg natives) must live there instead
 * of on the launcher classpath to fix `UnsatisfiedLinkError` for nanovg.
 */
object NanoVGNativesFix {
    private const val TAG = "NanoVGNativesFix"
    private const val ASSET_PATH = "compat_mods/lwjgl-nanovg-natives-1.0.2.jar"
    private const val TARGET_FILENAME = "turtle-lwjgl-nanovg-natives-fix.jar"

    /**
     * Copies the bundled nanovg-natives compatibility jar into [modsDir] if this instance's
     * loader can use it and it isn't already there. Safe to call unconditionally on every
     * launch - idempotent, local-only (no network), and a no-op for Forge/NeoForge/vanilla
     * instances where it wouldn't do anything useful anyway (this exists specifically for
     * Fabric's/Quilt's classpath model).
     */
    @JvmStatic
    fun ensureInstalled(context: Context, loader: ModLoader?, modsDir: File) {
        if (loader != ModLoader.FABRIC && loader != ModLoader.QUILT) return

        val target = File(modsDir, TARGET_FILENAME)
        if (target.exists() && target.length() > 0) return

        try {
            modsDir.mkdirs()
            context.assets.open(ASSET_PATH).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            Logger.info(TAG, "Installed nanovg natives compatibility fix into ${modsDir.path}")
        } catch (t: Throwable) {
            // Never let a compatibility-fix copy failure block an actual game launch.
            Logger.error(TAG, "Failed to install nanovg natives compatibility fix", t)
            target.delete()
        }
    }
}
