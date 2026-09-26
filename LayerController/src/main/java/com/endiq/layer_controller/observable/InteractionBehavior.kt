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

package com.endiq.layer_controller.observable

/**
 * Interaction behavior model of a widget
 */
sealed class InteractionBehavior {
    /**
     * Whether the widget auto-releases when the finger leaves its bounds
     */
    abstract val releaseOnOutOfBounds: Boolean

    /**
     * Whether pointers from other widgets may slide into this one
     */
    abstract val canBeSwipedTo: Boolean

    /**
     * Whether this widget, while active, blocks the swipe chain from propagating to other widgets
     */
    abstract val blocksSwipeChain: Boolean



    /**
     * Normal button
     * Holds while pressed, releases on lift, ignores swipe linking
     */
    data object Press : InteractionBehavior() {
        override val releaseOnOutOfBounds: Boolean get() = false
        override val canBeSwipedTo: Boolean get() = false
        override val blocksSwipeChain: Boolean get() = false
    }

    /**
     * Swipple button
     * Auto-releases out of bounds, supports swipe linking
     */
    data object Swipable : InteractionBehavior() {
        override val releaseOnOutOfBounds: Boolean get() = true
        override val canBeSwipedTo: Boolean get() = true
        override val blocksSwipeChain: Boolean get() = false
    }

    /**
     * Toggleable button:
     * Tap toggles on/off; no swipe linking; blocks swipe-chain propagation while active
     */
    data object Toggle : InteractionBehavior() {
        override val releaseOnOutOfBounds: Boolean get() = false
        override val canBeSwipedTo: Boolean get() = false
        override val blocksSwipeChain: Boolean get() = true
    }

    companion object {
        fun from(isSwipple: Boolean, isToggleable: Boolean): InteractionBehavior = when {
            isToggleable -> Toggle
            isSwipple -> Swipable
            else -> Press
        }
    }
}
