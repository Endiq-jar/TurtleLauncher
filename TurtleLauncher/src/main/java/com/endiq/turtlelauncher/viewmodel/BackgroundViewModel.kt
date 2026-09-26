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

package com.endiq.turtlelauncher.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.endiq.turtlelauncher.context.copyLocalFile
import com.endiq.turtlelauncher.path.PathManager
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.image.isImageFile
import com.endiq.turtlelauncher.utils.video.isVideoFile
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File

/**
 * Launcher background management
 */
class BackgroundViewModel: ViewModel() {
    // HazeState uses the Auto position strategy by default
    val hazeState = HazeState()

    val backgroundFile: File = PathManager.FILE_LAUNCHER_BACKGROUND

    /**
     * Whether the background file is valid (exists and is a valid video or image)
     */
    var isValid by mutableStateOf(false)
        private set

    /**
     * The background file is a video
     */
    var isVideo by mutableStateOf(false)
        private set

    /**
     * The background file is an image
     */
    var isImage by mutableStateOf(false)
        private set

    var refreshTrigger by mutableStateOf(false)
        private set

    private suspend fun updateState() {
        val (isImage0, isVideo0) = withContext(Dispatchers.IO) {
            val isImage = backgroundFile.isImageFile()
            //Skip the video check for image files
            val isVideo = if (!isImage) backgroundFile.isVideoFile() else false
            isImage to isVideo
        }
        //Update the state
        withContext(Dispatchers.Main) {
            isImage = isImage0
            isVideo = isVideo0
            isValid = backgroundFile.exists() && (isImage0 || isVideo0)
            refreshTrigger = !refreshTrigger
        }
    }

    init {
        viewModelScope.launch(Dispatchers.Main) {
            updateState()
        }
    }

    suspend fun delete() {
        withContext(Dispatchers.IO) {
            FileUtils.deleteQuietly(backgroundFile)
            updateState()
        }
    }

    suspend fun import(context: Context, result: Uri) {
        withContext(Dispatchers.IO) {
            FileUtils.deleteQuietly(backgroundFile)
            context.copyLocalFile(result, backgroundFile)
            if (!backgroundFile.isImageFile() && !backgroundFile.isVideoFile()) {
                //Not a media file
                FileUtils.deleteQuietly(backgroundFile)
                error("The selected file is not an image or a video!")
            }
            updateState()
        }
    }
}

/**
 * Locally available background image management ViewModel
 * Provided by MainActivity's theme
 */
val LocalBackgroundViewModel = compositionLocalOf<BackgroundViewModel?> {
    error("No BackgroundViewModel provided")
}

@Composable
fun <E> influencedByBackground(
    value: E,
    influenced: E,
    enabled: Boolean = true
): E {
    return if (enabled) {
        if (LocalBackgroundViewModel.current?.isValid == true) {
            influenced
        } else {
            value
        }
    } else {
        value
    }
}

@Composable
fun backgroundVisible(): Boolean {
    return LocalBackgroundViewModel.current?.isValid == true && AllSettings.launcherBackgroundOpacity.state < 100
}