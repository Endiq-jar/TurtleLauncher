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

import java.io.File
import java.io.IOException

/**
 * Task spec for one file download. urls is a priority-ordered candidate source list,
 * and the engine automatically switches down the list on failure or throttling.
 */
class DownloadRequest(
    val urls: List<String>,
    val targetFile: File,
    val sha1: String? = null,
    /** Known file size; -1 when unknown. Only used for pre-allocation and progress; the actual response wins */
    val expectedSize: Long = -1L,
    /** Caller-provided context object, carried back unchanged in the progress and success callbacks */
    val tag: Any? = null
) {
    init {
        require(urls.isNotEmpty()) { "Download requires at least one url" }
    }

    override fun toString(): String = targetFile.name
}

/** Finds the first HTTP status exception along the cause chain, letting callers recognize 404 and similar semantics */
fun Throwable.findHttpCode(): Int? =
    generateSequence(this) { it.cause }
        .filterIsInstance<HttpResultException>()
        .firstOrNull()
        ?.code

/** Thrown when every source still fails; the message carries each source's failure reason */
class AllSourcesFailedException(
    summary: String,
    cause: Throwable?
) : IOException(summary, cause)

class HttpResultException(
    val code: Int,
    message: String
) : IOException(message)
