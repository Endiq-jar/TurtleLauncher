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

package com.endiq.cardgrid.ui

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.endiq.cardgrid.model.CardInteraction
import com.endiq.cardgrid.state.CardGridState
import com.endiq.cardgrid.state.GridCard
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** How far the grid lines fade outward from the card rect */
private val GridFadeExtent = 72.dp
/** Maximum grid-line opacity (at the card rect) */
private const val GridLineMaxAlpha = 0.35f

/** Height of the adjust-mode toolbar */
private val ToolbarHeight = 40.dp
/** Gap between the toolbar and the card edge */
private val ToolbarGap = 8.dp

/**
 * Card grid container
 * @param cardBackground background decoration of the card content, applied inside the card surface
 * @param adjustingBar the toolbar UI component
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CardGrid(
    state: CardGridState,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceBright,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    cardBackground: (@Composable (Modifier) -> Modifier)? = null,
    adjustingBar: (@Composable (Modifier, GridCard) -> Unit)? = null,
) {
    val hostDirection = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        CardGridCanvas(
            state = state,
            modifier = modifier,
            containerColor = containerColor,
            contentColor = contentColor,
            cardBackground = cardBackground,
            adjustingBar = adjustingBar,
            contentDirection = hostDirection
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CardGridCanvas(
    state: CardGridState,
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color,
    cardBackground: (@Composable (Modifier) -> Modifier)?,
    adjustingBar: (@Composable (Modifier, GridCard) -> Unit)?,
    contentDirection: LayoutDirection
) {
    val density = LocalDensity.current
    val motionScheme = MaterialTheme.motionScheme
    LaunchedEffect(motionScheme) {
        state.fastSpec = motionScheme.fastSpatialSpec()
        state.defaultSpec = motionScheme.defaultSpatialSpec()
    }

    val heightPx = state.gridHeightPx()
    val targetHeight = with(density) { heightPx.toDp() }
    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardGridHeight"
    )

    val haptics = LocalHapticFeedback.current

    BackHandler(enabled = state.isAdjusting) {
        state.exitAdjusting()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                with(density) {
                    state.updateGeometry(coordinates.size.width.toDp().value, this)
                }
                state.onAreaPositioned(coordinates.positionInRoot())
            }
            .height(animatedHeight)
            .pointerInput(Unit) {
                // key is constantly Unit: adjust mode is read dynamically from state.isAdjusting at each gesture start;
                // using it as a key would restart on long-press and cancel the in-flight session
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Gestures already consumed by interactive subcomponents (like the adjust toolbar) do not belong to the grid
                    if (down.isConsumed) return@awaitEachGesture
                    val start = down.position
                    val slopPx = viewConfiguration.touchSlop
                    val longPressMillis = viewConfiguration.longPressTimeoutMillis

                    if (!state.isAdjusting) {
                        // Outside adjust mode: long-pressing a card enters adjust mode and starts dragging right away
                        val hit = state.cardAt(start) ?: return@awaitEachGesture
                        val longPressed = awaitLongPressOrAbort(down.id, start, slopPx, longPressMillis)
                        if (!longPressed) return@awaitEachGesture
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        state.onCardDragStart(hit, start)
                        runSession(
                            pointerId = down.id,
                            startPosition = start,
                            slopPx = slopPx,
                            state = state,
                            dispatch = { state.onCardDrag(it) },
                            onUp = { moved ->
                                if (moved) state.onCardDragEnd() else state.onCardDragCancel()
                            }
                        )
                    } else {
                        val edgeHit = state.resizeEdgeAt(start)
                        val card = state.cardAt(start)
                        when {
                            // In adjust mode: pressing a handle starts resizing immediately
                            edgeHit != null -> {
                                val (hitCard, edge) = edgeHit
                                state.onResizeStart(hitCard, edge, start)
                                runSession(
                                    pointerId = down.id,
                                    startPosition = start,
                                    slopPx = slopPx,
                                    state = state,
                                    dispatch = { state.onResize(it) },
                                    onUp = { state.onResizeEnd() }
                                )
                            }
                            // In adjust mode: pressing a card starts dragging; releasing without movement counts as a tap
                            card != null -> {
                                state.onCardDragStart(card, start)
                                runSession(
                                    pointerId = down.id,
                                    startPosition = start,
                                    slopPx = slopPx,
                                    state = state,
                                    dispatch = { state.onCardDrag(it) },
                                    onUp = { moved ->
                                        when {
                                            moved -> state.onCardDragEnd()
                                            card.id == state.adjustingCardId -> state.onCardDragCancel()
                                            else -> {
                                                state.onCardDragCancel()
                                                state.exitAdjusting()
                                            }
                                        }
                                    }
                                )
                            }
                            // In adjust mode: tapping empty space exits adjust mode
                            else -> {
                                val tapped = awaitUpWithoutSlop(down.id, start, slopPx)
                                if (tapped) state.exitAdjusting()
                            }
                        }
                    }
                }
            }
    ) {
        CardGridGlowEffect(state = state, modifier = Modifier.matchParentSize())
        state.cards.forEach { card ->
            key(card.id) {
                CardSlot(
                    state = state,
                    card = card,
                    containerColor = containerColor,
                    contentColor = contentColor,
                    cardBackground = cardBackground,
                    adjustingBar = adjustingBar,
                    contentDirection = contentDirection
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CardSlot(
    state: CardGridState,
    card: GridCard,
    containerColor: Color,
    contentColor: Color,
    cardBackground: (@Composable (Modifier) -> Modifier)?,
    adjustingBar: (@Composable (Modifier, GridCard) -> Unit)?,
    contentDirection: LayoutDirection
) {
    val rectProvider: () -> Rect = { state.renderRectOf(card) }
    val interaction = state.interactionOf(card.id)
    val zIndex = when {
        state.isSessionCard(card.id) -> 3f
        interaction != CardInteraction.Idle -> 2f
        else -> 1f
    }
    val adjusting = state.adjustingCardId == card.id

    Box(
        modifier = Modifier
            .cardBounds(rectProvider)
            .zIndex(zIndex)
    ) {
        CardSurface(
            interaction = interaction,
            modifier = Modifier.fillMaxSize(),
            shape = card.type.shape ?: MaterialTheme.shapes.extraLarge,
            containerColor = containerColor,
            contentColor = contentColor,
            cardBackground = cardBackground,
            selected = adjusting
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides contentDirection) {
                card.type.content(state.cardStateOf(card), card.id)
            }
        }

        if (adjusting && adjustingBar != null) {
            val density = LocalDensity.current
            val barExtentPx = with(density) { (ToolbarHeight + ToolbarGap).toPx() }
            val cardTopInViewport = state.areaOffsetInRoot.y +
                state.rectFor(state.effectiveLayout(card)).top - state.viewportTopPx
            val placeAbove = cardTopInViewport >= barExtentPx
            val barOffset by animateDpAsState(
                targetValue = if (placeAbove) {
                    -(ToolbarHeight + ToolbarGap)
                } else {
                    with(density) { rectProvider().height.toDp() } + ToolbarGap
                },
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "cardToolbarOffset"
            )

            adjustingBar(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = barOffset)
                    .height(ToolbarHeight)
                    .zIndex(1f)
                    .gestureGuard(),
                card
            )
        }
    }
}

/**
 * Container of the card surface: elevation animation for interaction and selection handles in adjust mode
 */
