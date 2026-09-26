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

import com.endiq.inputmap.keycodes.ControlEventKeycode.GLFW_KEY_A
import com.endiq.inputmap.keycodes.ControlEventKeycode.GLFW_KEY_D
import com.endiq.inputmap.keycodes.ControlEventKeycode.GLFW_KEY_LEFT_CONTROL
import com.endiq.inputmap.keycodes.ControlEventKeycode.GLFW_KEY_S
import com.endiq.inputmap.keycodes.ControlEventKeycode.GLFW_KEY_W
import com.endiq.layer_controller.event.ClickEvent
import com.endiq.layer_controller.observable.Modifiable
import com.endiq.layer_controller.utils.checkInRange
import com.endiq.layer_controller.utils.getAButtonUUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Minimum absolute joystick widget size (Dp)
 */
const val JOYSTICK_MIN_SIZE_DP = 20f

/**
 * Minimum joystick widget size percentage (100 = 1%)
 */
const val JOYSTICK_MIN_SIZE_PERCENTAGE = 2000

/**
 * Valid range of the joystick head size
 */
val JOYSTICK_KNOB_SIZE_RANGE: ClosedFloatingPointRange<Float> = 0.1f..1.0f

/**
 * Valid range of the dead-zone ratio
 */
val JOYSTICK_DEAD_ZONE_RANGE: ClosedFloatingPointRange<Float> = 0.0f..0.9f

/**
 * Valid range of the lock threshold
 */
val JOYSTICK_LOCK_THRESHOLD_RANGE: ClosedFloatingPointRange<Float> = 0.0f..1.0f

/**
 * Joystick widget data model
 * The joystick is always square; width and height share one value
 * @param uuid unique widget identifier
 * @param position widget position
 * @param sizeType size type (WrapContent unsupported)
 * @param sizeDp size value in Dp mode
 * @param sizePercentage size value in percentage mode
 * @param visibilityType widget visibility type
 * @param joystickStyleId referenced joystick style ID
 * @param deadZoneRatio dead-zone ratio
 * @param lockThreshold forward-lock threshold (as a percentage of the background layer size)
 * @param canLock whether forward-lock is supported
 * @param triggerMode trigger mode (drag / touch)
 * @param directionEvents direction binding events
 * @param lockEvents events fired while locked
 */
