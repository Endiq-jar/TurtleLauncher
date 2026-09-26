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

package com.endiq.turtlelauncher.game.download.modpack.platform

import android.content.Context
import com.endiq.turtlelauncher.coroutine.TaskFlowExecutor
import com.endiq.turtlelauncher.coroutine.TaskLogOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * Modpack task-flow builder
 * @param platform modpack format (platform)
 */
abstract class AbstractPack(
    val platform: PackPlatform
) {
    /**
     * Returns the final installed client version name (edited by the user)
     * After the install ends, it tries to switch directly to this version
     */
    abstract fun getFinalClientName(): String

    /**
     * Builds the install task phase, where dependency files are downloaded and modpack-internal files unpacked
     * @param scope the lifecycle-managed scope the install task runs in
     * @param versionFolder temp game version folder, for installing game files
     * @param waitForVersionName waits for the user to enter the version name
     * @param addPhases adds the next install phase
     * @param onClearTemp install finished, start cleaning caches
     * @param logOutputHolder holder of the install JVM log output
     */
    abstract fun buildTaskPhases(
        context: Context,
        scope: CoroutineScope,
        versionFolder: File,
        waitForVersionName: suspend (name: String) -> String,
        addPhases: (List<TaskFlowExecutor.TaskPhase>) -> Unit,
        onClearTemp: suspend () -> Unit,
        logOutputHolder: MutableStateFlow<TaskLogOutput?> = MutableStateFlow(null)
    ): List<TaskFlowExecutor.TaskPhase>
}