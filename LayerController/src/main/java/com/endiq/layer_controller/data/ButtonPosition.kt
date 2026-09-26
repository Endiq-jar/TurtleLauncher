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

package com.endiq.layer_controller.data

import com.endiq.layer_controller.observable.Modifiable
import com.endiq.layer_controller.utils.checkInRange
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Stored value range of a button position
 */
val POSITION_RANGE: IntRange = 0..10000

/**
 * Position of a button
 * @param x 0~10000
 * @param y 0~10000
 */
@Serializable
data class ButtonPosition(
    @SerialName("x")
    val x: Int,
    @SerialName("y")
    val y: Int
): Modifiable<ButtonPosition> {
    init {
        checkInRange("x", x, POSITION_RANGE)
        checkInRange("y", y, POSITION_RANGE)
    }

    /**
     * Computes the x coordinate percentage
     */
    fun xPercentage(): Float {
        return (x / 10000f).coerceAtMost(1f).coerceAtLeast(0f)
    }

    /**
     * Computes the y coordinate percentage
     */
    fun yPercentage(): Float {
        return (y / 10000f).coerceAtMost(1f).coerceAtLeast(0f)
    }

    override fun isModified(other: ButtonPosition): Boolean {
        return this.x != other.x ||
                this.y != other.y
    }
}

/**
 * Located at the screen's top-left corner
 */
val TopStartPosition = ButtonPosition(0, 0)

/**
 * Located at the screen's top-right corner
 */
val TopEndPosition = ButtonPosition(10000, 0)

/**
 * Located at the screen center
 */
val CenterPosition = ButtonPosition(5000, 5000)

/**
 * Located at the screen's bottom-left corner
 */
val BottomStartPosition = ButtonPosition(0, 10000)

/**
 * Located at the screen's bottom-right corner
 */
val BottomEndPosition = ButtonPosition(10000, 10000)