@Composable
private fun CardSurface(
    interaction: CardInteraction,
    modifier: Modifier = Modifier,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    cardBackground: (@Composable (Modifier) -> Modifier)?,
    selected: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val activated = interaction != CardInteraction.Idle
    val elevation by animateDpAsState(
        targetValue = if (activated) 6.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "cardGridSurfaceElevation"
    )
    val handles = if (selected) {
        Modifier.selectionHandles(color = MaterialTheme.colorScheme.primary)
    } else {
        Modifier
    }

    Card(
        modifier = modifier.then(handles),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = cardBackground?.invoke(Modifier) ?: Modifier,
            content = content
        )
    }
}

/**
 * Consumes all pointer events falling within itself:
 * children handle first; unconsumed events get marked consumed here,
 * so the gesture stops here while bubbling up and never reaches ancestors.
 */
private fun Modifier.gestureGuard(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false).consume()
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
            if (event.changes.none { it.pressed }) return@awaitEachGesture
        }
    }
}

/**
 * Shows grid lines during drag/resize: centered on the snapped preview rect,
 * the lines fade cell by cell with distance, converging like a glow around the card.
 */
@Composable
private fun CardGridGlowEffect(state: CardGridState, modifier: Modifier = Modifier) {
    val preview = state.dragPreview ?: return
    val previewRect = state.rectFor(preview)
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val cell = state.cellPx
    val columns = state.geometry.columns
    Canvas(modifier = modifier.zIndex(0.1f)) {
        // Highlight the snapped drop spot
        drawRoundRect(
            color = primary.copy(alpha = 0.08f),
            topLeft = previewRect.topLeft,
            size = previewRect.size,
            cornerRadius = CornerRadius(16.dp.toPx())
        )

        val fadePx = GridFadeExtent.toPx()
        val startCol = floor((previewRect.left - fadePx) / cell).toInt().coerceAtLeast(0)
        val endCol = ceil((previewRect.right + fadePx) / cell).toInt().coerceAtMost(columns)
        val startRow = floor((previewRect.top - fadePx) / cell).toInt().coerceAtLeast(0)
        val endRow = ceil((previewRect.bottom + fadePx) / cell).toInt()

        // The farther a line segment is from the preview rect, the lower its opacity (smoothstep falloff)
        fun segmentAlpha(seg: Rect): Float {
            val dx = maxOf(0f, previewRect.left - seg.right, seg.left - previewRect.right)
            val dy = maxOf(0f, previewRect.top - seg.bottom, seg.top - previewRect.bottom)
            val distance = sqrt(dx * dx + dy * dy)
            if (distance >= fadePx) return 0f
            val t = 1f - distance / fadePx
            return GridLineMaxAlpha * t * t * (3f - 2f * t)
        }

        val strokeWidth = 1.dp.toPx()
        for (row in startRow..endRow) {
            val y = row * cell
            for (col in startCol..endCol) {
                val alpha = segmentAlpha(Rect(col * cell, y, (col + 1) * cell, y))
                if (alpha > 0f) {
                    drawLine(
                        color = onSurface.copy(alpha = alpha),
                        start = Offset(col * cell, y),
                        end = Offset((col + 1) * cell, y),
                        strokeWidth = strokeWidth
                    )
                }
            }
        }
        for (col in startCol..endCol) {
            val x = col * cell
            for (row in startRow..endRow) {
                val alpha = segmentAlpha(Rect(x, row * cell, x, (row + 1) * cell))
                if (alpha > 0f) {
                    drawLine(
                        color = onSurface.copy(alpha = alpha),
                        start = Offset(x, row * cell),
                        end = Offset(x, (row + 1) * cell),
                        strokeWidth = strokeWidth
                    )
                }
            }
        }
    }
}

