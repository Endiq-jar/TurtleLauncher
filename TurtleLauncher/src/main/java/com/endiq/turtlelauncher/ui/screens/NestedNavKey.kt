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

package com.endiq.turtlelauncher.ui.screens

import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.game.download.assets.platform.Platform
import com.endiq.turtlelauncher.game.download.assets.platform.PlatformClasses
import com.endiq.turtlelauncher.game.version.installed.Version
import com.endiq.turtlelauncher.ui.androidText
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Screens that nest a NavDisplay
 */
sealed interface NestedNavKey {
    /** Splash screen */
    @Serializable class Splash : BackStackNavKey<TitledNavKey>() {
        init {
            backStack.addIfEmpty(NormalNavKey.UnpackDeps)
        }
    }
    /** Main screen */
    @Serializable class Main : BackStackNavKey<TitledNavKey>() {
        init {
            backStack.addIfEmpty(NormalNavKey.LauncherMain)
        }
    }
    /** Settings screen */
    @Serializable class Settings : BackStackNavKey<TitledNavKey>(androidText(R.string.generic_setting)) {
        init {
            backStack.addIfEmpty(NormalNavKey.Settings.Renderer)
        }
    }
    /** Version detailed settings screen */
    @Serializable
    class VersionSettings(@Contextual val version: Version) : BackStackNavKey<TitledNavKey>(
        androidText(R.string.page_title_version_manage)
    ) {
        init {
            backStack.addIfEmpty(NormalNavKey.Versions.OverView)
        }
    }
    /** Export modpack screen */
    @Serializable
    class VersionExport(@Contextual val version: Version) : BackStackNavKey<TitledNavKey>(
        androidText(R.string.versions_export)
    ) {
        init {
            backStack.addIfEmpty(NormalNavKey.VersionExports.SelectType)
        }
    }
    /** Download screen */
    @Serializable class Download : BackStackNavKey<TitledNavKey>(
        androidText(R.string.generic_download)
    )

    //Nested sub-screens of the download screen
    /** Download game screen */
    @Serializable class DownloadGame : BackStackNavKey<TitledNavKey>(
        androidText(R.string.download_category_game)
    ) {
        init {
            backStack.addIfEmpty(NormalNavKey.DownloadGame.SelectGameVersion)
        }
    }
    /** Download modpack screen */
    @Serializable class DownloadModPack : BackStackNavKey<TitledNavKey>(
        androidText(R.string.download_category_modpack)
    ) {
        init {
            backStack.addIfEmpty(NormalNavKey.SearchModPack)
        }
    }
    /** Download mods screen */
    @Serializable class DownloadMod : BackStackNavKey<TitledNavKey>(
        androidText(R.string.download_category_mod)
    ) {
        init {
            backStack.addIfEmpty(NormalNavKey.SearchMod)
        }
    }
    /** Download resource packs screen */
    @Serializable class DownloadResourcePack : BackStackNavKey<TitledNavKey>(
        androidText(R.string.download_category_resource_pack)
    ) {
        init {
            backStack.addIfEmpty(NormalNavKey.SearchResourcePack)
        }
    }
    /** Download saves screen */
    @Serializable class DownloadSaves : BackStackNavKey<TitledNavKey>(
        androidText(R.string.download_category_saves)
    ) {
        init {
            backStack.addIfEmpty(NormalNavKey.SearchSaves)
        }
    }
    /** Download shaders screen */
    @Serializable class DownloadShaders : BackStackNavKey<TitledNavKey>(
        androidText(R.string.download_category_shaders)
    ) {
        init {
            backStack.addIfEmpty(NormalNavKey.SearchShaders)
        }
    }
    /** Download favorites screen */
    @Serializable class DownloadFavorites : BackStackNavKey<TitledNavKey>(
        androidText(R.string.download_category_favorites)
    ) {
        init {
            backStack.addIfEmpty(NormalNavKey.Favorites)
        }
    }
    /** Screen for viewing Addons asset info */
    @Serializable
    class AssetInfo(
        val platform: Platform,
        val projectId: String,
        val classes: PlatformClasses
    ) : BackStackNavKey<TitledNavKey>(
        androidText(R.string.generic_download)
    ) {
        init {
            backStack.addIfEmpty(
                NormalNavKey.DownloadAssets(platform, projectId, classes)
            )
        }
    }
}