@Serializable
data class JoystickData(
    @SerialName("uuid")
    val uuid: String,
    @SerialName("position")
    val position: ButtonPosition,
    @SerialName("sizeType")
    val sizeType: ButtonSize.Type = ButtonSize.Type.Percentage,
    @SerialName("sizeDp")
    val sizeDp: Float = 200f,
    @SerialName("sizePercentage")
    val sizePercentage: Int = 2500,
    @SerialName("visibilityType")
    val visibilityType: VisibilityType = VisibilityType.ALWAYS,
    @SerialName("joystickStyleId")
    val joystickStyleId: String? = null,
    @SerialName("deadZoneRatio")
    val deadZoneRatio: Float = 0.5f,
    @SerialName("lockThreshold")
    val lockThreshold: Float = 0.3f,
    @SerialName("canLock")
    val canLock: Boolean = true,
    @SerialName("triggerMode")
    val triggerMode: JoystickTriggerMode = JoystickTriggerMode.DRAG,
    @SerialName("directionEvents")
    val directionEvents: Map<JoystickDirection, List<ClickEvent>> = DefaultDirectionEvents,
    @SerialName("lockEvents")
    val lockEvents: List<ClickEvent> = DefaultLockEvents,
): Widget, Modifiable<JoystickData> {
    init {
        require(sizeType != ButtonSize.Type.WrapContent) { "JoystickData does not support WrapContent size type" }
        checkInRange("deadZoneRatio", deadZoneRatio, JOYSTICK_DEAD_ZONE_RANGE)
        checkInRange("lockThreshold", lockThreshold, JOYSTICK_LOCK_THRESHOLD_RANGE)
    }

    /**
     * Converts the joystick size into a ButtonSize (width=height) for the editMode / buttonSize modifiers
     */
    fun toButtonSize(): ButtonSize {
        val clampedDp = sizeDp.coerceAtLeast(JOYSTICK_MIN_SIZE_DP)
        val clampedPercentage = sizePercentage.coerceAtLeast(JOYSTICK_MIN_SIZE_PERCENTAGE)
        return when (sizeType) {
            ButtonSize.Type.Dp -> ButtonSize(
                type = ButtonSize.Type.Dp,
                widthDp = clampedDp,
                heightDp = clampedDp,
                widthPercentage = MIN_SIZE_PERCENTAGE,
                heightPercentage = MIN_SIZE_PERCENTAGE,
                widthReference = ButtonSize.Reference.ScreenHeight,
                heightReference = ButtonSize.Reference.ScreenHeight
            )
            ButtonSize.Type.Percentage -> ButtonSize(
                type = ButtonSize.Type.Percentage,
                widthDp = clampedDp,
                heightDp = clampedDp,
                widthPercentage = clampedPercentage,
                heightPercentage = clampedPercentage,
                widthReference = ButtonSize.Reference.ScreenHeight,
                heightReference = ButtonSize.Reference.ScreenHeight
            )
            else -> ButtonSize(
                type = ButtonSize.Type.Dp,
                widthDp = 200f,
                heightDp = 200f,
                widthPercentage = MIN_SIZE_PERCENTAGE,
                heightPercentage = MIN_SIZE_PERCENTAGE,
                widthReference = ButtonSize.Reference.ScreenHeight,
                heightReference = ButtonSize.Reference.ScreenHeight
            )
        }
    }

    override fun isModified(other: JoystickData): Boolean {
        return this.uuid != other.uuid ||
                this.position.isModified(other.position) ||
                this.sizeType != other.sizeType ||
                this.sizeDp != other.sizeDp ||
                this.sizePercentage != other.sizePercentage ||
                this.visibilityType != other.visibilityType ||
                this.joystickStyleId != other.joystickStyleId ||
                this.deadZoneRatio != other.deadZoneRatio ||
                this.lockThreshold != other.lockThreshold ||
                this.canLock != other.canLock ||
                this.triggerMode != other.triggerMode ||
                this.directionEvents != other.directionEvents ||
                this.lockEvents != other.lockEvents
    }
}

/**
 * Default joystick direction event bindings
 */
val DefaultDirectionEvents = buildMap {
    val forward = ClickEvent(ClickEvent.Type.Key, GLFW_KEY_W)
    val back = ClickEvent(ClickEvent.Type.Key, GLFW_KEY_S)
    val left = ClickEvent(ClickEvent.Type.Key, GLFW_KEY_A)
    val right = ClickEvent(ClickEvent.Type.Key, GLFW_KEY_D)

    put(JoystickDirection.North, listOf(forward))
    put(JoystickDirection.NorthEast, listOf(forward, right))
    put(JoystickDirection.NorthWest, listOf(forward, left))

    put(JoystickDirection.South, listOf(back))
    put(JoystickDirection.SouthEast, listOf(back, right))
    put(JoystickDirection.SouthWest, listOf(back, left))

    put(JoystickDirection.East, listOf(right))
    put(JoystickDirection.West, listOf(left))
}

/**
 * Default joystick lock event bindings
 */
val DefaultLockEvents = buildList {
    add(ClickEvent(ClickEvent.Type.Key, GLFW_KEY_LEFT_CONTROL))
}

fun JoystickData.cloneNew(): JoystickData = JoystickData(
    uuid = getAButtonUUID(),
    position = CenterPosition,
    sizeType = sizeType,
    sizeDp = sizeDp,
    sizePercentage = sizePercentage,
    visibilityType = visibilityType,
    joystickStyleId = joystickStyleId,
    deadZoneRatio = deadZoneRatio,
    lockThreshold = lockThreshold,
    canLock = canLock,
    triggerMode = triggerMode,
    directionEvents = directionEvents,
    lockEvents = lockEvents
)
