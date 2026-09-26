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
import com.endiq.turtlelauncher.ui.AndroidStringText
import com.endiq.turtlelauncher.ui.androidText
import com.endiq.turtlelauncher.ui.screens.content.FirstLoginMenu
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Plain screens
 */
sealed interface NormalNavKey : TitledNavKey {
    @Contextual override val title: AndroidStringText?
        get() = null

    /** Dependency unpacking screen (launch screen) */
    @Serializable data object UnpackDeps: NormalNavKey
    /** Launcher home screen */
    @Serializable data object LauncherMain : NormalNavKey
    /** Account management screen */
    @Serializable data class AccountManager(
        val loginMenu: FirstLoginMenu = FirstLoginMenu.NONE
    ) : NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.page_title_account_list)
    }
    /** Web screen */
    @Serializable data class WebScreen(val url: String) : NormalNavKey
    /** Version management screen */
    @Serializable data object VersionsManager : NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.page_title_version_list)
    }
    /** File selector screen */
    @Serializable data class FileSelector(
        val startPath: String,
        val selectFile: Boolean,
        val saveKey: TitledNavKey,
        val onSelected: (path: String) -> Unit
    ) : NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.page_title_select_files)
    }
    /** Multiplayer screen */
    @Serializable data object Multiplayer: NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.terracotta_terracotta)
    }

    /** Log viewer screen */
    @Serializable data class LogView(
        val logPath: String
    ) : NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.versions_overview_log)
    }

    /** Settings nested sub-screens */
    sealed interface Settings : NormalNavKey {
        /** Renderer settings screen */
        @Serializable data object Renderer : Settings {
            @Contextual override val title: AndroidStringText = androidText(R.string.settings_tab_renderer)
        }
        /** Game settings screen */
        @Serializable data object Game : Settings {
            @Contextual override val title: AndroidStringText = androidText(R.string.settings_tab_game)
        }
        /** Control settings screen */
        @Serializable data object Control : Settings {
            @Contextual override val title: AndroidStringText = androidText(R.string.settings_tab_control)
        }
        /** Gamepad settings screen */
        @Serializable data object Gamepad : Settings {
            @Contextual override val title: AndroidStringText = androidText(R.string.settings_tab_gamepad)
        }
        /** Launcher settings screen */
        @Serializable data object Launcher : Settings {
            @Contextual override val title: AndroidStringText = androidText(R.string.settings_tab_launcher)
        }
        /** Extras screen: screen recorder, Emotes and Shizuku */
        @Serializable data object Extras : Settings {
            @Contextual override val title: AndroidStringText = androidText(R.string.settings_tab_extras)
        }
        /** Java management screen */
        @Serializable data object JavaManager : Settings {
            @Contextual override val title: AndroidStringText = androidText(R.string.settings_tab_java_manage)
        }
        /** Control management screen */
        @Serializable data object ControlManager : Settings {
            @Contextual override val title: AndroidStringText = androidText(R.string.settings_tab_control_manage)
        }
        /** About screen */
        @Serializable data object AboutInfo : Settings {
            @Contextual override val title: AndroidStringText = androidText(R.string.settings_tab_info_about)
        }
    }

    /** Version detail settings nested sub-screens */
    sealed interface Versions : NormalNavKey {
        /** Version overview screen */
        @Serializable data object OverView : Versions {
            @Contextual override val title: AndroidStringText = androidText(R.string.versions_settings_overview)
        }
        /** Version configuration screen */
        @Serializable data object Config : Versions {
            @Contextual override val title: AndroidStringText = androidText(R.string.versions_settings_config)
        }
        /** Update the version's mod loader */
        @Serializable data object UpdateLoader : Versions {
            @Contextual override var title: AndroidStringText = androidText(R.string.versions_update_loader)
        }
        /** Mods management screen */
        @Serializable data object ModsManager : Versions {
            @Contextual override var title: AndroidStringText = androidText(R.string.mods_manage)
        }
        /** Saves management screen */
        @Serializable data object SavesManager : Versions {
            @Contextual override var title: AndroidStringText = androidText(R.string.saves_manage)
        }
        /** Resource-pack management screen */
        @Serializable data object ResourcePackManager : Versions {
            @Contextual override var title: AndroidStringText = androidText(R.string.resource_pack_manage)
        }
        /** Shader-pack management screen */
        @Serializable data object ShadersManager : Versions {
            @Contextual override var title: AndroidStringText = androidText(R.string.shader_pack_manage)
        }
        /** Screenshot management screen */
        @Serializable data object ScreenshotsManager : Versions {
            @Contextual override var title: AndroidStringText = androidText(R.string.screenshots_manage)
        }
        /** Server list screen */
        @Serializable data object ServerList : Versions {
            @Contextual override val title: AndroidStringText = androidText(R.string.servers_list)
        }
    }

    /** Modpack export screen */
    sealed interface VersionExports : NormalNavKey {
        /** Pick the export format */
        @Serializable data object SelectType : VersionExports
        /** Edit the modpack export configuration */
        @Serializable data object EditInfo : VersionExports
        /** Pick the files to export */
        @Serializable data object SelectFiles : VersionExports
    }

    /** Game download nested sub-screens */
    sealed interface DownloadGame : NormalNavKey {
        /** Pick game version screen */
        @Serializable data object SelectGameVersion : Versions
        /** Pick addons screen */
        @Serializable data class Addons(val gameVersion: String) : Versions
    }

    /** Search modpacks screen */
    @Serializable data object SearchModPack : NormalNavKey
    /** Search mods screen */
    @Serializable data object SearchMod : NormalNavKey
    /** Search resource packs screen */
    @Serializable data object SearchResourcePack : NormalNavKey
    /** Search saves screen */
    @Serializable data object SearchSaves : NormalNavKey
    /** Search shader packs screen */
    @Serializable data object SearchShaders : NormalNavKey
    /** Search by ID screen */
    @Serializable data object SearchId : NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.download_category_by_id)
    }
    /** Favorites screen */
    @Serializable data object Favorites : NormalNavKey {
        @Contextual override val title: AndroidStringText = androidText(R.string.download_category_favorites)
    }

    /** Resource download screen */
    @Serializable data class DownloadAssets(
        val platform: Platform,
        val projectId: String,
        val classes: PlatformClasses,
        val iconUrl: String? = null
    ) : NormalNavKey

    /** Agreement display screen */
    @Serializable data class License(
        val raw: Int
    ): NormalNavKey
}