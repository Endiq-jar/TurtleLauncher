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

package com.endiq.turtlelauncher.filemanager.logic.compress

import java.nio.file.Path

/**
 * Compression format
 */
enum class CompressFormat(val extension: String, val mimeType: String) {
    ZIP("zip", "application/zip"),
    SEVEN_Z("7z", "application/x-7z-compressed"),
    TAR("tar", "application/x-tar");

    /** Canonical suffix of the output file name */
    val suffix: String get() = ".$extension"

    /** Default compression method of the format */
    val defaultMethod: CompressMethod
        get() = when (this) {
            ZIP -> CompressMethod.DEFLATE
            SEVEN_Z -> CompressMethod.LZMA2
            TAR -> CompressMethod.TAR_POSIX
        }
}

/**
 * Compression method
 */
enum class CompressMethod(val displayName: String) {
    /** Zip: stored without compression; 7Z: COPY without compression */
    STORE("Store"),
    /** Zip default */
    DEFLATE("Deflate"),

    /** 7Z default */
    LZMA2("LZMA2"),
    BZIP2("BZIP2"),

    /** TAR long file name handling: GNU format */
    TAR_GNU("GNU"),
    /** TAR long file name handling: POSIX (PAX) format */
    TAR_POSIX("POSIX");

    companion object {
        /** Methods supported by ZIP */
        val zipMethods = listOf(STORE, DEFLATE)
        /** Methods supported by 7Z */
        val sevenZMethods = listOf(LZMA2, BZIP2)
        /** Methods supported by TAR (long file name handling formats) */
        val tarMethods = listOf(TAR_GNU, TAR_POSIX)
    }
}

/**
 * Compression parameters.
 * @param format compression format
 * @param method compression method; null uses the format default
 * @param level compression level (1-9); null uses the format default
 * @param password password; not supported by TAR
 */
data class CompressOptions(
    val format: CompressFormat,
    val method: CompressMethod? = null,
    val level: Int? = null,
    val password: String? = null
)

/**
 * Compression result summary.
 */
data class CompressSummary(
    val outputPath: Path,
    val entryCount: Int
)
