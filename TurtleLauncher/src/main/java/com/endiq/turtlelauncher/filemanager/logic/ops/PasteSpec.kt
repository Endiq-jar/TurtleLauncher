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

package com.endiq.turtlelauncher.filemanager.logic.ops

import java.nio.file.Path

/** Top-level conflict handling option */
enum class ConflictResolution {
    /** Skips this entry */
    SKIP,
    /** Files are fully overwritten; folders have their contents merged */
    OVERWRITE,
    /** Appends a suffix to the entry being written */
    KEEP_BOTH
}

/** Paste mode. */
enum class PasteMode {
    /** Copy */
    COPY,
    /** Move */
    MOVE
}

/** Paste request build result. */
sealed interface PasteRequest {
    /** No decision needed; can run directly */
    data class Ready(val sources: List<Path>, val targetDir: Path, val mode: PasteMode) : PasteRequest

    /** Top-level conflicts exist; a decision is required before running */
    data class ResolveRequest(
        val sources: List<Path>,
        val targetDir: Path,
        val mode: PasteMode,
        /** Conflict entries aligned one-to-one with [sources]; non-empty means that entry needs a decision */
        val conflicts: List<ConflictItem?>
    ) : PasteRequest
}

data class ConflictItem(val source: Path, val existing: Path)

/** Per-entry result after pasting. */
data class ItemResult(
    val source: Path,
    val target: Path?,
    val success: Boolean,
    val reason: String?
)

/** Overall paste result. */
data class PasteSummary(
    val mode: PasteMode,
    val targetDir: Path,
    val results: List<ItemResult>
)