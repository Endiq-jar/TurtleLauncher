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

package com.endiq.turtlelauncher.game.addons.modloader.fabriclike

import com.endiq.turtlelauncher.game.addons.mirror.MirrorSource
import com.endiq.turtlelauncher.game.addons.mirror.SourceType
import com.endiq.turtlelauncher.game.addons.mirror.orderedByGameSourcePreference
import com.endiq.turtlelauncher.game.addons.mirror.runMirrorable
import com.endiq.turtlelauncher.game.addons.modloader.fabriclike.models.FabricLikeLoader
import com.endiq.turtlelauncher.game.addons.modloader.fabriclike.models.FabricLikeVersionsJson
import com.endiq.turtlelauncher.path.GLOBAL_CLIENT
import com.endiq.turtlelauncher.utils.isChinaMainland
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.network.safeBodyAsJson
import com.endiq.turtlelauncher.utils.network.withRetry
import io.ktor.client.request.get
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FabricLikeVersions"

abstract class FabricLikeVersions(
    val officialUrl: String,
    val mirrorUrl: String? = null
) {
    private var cacheVersions: FabricLikeVersionsJson? = null

    /**
     * Generic loader list fetching
     * [Reference PCL2](https://github.com/Meloong-Git/PCL/blob/28ef67e/Plain%20Craft%20Launcher%202/Modules/Minecraft/ModDownload.vb#L1035-L1054)
     */
    protected suspend fun fetchLoaderList(
        force: Boolean,
        tag: String,
        mcVersion: String
    ): List<FabricLikeLoader>? = withContext(Dispatchers.Default) {
        mirrorUrl?.takeIf { isChinaMainland() }?.let {
            runMirrorable(
                listOf(
                    fetchListFromMirrorSource(SourceType.OFFICIAL, force, tag, mcVersion, officialUrl),
                    fetchListFromMirrorSource(SourceType.BMCLAPI, force, tag, mcVersion, it)
                ).orderedByGameSourcePreference()
            )
        } ?: run {
            //Mirror sources unsupported; official source only
            fetchListWithSource(force, tag, mcVersion, officialUrl)
        }
    }

    private fun fetchListFromMirrorSource(
        type: SourceType,
        force: Boolean,
        tag: String,
        mcVersion: String,
        sourceUrl: String
    ): MirrorSource<List<FabricLikeLoader>?> = MirrorSource(type) {
        fetchListWithSource(force, tag, mcVersion, sourceUrl)
    }

    /**
     * Fetches the version list from the given source
     */
    private suspend fun fetchListWithSource(
        force: Boolean,
        tag: String,
        mcVersion: String,
        sourceUrl: String
    ): List<FabricLikeLoader>? = withContext(Dispatchers.IO) {
        val url = "$sourceUrl/versions"
        try {
            val versions: FabricLikeVersionsJson = run {
                if (!force && cacheVersions != null) return@run cacheVersions!!
                withContext(Dispatchers.IO) {
                    withRetry(tag, maxRetries = 2) { GLOBAL_CLIENT.get(url).safeBodyAsJson() }
                }
            }.also {
                cacheVersions = it
            }

            if (!versions.game.any { it.version == mcVersion }) {
                Logger.warning(TAG, "The version $mcVersion does not have a corresponding loader.")
                return@withContext null
            }

            versions.loader
        } catch (_: CancellationException) {
            Logger.debug(TAG, "Client cancelled.")
            null
        } catch (e: Exception) {
            throw RuntimeException("Failed to fetch fabriclike loader list! url: $url", e)
        }
    }
}