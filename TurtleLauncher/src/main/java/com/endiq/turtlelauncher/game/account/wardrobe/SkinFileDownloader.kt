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

package com.endiq.turtlelauncher.game.account.wardrobe

import com.endiq.turtlelauncher.utils.logging.Logger
import java.io.File

private const val TAG = "SkinFileDownloader"

class SkinFileDownloader: WardrobeDownloader() {
    /**
     * Tries to download the Yggdrasil skin
     */
    @Throws(Exception::class)
    suspend fun download(
        url: String,
        skinFile: File,
        uuid: String,
        changeSkinModel: (SkinModelType) -> Unit
    ) {
        val valueObject = yggdrasil(url, uuid)
        val skinObject = valueObject.get("textures").asJsonObject.get("SKIN").asJsonObject
        val skinUrl = skinObject.get("url").asString

        val skinModelType = runCatching {
            skinObject.takeIf {
                it.has("metadata")
            }?.get("metadata")?.let {
                //The metadata field only exists for slim-arm models; otherwise it's the classic arm
                //Wiki: https://zh.minecraft.wiki/w/Mojang_API#%E8%8E%B7%E5%8F%96%E7%8E%A9%E5%AE%B6%E7%9A%84%E7%9A%AE%E8%82%A4%E5%92%8C%E6%8A%AB%E9%A3%8E
                SkinModelType.ALEX
            } ?: SkinModelType.STEVE
        }.getOrElse {
            Logger.warning(TAG, "Can not get skin model type.")
            SkinModelType.NONE
        }

        download(skinUrl, skinFile)
        changeSkinModel(skinModelType)
    }
}