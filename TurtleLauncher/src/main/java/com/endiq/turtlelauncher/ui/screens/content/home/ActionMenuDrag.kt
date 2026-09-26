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

package com.endiq.turtlelauncher.ui.screens.content.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import com.endiq.turtlelauncher.setting.enums.ActionMenuSide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Zoom factor while the ActionMenu is lifted
 */
private const val PickUpScale = 1.08f

interface ActionMenuDragHandler {
    fun onDragStart(position: Offset)
    fun onDrag(position: Offset)
    fun onDragEnd()
    fun onDragCancel()

    /** Register an inner region that handles long-press itself */
    fun addExclusion(exclusion: ActionMenuDragExclusion)
    /** Remove a registered inner region */
    fun removeExclusion(exclusion: ActionMenuDragExclusion)
}

/**
 * Inner region handling long-press itself (root-coordinate rect, updated with layout)
 */
class ActionMenuDragExclusion {
    internal var bounds: Rect? = null
}

/**
 * The active ActionMenu long-press drag handler
 */
val LocalActionMenuDrag = staticCompositionLocalOf<ActionMenuDragHandler?> { null }

/**
 * Marks the backdrop as the ActionMenu's long-press drag anchor
 */
@Composable
fun Modifier.actionMenuDragAnchor(): Modifier {
    val handler = LocalActionMenuDrag.current ?: return this
    var anchorCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    return this
        .onGloballyPositioned { anchorCoordinates = it }
        .pointerInput(handler) {
            detectDragGesturesAfterLongPress(
                onDragStart = { position ->
                    anchorCoordinates?.let { coordinates ->
                        handler.onDragStart(coordinates.positionInRoot() + position)
                    }
                },
                onDragEnd = { handler.onDragEnd() },
                onDragCancel = { handler.onDragCancel() },
                onDrag = { change, _ ->
                    anchorCoordinates?.let { coordinates ->
                        handler.onDrag(coordinates.positionInRoot() + change.position)
                    }
                }
            )
        }
}

/**
 * Declares this element region handles long-press itself
 */
@Composable
fun Modifier.actionMenuDragExclusion(): Modifier {
    val handler = LocalActionMenuDrag.current ?: return this
    val exclusion = remember { ActionMenuDragExclusion() }
    DisposableEffect(handler) {
        handler.addExclusion(exclusion)
        onDispose {
            handler.removeExclusion(exclusion)
        }
    }
    return this.onGloballyPositioned { coordinates ->
        exclusion.bounds = if (coordinates.isAttached) coordinates.boundsInRoot() else null
    }
}

/**
 * Creates an [ActionMenuDragState]
 * @param onCommit commits the final docked side on release
 */
@Composable
fun rememberActionMenuDragState(
    onCommit: (ActionMenuSide) -> Unit
): ActionMenuDragState {
    val scope = rememberCoroutineScope()
    val settleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Offset>()
    val scaleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val previewSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val currentOnCommit by rememberUpdatedState(onCommit)
    return remember {
        ActionMenuDragState(
            scope = scope,
            settleSpec = settleSpec,
            scaleSpec = scaleSpec,
            previewSpec = previewSpec,
            onCommit = { currentOnCommit(it) }
        )
    }
}

/**
 * State holder for the ActionMenu long-press drag
 */
