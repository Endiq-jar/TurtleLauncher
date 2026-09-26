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

import com.endiq.layer_controller.data.ButtonSize.Reference
import com.endiq.layer_controller.data.ButtonSize.Type
import com.endiq.layer_controller.observable.Modifiable
import com.endiq.layer_controller.utils.checkInRange
import com.endiq.layer_controller.utils.checkMin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Minimum button size
 */
const val MIN_SIZE_DP = 5.0f

/**
 * Minimum button size percentage
 */
internal const val MIN_SIZE_PERCENTAGE = 100

/**
 * Maximum button size percentage
 */
internal const val MAX_SIZE_PERCENTAGE = 10000

/**
 * Valid range of button size percentages
 */
val SIZE_PERCENTAGE: ClosedFloatingPointRange<Float> = 100.0f..10000.0f

/**
 * Percentage range used by the editor
 */
val SIZE_PERCENTAGE_EDITOR: ClosedFloatingPointRange<Float> = 1.0f..100.0f

/**
 * Size of a button
 * @param widthDp absolute width, 5..device width in Dp
 * @param heightDp absolute height, 5..device height in Dp
 * @param widthPercentage percentage width, 100..10000
 * @param heightPercentage percentage height, 100..10000
 */
@Serializable
data class ButtonSize(
    @SerialName("type")
    val type: Type,
    @SerialName("widthDp")
    val widthDp: Float,
    @SerialName("heightDp")
    val heightDp: Float,
    @SerialName("widthPercentage")
    val widthPercentage: Int,
    @SerialName("heightPercentage")
    val heightPercentage: Int,
    @SerialName("widthReference")
    val widthReference: Reference,
    @SerialName("heightReference")
    val heightReference: Reference
): Modifiable<ButtonSize> {
    init {
        checkMin("widthDp", widthDp, MIN_SIZE_DP)
        checkMin("heightDp", heightDp, MIN_SIZE_DP)
        checkInRange("widthPercentage", widthPercentage.toFloat(), SIZE_PERCENTAGE)
        checkInRange("heightPercentage", heightPercentage.toFloat(), SIZE_PERCENTAGE)
    }

    /**
     * Size calculation type
     */
    @Serializable
    enum class Type {
        /**
         * Stores the size as an absolute Dp value
         */
        @SerialName("dp") Dp,

        /**
         * Stores the size as a percentage value
         */
        @SerialName("percentage") Percentage,

        /**
         * Follows the content size
         */
        @SerialName("wrap_content") WrapContent
    }

    @Serializable
    enum class Reference {
        /**
         * References the screen width
         */
        @SerialName("screen_width") ScreenWidth,

        /**
         * References the screen height
         */
        @SerialName("screen_height") ScreenHeight,
    }

    override fun isModified(other: ButtonSize): Boolean {
        return this.type != other.type ||
                this.widthDp != other.widthDp ||
                this.heightDp != other.heightDp ||
                this.widthPercentage != other.widthPercentage ||
                this.heightPercentage != other.heightPercentage ||
                this.widthReference != other.widthReference ||
                this.heightReference != other.heightReference
    }
}

/**
 * Default size: stored as a percentage value
 */
val DefaultSize = ButtonSize(
    type = Type.Percentage,
    widthDp = 50f,
    heightDp = 50f,
    widthPercentage = 1400,
    heightPercentage = 1400,
    widthReference = Reference.ScreenHeight,
    heightReference = Reference.ScreenHeight
)

/**
 * Creates a default percentage size computed from the reference size
 */
fun createAdaptiveButtonSize(
    referenceLength: Int,
    type: Type = Type.Percentage,
    reference: Reference = Reference.ScreenHeight,
    density: Float = 1f,
    targetDpSize: Float = 50f
): ButtonSize {
    val percentage = ((targetDpSize * density) / referenceLength * 10000).toInt()

    return ButtonSize(
        type = type,
        widthDp = targetDpSize,
        heightDp = targetDpSize,
        widthPercentage = percentage,
        heightPercentage = percentage,
        widthReference = reference,
        heightReference = reference
    )
}