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

package com.endiq.turtlelauncher.game.version.mod

import com.endiq.turtlelauncher.utils.file.MurmurHash2Incremental
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Whitespace bytes CurseForge fingerprinting must strip: \\t, \\n, \\r and space */
val CURSEFORGE_FINGERPRINT_SKIP_BYTES = listOf(0x9, 0xa, 0xd, 0x20)

/** Whitespace-stripping lookup table; avoids boxing and linear scans in per-byte checks */
private val CURSEFORGE_FINGERPRINT_SKIP_TABLE = BooleanArray(256).also { table ->
    CURSEFORGE_FINGERPRINT_SKIP_BYTES.forEach { table[it] = true }
}

/**
 * Fingerprints of a local mod file
 * @param sha1 the file's SHA-1 (Modrinth)
 * @param murmur2 the file's MurmurHash2 after whitespace stripping per CurseForge rules (CurseForge)
 */
class ModFingerprints(
    val sha1: String,
    val murmur2: Long
)

/**
 * Process-level in-memory cache of fingerprints, keyed by absolute file path
 *
 * Validated against file size and mtime; changed files are treated as stale, avoiding repeat disk reads
 */
private object ModFingerprintMemoryCache {
    private class Entry(
        val fingerprints: ModFingerprints,
        val lastModified: Long,
        val length: Long
    )

    private val cache = ConcurrentHashMap<String, Entry>()

    fun get(file: File): ModFingerprints? {
        return cache[file.absolutePath]
            ?.takeIf { it.lastModified == file.lastModified() && it.length == file.length() }
            ?.fingerprints
    }

    fun put(file: File, fingerprints: ModFingerprints) {
        cache[file.absolutePath] = Entry(fingerprints, file.lastModified(), file.length())
    }
}

/**
 * Computes a mod file's platform fingerprints in one pass; identical files reuse the in-memory cache directly
 *
 * The CurseForge fingerprint algorithm needs the whitespace-stripped total length upfront,
 * so the first scan pass computes SHA-1 plus the filtered length, and the second pass computes only MurmurHash2
 */
suspend fun computeModFingerprints(file: File): ModFingerprints = withContext(Dispatchers.IO) {
    ModFingerprintMemoryCache.get(file)?.let { return@withContext it }

    val context = currentCoroutineContext()
    val digest = MessageDigest.getInstance("SHA-1")
    var filteredLength = 0

    Files.newInputStream(file.toPath()).use { stream ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } != -1) {
            context.ensureActive()
            digest.update(buffer, 0, bytesRead)
            for (i in 0 until bytesRead) {
                if (!CURSEFORGE_FINGERPRINT_SKIP_TABLE[buffer[i].toInt() and 0xFF]) filteredLength++
            }
        }
    }

    val sha1 = digest.digest().joinToString("") { "%02x".format(it) }
    val murmur2 = MurmurHash2Incremental.computeHash(
        file = file,
        byteToSkip = CURSEFORGE_FINGERPRINT_SKIP_BYTES,
        filteredLength = filteredLength
    )

    ModFingerprints(sha1 = sha1, murmur2 = murmur2).also {
        ModFingerprintMemoryCache.put(file, it)
    }
}