@Stable
class ActionMenuDragState(
    private val scope: CoroutineScope,
    private val settleSpec: AnimationSpec<Offset>,
    private val scaleSpec: AnimationSpec<Float>,
    private val previewSpec: AnimationSpec<Float>,
    private val onCommit: (ActionMenuSide) -> Unit
) : ActionMenuDragHandler {
    /**
     * Whether the ActionMenu is lifted
     */
    var floating by mutableStateOf(false)
        private set

    /**
     * The dock side previewed while dragging; null when not dragging
     */
    var previewSide by mutableStateOf<ActionMenuSide?>(null)
        private set

    /**
     * Zoom ratio while lifted
     */
    var scale by mutableFloatStateOf(1f)
        private set

    /**
     * The lifted card's top-left position in parent coordinates
     */
    var cardPosition by mutableStateOf(Offset.Zero)
        private set

    /**
     * Docking-animation offset after release, layered on the docked position
     */
    val settleOffset = Animatable(Offset.Zero, Offset.VectorConverter)

    /**
     * Horizontal shift of the content area when previewing (layout-direction coordinates; positive toward the end side),
     * when switching dock-side previews, the content area translates one slot toward the dock side
     */
    val previewShift = Animatable(0f, Float.VectorConverter)

    private var settleJob: Job? = null
    private var scaleJob: Job? = null
    private var fingerAtGrab = Offset.Zero
    private var cardPositionAtGrab = Offset.Zero

    private val exclusions = mutableListOf<ActionMenuDragExclusion>()

    /**
     * Docked side (persistent state)
     */
    var dockedSide = ActionMenuSide.END

    /**
     * Whether the layout direction is right-to-left
     */
    var isRtl = false

    /**
     * The parent container's position in root coordinates, converting touch positions
     */
    var parentOrigin = Offset.Zero

    /**
     * Parent width in px, used to map the screen midline and docked positions
     */
    var parentWidthPx = 0f

    /**
     * Gap between dock-slot content and screen edge (px)
     */
    var outerPaddingPx = 0f

    /**
     * Dock-slot width (px): the content/card displacement when switching sides
     */
    var menuSpanPx = 0f

    /**
     * @return the given side's dock-slot content position (parent coordinates)
     */
    fun landingOf(side: ActionMenuSide): Offset {
        val physicallyLeft = if (isRtl) side == ActionMenuSide.END else side == ActionMenuSide.START
        val x = if (physicallyLeft) outerPaddingPx else parentWidthPx - menuSpanPx
        return Offset(x, outerPaddingPx)
    }

    override fun addExclusion(exclusion: ActionMenuDragExclusion) {
        exclusions.add(exclusion)
    }

    override fun removeExclusion(exclusion: ActionMenuDragExclusion) {
        exclusions.remove(exclusion)
    }

    override fun onDragStart(position: Offset) {
        //Releases landing inside a self-handling region are not taken over, avoiding dual long-press triggers
        if (exclusions.any { it.bounds?.contains(position) == true }) return

        settleJob?.cancel()
        fingerAtGrab = position - parentOrigin
        //Anchor on the current render position (docked + in-progress offset), so lifting starts with no jump
        cardPositionAtGrab = landingOf(dockedSide) + settleOffset.value
        cardPosition = cardPositionAtGrab
        previewSide = dockedSide
        floating = true
        animatePreviewTo(0f)
        animateScaleTo(PickUpScale)
    }

    override fun onDrag(position: Offset) {
        if (!floating) return
        val finger = position - parentOrigin
        cardPosition = cardPositionAtGrab + (finger - fingerAtGrab)
        //Preview the dock side by whether the finger is on the physical left half
        val physicalLeft = finger.x < parentWidthPx / 2f
        val side = if (isRtl) {
            if (physicalLeft) ActionMenuSide.END else ActionMenuSide.START
        } else {
            if (physicalLeft) ActionMenuSide.START else ActionMenuSide.END
        }
        if (side != previewSide) {
            previewSide = side
            animatePreviewTo(previewShiftTargetOf(side))
        }
    }

    override fun onDragEnd() {
        if (!floating) return
        val target = previewSide ?: dockedSide
        settle(target)
    }

    override fun onDragCancel() {
        if (!floating) return
        settle(dockedSide)
    }

    /**
     * Releases the card, animating from the current spot to the target dock slot
     */
    private fun settle(target: ActionMenuSide) {
        settleJob?.cancel()
        val releasePosition = cardPosition
        settleJob = scope.launch {
            if (target != dockedSide) {
                onCommit(target)
                val shiftTarget = previewShiftTargetOf(target)
                previewShift.snapTo(previewShift.value - shiftTarget)
            }
            launch {
                previewShift.animateTo(0f, previewSpec)
            }
            settleOffset.snapTo(releasePosition - landingOf(target))
            previewSide = null
            floating = false
            launch { animateScaleTo(1f) }
            settleOffset.animateTo(Offset.Zero, settleSpec)
        }
    }

    private fun animateScaleTo(target: Float) {
        scaleJob?.cancel()
        scaleJob = scope.launch {
            animate(
                initialValue = scale,
                targetValue = target,
                animationSpec = scaleSpec
            ) { value, _ -> scale = value }
        }
    }

    /**
     * @return the content shift needed to preview the given side
     */
    private fun previewShiftTargetOf(side: ActionMenuSide): Float = when {
        side == dockedSide -> 0f
        dockedSide == ActionMenuSide.END -> menuSpanPx
        else -> -menuSpanPx
    }

    private fun animatePreviewTo(target: Float) {
        scope.launch {
            previewShift.animateTo(target, previewSpec)
        }
    }
}
