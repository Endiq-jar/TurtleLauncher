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

package com.endiq.turtlelauncher.game.addons.modloader.cleanroom

import com.endiq.turtlelauncher.path.GLOBAL_CLIENT
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.network.safeBodyAsJson
import com.endiq.turtlelauncher.utils.network.withRetry
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

object CleanroomVersions {
    private const val TAG = "CleanroomVersions"

    private const val LOADER_LIST_URL = "https://hmcl-dev.github.io/metadata/cleanroom/index.json"

    /**
     * Fetches the Cleanroom version list from the HMCL source
     */
    suspend fun fetchLoaderList(mcVersion: String): List<CleanroomVersion>? {
        //Cleanroom only supports 1.12.2
        if (mcVersion != "1.12.2") {
            Logger.warning(TAG, "Cleanroom only supports 1.12.2, current input: $mcVersion")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                withRetry(TAG, maxRetries = 2) {
                    val versions: List<ReleaseResult> = GLOBAL_CLIENT.get(LOADER_LIST_URL).safeBodyAsJson()
                    versions.map { ver ->
                        CleanroomVersion(
                            version = ver.name,
                            createdAt = Instant.parse(ver.createdAt)
                        )
                    }
                }
            } catch (_: CancellationException) {
                Logger.debug(TAG, "Client cancelled.")
                null
            } catch (e: Exception) {
                Logger.debug(TAG, "Failed to fetch loader list!", e)
                throw e
            }
        }
    }
}