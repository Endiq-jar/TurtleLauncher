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

package com.endiq.turtlelauncher.game.control

import android.content.Context
import com.endiq.layer_controller.layout.ControlLayout
import com.endiq.layer_controller.layout.loadLayoutFromFile
import com.endiq.layer_controller.layout.loadLayoutFromFileUncheck
import com.endiq.layer_controller.layout.loadLayoutFromString
import com.endiq.layer_controller.observable.ObservableControlLayout
import com.endiq.layer_controller.utils.newRandomFileName
import com.endiq.layer_controller.utils.saveToFile
import com.endiq.turtlelauncher.context.copyAssetFile
import com.endiq.turtlelauncher.path.PathManager
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.file.readString
import com.endiq.turtlelauncher.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.InputStream

private const val TAG = "ControlManager"

/**
 * Control layout manager
 */
object ControlManager {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _dataList = MutableStateFlow<List<ControlData>>(emptyList())
    val dataList = _dataList.asStateFlow()

    private var currentJob: Job? = null

    private val _selectedLayout = MutableStateFlow<ControlData?>(null)
    /** Currently selected control layout */
    val selectedLayout = _selectedLayout.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    /** Whether control layouts are being refreshed */
    val isRefreshing = _isRefreshing.asStateFlow()

    /**
     * Gets a new layout file with a random name
     */
    private fun getNewRandomFile() = File(PathManager.DIR_CONTROL_LAYOUTS, "${newRandomFileName()}.json")

    /**
     * Checks whether no control layout exists, and unpacks the default one
     * @param context a context for accessing assets
     */
    fun checkDefaultAndRefresh(context: Context) {
        scope.launch(Dispatchers.IO) {
            val files = (PathManager.DIR_CONTROL_LAYOUTS.listFiles() ?: emptyArray())
                .filter { file ->
                    file.isFile && file.exists() && file.extension.equals("json", true)
                }
            if (files.isEmpty()) {
                unpackDefaultControl(context)
            }
            refresh()
        }
    }

    fun refresh() {
        currentJob?.cancel()
        currentJob = scope.launch(Dispatchers.IO) {
            _isRefreshing.update { true }

            _dataList.update { emptyList() }
            PathManager.DIR_CONTROL_LAYOUTS.listFiles()?.mapNotNull { file ->
                if (!(file.isFile && file.exists() && file.extension.equals("json", true))) return@mapNotNull null

                var isSupport = true
                val layout: ControlLayout = try {
                    loadLayoutFromFile(file)
                } catch (_: IllegalArgumentException) {
                    isSupport = false
                    runCatching {
                        loadLayoutFromFileUncheck(file)
                    }.onFailure { e ->
                        Logger.warning(TAG, "Failed to load control layout! file = $file", e)
                    }.getOrNull() ?: return@mapNotNull null
                } catch (e: Exception) {
                    Logger.warning(TAG, "Failed to load control layout! file = $file", e)
                    return@mapNotNull null
                }

                ControlData(
                    file = file,
                    controlLayout = ObservableControlLayout(layout),
                    isSupport = isSupport
                )
            }?.let { list ->
                _dataList.update {
                    list.sortedBy {
                        if (it.isSupport) it.controlLayout.info.name.default
                        else it.file.name
                    }
                }
            }
            checkSettings()

            _isRefreshing.update { false }
        }
    }

    /**
     * Checks and updates the settings
     */
    private fun checkSettings() {
        val setting = AllSettings.controlLayout.getValue()

        val layout = _dataList.value.find { it.file.name == setting && it.isSupport }
            ?: dataList.value.firstOrNull { it.isSupport }
                ?.also { AllSettings.controlLayout.save(it.file.name) }

        if (layout == null) {
            AllSettings.controlLayout.reset()
        }

        _selectedLayout.update { layout }
    }

    /**
     * Unpacks the default control layout
     */
    private suspend fun unpackDefaultControl(
        context: Context
    ) = withContext(Dispatchers.IO) {
        //Bundled control layouts: the launcher's own default plus the presets
        //imported from Turtle-Launcher (entries: asset file name; display names
        //come from each layout's `info.name` field)
        val presets = listOf(
            "default_layout.json",
            "turtle_control_presets/default.json",
            "turtle_control_presets/survival.json"
        )
        for (preset in presets) {
            try {
                val file = getNewRandomFile()
                context.copyAssetFile(fileName = preset, output = file, overwrite = false)
            } catch (e: Exception) {
                Logger.warning(TAG, "Failed to unpack bundled control layout: $preset", e)
            }
        }
    }

    /**
     * Selects a control layout
     */
    fun selectControl(data: ControlData) {
        if (!data.file.exists() || !data.isSupport) return
        AllSettings.controlLayout.save(data.file.name)
        _selectedLayout.update { data }
    }

    /**
     * Deletes a control layout in a coroutine
     */
    fun deleteControl(data: ControlData) {
        scope.launch(Dispatchers.IO) {
            if (!data.file.exists()) return@launch
            FileUtils.deleteQuietly(data.file)
            refresh()
        }
    }

    /**
     * Saves control layout data in a coroutine
     */
    fun saveControl(
        data: ControlData,
        submitError: (Exception) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            if (!data.file.exists()) {
                refresh()
                return@launch
            }
            val layout = data.controlLayout.pack()
            try {
                layout.saveToFile(data.file)
            } catch (e: Exception) {
                submitError(e)
//                FileUtils.deleteQuietly(data.file)
            }
            refresh()
        }
    }

    /**
     * Tries to import a control layout
     */
    suspend fun importControl(
        inputStream: InputStream,
        onSerializationError: (Exception) -> Unit,
        catchedError: (Exception) -> Unit,
        onFinished: () -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val file = getNewRandomFile()
        try {
            inputStream.use { stream ->
                val jsonString = stream.readString()
                val layout = loadLayoutFromString(jsonString)
                layout.saveToFile(file)
            }
            onFinished()
        } catch (e: SerializationException) {
            FileUtils.deleteQuietly(file)
            onSerializationError(e)
        } catch (e: Exception) {
            FileUtils.deleteQuietly(file)
            catchedError(e)
        }
    }
}