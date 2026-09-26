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

package com.endiq.turtlelauncher.game.version.download

import com.endiq.turtlelauncher.game.download.engine.BatchDownloader
import com.endiq.turtlelauncher.game.download.engine.DownloadRequest
import com.endiq.turtlelauncher.utils.file.check7z
import com.endiq.turtlelauncher.utils.file.checkZip
import com.endiq.turtlelauncher.utils.file.compareSHA1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File

/**
 * Complete description of one file to download
 * Candidate sources, target location and validation
 * Bulk execution goes to [BatchDownloader]; a single file can also be driven directly via [com.endiq.turtlelauncher.game.download.engine.DownloadEngine]
 */
class DownloadTask(
    val urls: List<String>,
    private val verifyIntegrity: Boolean,
    val targetFile: File,
    val sha1: String? = null,
    /** Known size, for progress accounting and pre-allocation; -1 when unknown */
    val size: Long = -1L,
    /**
     * Whether the file is downloadable at all; if not, it may only be satisfied by the target already existing,
     * since forcing the download fails with 404
     */
    val isDownloadable: Boolean = true
) {
    init {
        require(urls.isNotEmpty()) { "DownloadTask requires at least one url" }
    }

    var fileDownloadedTask: (suspend () -> Unit)? = null

    internal fun toRequest(): DownloadRequest = DownloadRequest(
        urls = urls,
        targetFile = targetFile,
        sha1 = sha1,
        expectedSize = size,
        tag = this
    )

    internal suspend fun runFileDownloadedTask() {
        withContext(Dispatchers.IO) {
            fileDownloadedTask?.invoke()
        }
    }

    /** True when the target already exists and validates */
    fun existingFileValid(): Boolean {
        val file = targetFile
        if (!file.exists()) return false
        if (!verifyIntegrity) return true

        if (sha1.isNullOrBlank()) {
            //Rule out targets that can't be downloaded, e.g. Forge's client
            if (!isDownloadable) return true
            return archiveOrPlainValid(file)
        }

        if (compareSHA1(file, sha1)) return true
        FileUtils.deleteQuietly(file)
        return false
    }

    private fun archiveOrPlainValid(file: File): Boolean = when (file.extension.lowercase()) {
        "zip", "jar" -> checkZip(file)
        "7z" -> check7z(file)
        else -> true
    }
}
