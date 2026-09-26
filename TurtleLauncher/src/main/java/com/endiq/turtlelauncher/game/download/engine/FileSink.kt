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

package com.endiq.turtlelauncher.game.download.engine

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * [Modified from HMCL FileDownloadTask](https://github.com/HMCL-dev/HMCL/blob/59bcc7fe/HMCLCore/src/main/java/org/jackhuang/hmcl/task/FileDownloadTask.java)
 *
 * Download sink context: writes into a temp file next to the target first, and validates the SHA-1 on successful finalization
 * then atomically replaces the target file; a failed discard deletes the temp file.
 * Write failures are marked by [broken], telling callers to abandon resume and retry from scratch.
 */
class FileSink(
    targetFile: File,
    private val expectedChecksum: String?
) : Closeable {
    private val target: Path = targetFile.toPath()
    private val temp: Path
    private val channel: FileChannel
    private val digest: MessageDigest? = expectedChecksum?.let { MessageDigest.getInstance("SHA-1") }

    /** Whether the latest write failed; when true the existing content is untrusted and retries must not resume */
    var broken = false
        private set

    init {
        val parent = target.toAbsolutePath().parent
        Files.createDirectories(parent)
        temp = Files.createTempFile(parent, null, null)
        channel = FileChannel.open(
            temp,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.CREATE
        )
    }

    fun write(buffer: ByteArray, offset: Int, length: Int) {
        try {
            digest?.update(buffer, offset, length)
            val wrapped = ByteBuffer.wrap(buffer, offset, length)
            while (wrapped.hasRemaining()) {
                channel.write(wrapped)
            }
        } catch (e: IOException) {
            broken = true
            throw e
        }
    }

    /** Finalization after a successful download: validates the SHA-1 and atomically replaces the target with the temp file; throwing counts as a retriable failure */
    fun finish() {
        var moved = false
        try {
            channel.close()

            if (expectedChecksum != null) {
                val actual = digest!!.digest().toHexString()
                if (!expectedChecksum.equals(actual, ignoreCase = true)) {
                    throw ChecksumMismatchException("SHA-1", expectedChecksum, actual)
                }
            }

            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            moved = true
        } finally {
            if (!moved) deleteTemp()
        }
    }

    override fun close() {
        runCatching { channel.close() }
        deleteTemp()
    }

    private fun deleteTemp() {
        runCatching { Files.deleteIfExists(temp) }
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { "%02x".format(it) }
}
