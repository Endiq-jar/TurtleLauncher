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

package com.endiq.turtlelauncher.game.addons.mirror

import com.endiq.turtlelauncher.setting.enums.MirrorSourceType

/** Mirror usage strategy resolved from the settings */
enum class MirrorPriority {
    /** Only the official source */
    OFFICIAL,
    /** Mirrors first, official last */
    MIRROR_FIRST
}

/**
 * Resolves the three-level setting into a mirror usage strategy
 * Auto is decided statically from mainland-China detection; source failover is handled adaptively by the download engine at runtime, no probing needed.
 */
fun resolveMirrorPriority(source: MirrorSourceType, mainland: Boolean): MirrorPriority =
    when {
        source == MirrorSourceType.OFFICIAL -> MirrorPriority.OFFICIAL
        source == MirrorSourceType.MIRROR -> MirrorPriority.MIRROR_FIRST
        mainland -> MirrorPriority.MIRROR_FIRST
        else -> MirrorPriority.OFFICIAL
    }

/** Produces the candidate list per the active strategy; official-only mode injects no mirrors, and only the official source remains when a mirror link is missing */
fun orderCandidates(official: String, mirror: String?, priority: MirrorPriority): List<String> =
    when (priority) {
        MirrorPriority.OFFICIAL -> listOfNotNull(official)
        MirrorPriority.MIRROR_FIRST -> listOfNotNull(mirror, official)
    }