/**
 * When a node is selected in adjust mode, draw solid handles at the center of each bounding-box edge
 */
@SuppressLint("ModifierNodeInspectableProperties")
private data class SelectionHandlesElement(
    val color: Color,
    val handleLength: Dp,
    val handleThickness: Dp
) : ModifierNodeElement<SelectionHandlesNode>() {
    override fun create() = SelectionHandlesNode(color, handleLength, handleThickness)

    override fun update(node: SelectionHandlesNode) {
        node.color = color
        node.handleLength = handleLength
        node.handleThickness = handleThickness
    }
}

private class SelectionHandlesNode(
    var color: Color,
    var handleLength: Dp,
    var handleThickness: Dp
) : DrawModifierNode, Modifier.Node() {
    override fun ContentDrawScope.draw() {
        drawContent()

        fun drawPill(center: Offset, wide: Boolean) {
            val pillWidth = if (wide) handleLength.toPx() else handleThickness.toPx()
            val pillHeight = if (wide) handleThickness.toPx() else handleLength.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(center.x - pillWidth / 2, center.y - pillHeight / 2),
                size = Size(pillWidth, pillHeight),
                cornerRadius = CornerRadius(handleThickness.toPx() / 2)
            )
        }

        drawPill(center = Offset(0f, size.height / 2), wide = false)
        drawPill(center = Offset(size.width, size.height / 2), wide = false)
        drawPill(center = Offset(size.width / 2, 0f), wide = true)
        drawPill(center = Offset(size.width / 2, size.height), wide = true)
    }
}

