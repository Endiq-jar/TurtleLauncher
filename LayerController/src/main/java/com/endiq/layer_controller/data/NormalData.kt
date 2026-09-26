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

import com.endiq.layer_controller.data.lang.TranslatableString
import com.endiq.layer_controller.event.ClickEvent
import com.endiq.layer_controller.observable.Modifiable
import com.endiq.layer_controller.observable.isModified
import com.endiq.layer_controller.utils.getAButtonUUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * @param clickEvents the click events
 * @param isSwipple whether swiping cascades onto neighboring buttons
 * @param isPenetrable whether touch events may pass through
 * @param isToggleable whether pressed state toggles like a switch
 */
@Serializable
data class NormalData(
    @SerialName("text")
    val text: TranslatableString,
    @SerialName("uuid")
    val uuid: String,
    @SerialName("position")
    val position: ButtonPosition,
    @SerialName("buttonSize")
    val buttonSize: ButtonSize,
    @SerialName("buttonStyle")
    val buttonStyle: String? = null,
    @SerialName("textAlignment")
    val textAlignment: TextAlignment = TextAlignment.Left,
    @SerialName("textBold")
    val textBold: Boolean = false,
    @SerialName("textItalic")
    val textItalic: Boolean = false,
    @SerialName("textUnderline")
    val textUnderline: Boolean = false,
    @SerialName("visibilityType")
    val visibilityType: VisibilityType,
    @SerialName("clickEvents")
    private var _clickEvents: List<ClickEvent> = emptyList(),
    @SerialName("isSwipple")
    val isSwipple: Boolean,
    @SerialName("isPenetrable")
    val isPenetrable: Boolean,
    @SerialName("isToggleable")
    val isToggleable: Boolean
): Widget, Modifiable<NormalData> {
    val clickEvents: List<ClickEvent> get() = _clickEvents

    init {
        _clickEvents = _clickEvents.filterValidEvent()
    }

    override fun isModified(other: NormalData): Boolean {
        return this.text.isModified(other.text) ||
                this.uuid != other.uuid ||
                this.position.isModified(other.position) ||
                this.buttonSize.isModified(other.buttonSize) ||
                this.buttonStyle != other.buttonStyle ||
                this.textAlignment != other.textAlignment ||
                this.textBold != other.textBold ||
                this.textItalic != other.textItalic ||
                this.textUnderline != other.textUnderline ||
                this.visibilityType != other.visibilityType ||
                this._clickEvents.isModified(other._clickEvents) ||
                this.isSwipple != other.isSwipple ||
                this.isPenetrable != other.isPenetrable ||
                this.isToggleable != other.isToggleable
    }
}

/**
 * Filters out valid click events
 */
internal fun List<ClickEvent>.filterValidEvent(): List<ClickEvent> {
    var foundValidSendText = false
    return filter { event ->
        if (event.type == ClickEvent.Type.SendText) {
            if (!foundValidSendText && event.key.isNotEmpty()) {
                foundValidSendText = true
                true
            } else {
                false
            }
        } else {
            true
        }
    }
}

/**
 * Clones a new NormalData (with a different UUID and position)
 */
fun NormalData.cloneNew(): NormalData = NormalData(
    text = this.text,
    uuid = getAButtonUUID(),
    position = CenterPosition,
    buttonSize = buttonSize,
    buttonStyle = buttonStyle,
    textAlignment = textAlignment,
    textBold = textBold,
    textItalic = textItalic,
    textUnderline = textUnderline,
    visibilityType = visibilityType,
    _clickEvents = clickEvents,
    isSwipple = isSwipple,
    isPenetrable = isPenetrable,
    isToggleable = isToggleable
)