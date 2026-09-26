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

package com.endiq.turtlelauncher.setting.enums

import com.endiq.turtlelauncher.R

enum class MirrorSourceType(val textRes: Int) {
    /**
     * Automatically chooses a download source
     * The current network decides the initial preference; the download engine adapts sources on failures
     */
    AUTO(R.string.settings_launcher_mirror_auto),

    /**
     * Prefer the official source
     */
    OFFICIAL(R.string.settings_launcher_mirror_official),

    /**
     * Prefer the mirror source
     */
    MIRROR(R.string.settings_launcher_mirror_mirror);

    companion object {
        /** Mapping from legacy enum names to current ones; fallback when reading historic saved settings */
        val LEGACY_NAMES: Map<String, MirrorSourceType> = mapOf(
            "OFFICIAL_FIRST" to OFFICIAL,
            "MIRROR_FIRST" to MIRROR
        )
    }
}