/**
 * Adjust-mode selection handles, drawn at the center of each bounding-box edge
 */
private fun Modifier.selectionHandles(
    color: Color,
    handleLength: Dp = 16.dp,
    handleThickness: Dp = 5.dp,
): Modifier = then(SelectionHandlesElement(color, handleLength, handleThickness))

/** Modifier that positions and sizes by a grid-coordinate rect, re-measuring when the rect changes */
private fun Modifier.cardBounds(rectProvider: () -> Rect): Modifier = this
    .absoluteOffset {
        val rect = rectProvider()
        IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
    }
    .layout { measurable, constraints ->
        val rect = rectProvider()
        val width = rect.width.roundToInt().coerceAtLeast(1)
        val height = rect.height.roundToInt().coerceAtLeast(1)
        val placeable = measurable.measure(
            constraints.copy(
                minWidth = width,
                maxWidth = width,
                minHeight = height,
                maxHeight = height
            )
        )
        layout(width, height) { placeable.place(0, 0) }
    }

/**
 * Waits for a long-press: aborts if the pointer moves beyond [slopPx] or lifts in time (returns false,
 * leaving events unconsumed so scrolling continues); returns true once the long-press holds.
 */
private suspend fun AwaitPointerEventScope.awaitLongPressOrAbort(
    pointerId: PointerId,
    startPosition: Offset,
    slopPx: Float,
    timeoutMillis: Long
): Boolean {
    var longPressed = false
    try {
        withTimeout(timeoutMillis) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                if (!change.pressed) return@withTimeout
                if ((change.position - startPosition).getDistance() > slopPx) return@withTimeout
            }
        }
    } catch (_: PointerEventTimeoutCancellationException) {
        longPressed = true
    }
    return longPressed
}

/**
 * Waits for the pointer to lift without exceeding [slopPx] (a tap succeeds, returning true);
 * if movement exceeds the limit, returns false with events unconsumed and scrolling unaffected.
 */
private suspend fun AwaitPointerEventScope.awaitUpWithoutSlop(
    pointerId: PointerId,
    startPosition: Offset,
    slopPx: Float
): Boolean {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
        if ((change.position - startPosition).getDistance() > slopPx) return false
        if (!change.pressed) return true
    }
}

/**
 * Session drag loop: consumes every pointer event and dispatches grid coordinates;
 * on release it calls [onUp] (whether meaningful movement happened); an interrupted session cancels the computation.
 */
private suspend fun AwaitPointerEventScope.runSession(
    pointerId: PointerId,
    startPosition: Offset,
    slopPx: Float,
    state: CardGridState,
    dispatch: (Offset) -> Unit,
    onUp: (moved: Boolean) -> Unit
) {
    try {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
            if (!change.pressed) {
                event.changes.forEach { it.consume() }
                onUp((change.position - startPosition).getDistance() > slopPx)
                return
            }
            dispatch(change.position)
            event.changes.forEach { it.consume() }
        }
    } finally {
        if (state.hasSession) state.onCardDragCancel()
    }
}
