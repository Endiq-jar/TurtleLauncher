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

package com.endiq.turtlelauncher.utils.animation

import com.endiq.turtlelauncher.R

/**
 * All available launcher page / content transition styles. The enum order is
 * stable and is persisted in settings by ordinal through [legacyNames], so
 * new entries must be appended, never reordered.
 */
enum class TransitionAnimationType(val textRes: Int) {
    /** Off */
    CLOSE(R.string.generic_close),
    /** Rebound */
    JELLY_BOUNCE(R.string.animate_type_jelly_bounce),
    /** Bounce */
    BOUNCE(R.string.animate_type_bounce),
    /** Cut in */
    SLICE_IN(R.string.animate_type_slice_in),

    /** Fade/blend */
    FADE(R.string.animate_type_fade),
    /** Slide up */
    SLIDE_UP(R.string.animate_type_slide_up),
    /** Slide down */
    SLIDE_DOWN(R.string.animate_type_slide_down),
    /** Slide left */
    SLIDE_LEFT(R.string.animate_type_slide_left),
    /** Slide right */
    SLIDE_RIGHT(R.string.animate_type_slide_right),
    /** Fade + slide up */
    FADE_SLIDE_UP(R.string.animate_type_fade_slide_up),
    /** Fade + slide down */
    FADE_SLIDE_DOWN(R.string.animate_type_fade_slide_down),
    /** Fade + slide left */
    FADE_SLIDE_LEFT(R.string.animate_type_fade_slide_left),
    /** Fade + slide right */
    FADE_SLIDE_RIGHT(R.string.animate_type_fade_slide_right),
    /** Scale in */
    SCALE(R.string.animate_type_scale),
    /** Zoom in */
    ZOOM_IN(R.string.animate_type_zoom_in),
    /** Zoom out */
    ZOOM_OUT(R.string.animate_type_zoom_out),
    /** Expand from center */
    EXPAND_CENTER(R.string.animate_type_expand_center),
    /** Wipe vertically */
    WIPE_VERTICAL(R.string.animate_type_wipe_vertical),
    /** Wipe horizontally */
    WIPE_HORIZONTAL(R.string.animate_type_wipe_horizontal),
    /** Overshoot with back easing */
    OVERSHOOT(R.string.animate_type_overshoot),
    /** Reveal + fade */
    REVEAL(R.string.animate_type_reveal),
    /** Deep zoom dissolve */
    ZOOM_DISSOLVE(R.string.animate_type_zoom_dissolve)
}
