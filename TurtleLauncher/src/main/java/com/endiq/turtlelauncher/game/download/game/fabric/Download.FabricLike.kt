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

package com.endiq.turtlelauncher.game.download.game.fabric

import com.endiq.turtlelauncher.coroutine.Task
import com.endiq.turtlelauncher.game.addons.mirror.mapBMCLMirrorUrls
import com.endiq.turtlelauncher.game.addons.modloader.fabriclike.FabricLikeVersion
import com.endiq.turtlelauncher.utils.file.ensureParentDirectory
import com.endiq.turtlelauncher.utils.network.fetchStringFromUrls
import kotlinx.coroutines.Dispatchers
import java.io.File

const val FABRIC_LIKE_DOWNLOAD_ID = "Download.FabricLike"

fun getFabricLikeDownloadTask(
    fabricLikeVersion: FabricLikeVersion,
    tempVersionJson: File
): Task {
    return Task.runTask(
        id = FABRIC_LIKE_DOWNLOAD_ID,
        dispatcher = Dispatchers.IO,
        task = {
            //下载版本 Json
            val loaderJson = fetchStringFromUrls(fabricLikeVersion.loaderJsonUrl.mapBMCLMirrorUrls())
            tempVersionJson
                .ensureParentDirectory()
                .writeText(loaderJson)
        }
    )
}