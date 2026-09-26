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

package com.endiq.cardgrid.state

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import com.endiq.cardgrid.engine.GridEngine
import com.endiq.cardgrid.model.CardInteraction
import com.endiq.cardgrid.model.CardLimits
import com.endiq.cardgrid.model.CardRect
import com.endiq.cardgrid.model.CardSpacing
import com.endiq.cardgrid.model.CardState
import com.endiq.cardgrid.model.CardType
import com.endiq.cardgrid.model.GridGeometry
import com.endiq.cardgrid.model.MIN_GRID_COLUMNS
import com.endiq.cardgrid.model.ResizeEdge
import com.endiq.cardgrid.model.computeGridGeometry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** Settled grid card */
data class GridCard(
    val id: String,
    val type: CardType,
    val layout: CardRect,
    /** Span conversion basis (the span and column count from the user's last settle); spans convert losslessly from this basis when the window column count changes */
    val reflowBase: ReflowBase? = null
)

/** Repack basis of a card span */
data class ReflowBase(
    val width: Int,
    val height: Int,
    val columns: Int
)

/** A persisted card layout waiting to be seeded; [typeId] must exist in the seeded type table */
data class CardSeed(
    val id: String,
    val typeId: String,
    val layout: CardRect
)

/**
 * State holder of the card grid:
 * holds all layout-session state; every pointer coordinate uses the grid content coordinate system
 * (origin at the grid area's top-left, in pixels); hit testing and session computation run in this system,
 * all layout computation is delegated to [GridEngine], and per-card [Animatable]s drive spring-animated render rects.
 */
@Stable
class CardGridState internal constructor(
    private val scope: CoroutineScope
) {
    /** Drag/resize session */
    private data class AdjustSession(
        val card: GridCard,
        val grabOffset: Offset,
        val mode: Mode
    ) {
        sealed interface Mode {
            data object Move : Mode
            data class Resize(val edge: ResizeEdge) : Mode
        }
    }

    /** Grid geometry (always even columns, square cells) */
    var geometry by mutableStateOf(GridGeometry(MIN_GRID_COLUMNS, 20f))
        private set

    /** Cell edge length (px) */
    var cellPx by mutableFloatStateOf(20f)
        private set

    /** Screen density (dp → px), updated with the geometry */
    private var densityFactor by mutableFloatStateOf(1f)

    /** Card rect inset within a cell (px) */
    var cardInsetPx by mutableIntStateOf(2)
        private set

    /** All settled cards, in insertion order */
    var cards by mutableStateOf<List<GridCard>>(emptyList())
        private set

    /** Id of the card in adjust mode (long-press selected) */
    var adjustingCardId by mutableStateOf<String?>(null)
        private set

    /** Whether adjust mode is active */
    val isAdjusting: Boolean get() = adjustingCardId != null

    /** Snapped preview layout (ghost position); non-null only during a session */
    var dragPreview by mutableStateOf<CardRect?>(null)
        private set

    /** Raw rect that follows the finger; non-null only during a session */
    var dragRawRect by mutableStateOf<Rect?>(null)
        private set

    /** Pointer position in grid content coordinates, used by the grid glow and auto-scroll */
    var pointerPosition by mutableStateOf<Offset?>(null)
        private set

    /** Offset of the grid area in window coordinates (changes with scrolling) */
    internal var areaOffsetInRoot by mutableStateOf(Offset.Zero)
        private set

    /** Top edge of the grid viewport in window coordinates (window coordinates ignore scrolling); 0 when not yet reported */
    var viewportTopPx by mutableFloatStateOf(0f)
        private set

    /** Grid viewport height (px); 0 when not yet reported */
    var viewportHeightPx by mutableFloatStateOf(0f)
        private set

    /** Layout position callback of the grid area */
    fun onAreaPositioned(offsetInRoot: Offset) {
        areaOffsetInRoot = offsetInRoot
    }

    /** Layout position callback of the grid viewport (window coordinates), used for toolbar placement and auto-scroll */
    fun onViewportPositioned(topPx: Float, heightPx: Float) {
        viewportTopPx = topPx
        viewportHeightPx = heightPx
    }

    /**
     * The finger's anchor in window coordinates (window coordinates ignore scrolling);
     * the pointer's grid coordinate always derives wholesale from the anchor and the grid area's current offset,
     * avoiding feedback oscillation between scroll increments and event coordinates.
     */
    private var pointerAnchorInRoot: Offset? = null

    /** Cards that yielded (id -> yielded layout): updated live as previews during the session and persisted once the drop is committed */
    var displaced by mutableStateOf<Map<String, CardRect>>(emptyMap())
        private set

    /** Callback after a layout settle (used for persistence) */
    var onLayoutCommitted: () -> Unit = {}

    /** Callback after a card is removed (used to sync external data linked to that card) */
    var onCardRemoved: (cardId: String) -> Unit = {}

    /** Persists the layout settle; settles made before geometry is ready are transient filler and must not overwrite valid persisted data */
    private fun commitLayout() {
        if (geometryReady) onLayoutCommitted()
    }

    /** Quick spatial animation (yielding during a drag) */
    internal var fastSpec: AnimationSpec<Rect> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Default spatial animation (settle, compaction) */
    internal var defaultSpec: AnimationSpec<Rect> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    private var session by mutableStateOf<AdjustSession?>(null)

    /** Whether a drag/resize session is in progress */
    val hasSession: Boolean get() = session != null

    /** Persisted cards waiting to be seeded; take effect once grid geometry is ready */
    private var pendingSeeds: List<CardSeed>? = null
    private var pendingColumns: Int = 0

    /** Registered card type table */
    private var typeById: Map<String, CardType> = emptyMap()

    /** Whether grid geometry has been computed from a real container width */
    private var geometryReady = false

    private val animators = mutableMapOf<String, Animatable<Rect, AnimationVector4D>>()

    // ---------- Seeding ----------

    /**
     * Seeds card types and persisted card layouts:
     * The type table applies immediately; layouts are repaired (matching/unknown column count) or repacked in reading order (mismatched) once geometry is ready.
     * Duplicate ids keep only the first occurrence; cards of unknown type are dropped.
     */
    fun seed(types: List<CardType>, seeds: List<CardSeed>, storedColumns: Int) {
        typeById = types.associateBy { it.typeId }
        val distinct = seeds.distinctBy { it.id }.filter { it.typeId in typeById }
        if (distinct.isEmpty()) return

        pendingSeeds = distinct
        pendingColumns = storedColumns
        if (geometryReady) materializePending(newColumns = geometry.columns)
    }

    /**
     * @return whether any pending cards were seeded
     */
    private fun materializePending(newColumns: Int): Boolean {
        val pending = pendingSeeds ?: return false
        pendingSeeds = null
        val typeIdById = pending.associate { it.id to it.typeId }
        val layouts = if (pendingColumns == newColumns || pendingColumns <= 0) {
            GridEngine.validate(pending.map { it.layout }, newColumns) { rect ->
                typeById[typeIdById[rect.id]]?.limits ?: CardLimits.DEFAULT
            }
        } else {
            GridEngine.reflow(
                cards = pending.map { it.layout },
                oldColumns = pendingColumns,
                columns = newColumns
            ) { rect ->
                typeById[typeIdById[rect.id]]?.limits ?: CardLimits.DEFAULT
            }
        }.associateBy { it.id }

        val materialized = pending.mapNotNull { seed ->
            typeById[seed.typeId]?.let { type ->
                val layout = layouts.getValue(seed.id)
                //The conversion basis uses the persisted span and stored column count; when the stored count is unknown, the current geometry is the basis
                val base = if (pendingColumns > 0) {
                    ReflowBase(seed.layout.width, seed.layout.height, pendingColumns)
                } else {
                    ReflowBase(layout.width, layout.height, newColumns)
                }
                GridCard(id = seed.id, type = type, layout = layout, reflowBase = base)
            }
        }
        //Seeded layouts take the persisted data as truth, replacing same-id cards added as filler before geometry was ready, avoiding duplicates
        val seededIds = materialized.mapTo(mutableSetOf()) { it.id }
        cards = cards.filterNot { it.id in seededIds } + materialized
        materialized.forEach { animateTo(it, effectiveLayout(it), defaultSpec) }
        return true
    }

    // ---------- Geometry ----------

    /** Updates the grid from the container width; a column-count change triggers a full repack */
    fun updateGeometry(widthDp: Float, density: Density) {
        if (widthDp <= 0f) return // ignore invalid widths so a transient zero width cannot repack the grid into a degenerate geometry
        val wasReady = geometryReady
        val oldColumns = geometry.columns
        val newGeometry = computeGridGeometry(widthDp)
        cellPx = newGeometry.cellSize * density.density
        cardInsetPx = with(density) {
            CardSpacing.roundToPx()
        }
        densityFactor = density.density
        geometry = newGeometry
        geometryReady = true
        //Cards just settled by seeding were never laid out under the old geometry; repacking right away would scale their sizes by the wrong ratio
        val materialized = materializePending(newColumns = newGeometry.columns)
        //Only treat it as a column change when the old geometry came from a real measurement; the initial default geometry is a placeholder and never repacks
        if (!materialized && wasReady && newGeometry.columns != oldColumns && cards.isNotEmpty()) {
            reflowTo(newColumns = newGeometry.columns, oldColumns = oldColumns)
        }
    }

    /** Render rect (px) of a card layout, anchored at the grid's top-left */
    fun rectFor(layout: CardRect): Rect = Rect(
        left = layout.x * cellPx + cardInsetPx,
        top = layout.y * cellPx + cardInsetPx,
        right = layout.right * cellPx - cardInsetPx,
        bottom = layout.bottom * cellPx - cardInsetPx
    )

    /** Grid content height (px), including one spare row for pushing at the tail */
    fun gridHeightPx(): Float {
        val rows = max(
            GridEngine.totalRows(cards.map { it.layout }),
            dragPreview?.bottom ?: 0
        )
        return (rows + 1) * cellPx
    }

    // ---------- Card management ----------

    /** Appends a card, landing at the topmost-leftmost free spot */
    fun addCard(type: CardType, id: String = UUID.randomUUID().toString()): GridCard? {
        if (cards.any { it.id == id }) return null
        val lim = type.limits.clampedFor(geometry.columns)
        val width = type.defaultSpan.x.coerceIn(lim.minWidth, lim.maxWidth)
        val height = type.defaultSpan.y.coerceIn(lim.minHeight, lim.maxHeight)
        val slot = GridEngine.findTopLeftFreeSlot(
            width = width,
            height = height,
            columns = geometry.columns,
            obstacles = layouts()
        )
        val card = GridCard(
            id = id,
            type = type,
            layout = CardRect(id = id, x = slot.x, y = slot.y, width = width, height = height),
            reflowBase = ReflowBase(width, height, geometry.columns)
        )
        cards = cards + card
        commitLayout()
        return card
    }

    /** Removes a card and compacts the remaining layout */
    fun removeCard(id: String) {
        val removed = cards.firstOrNull { it.id == id } ?: return
        val remaining = cards.filterNot { it.id == id }
        val compacted = GridEngine.compact(remaining.map { it.layout }).associateBy { it.id }
        cards = remaining.map { card -> card.copy(layout = compacted.getValue(card.id)) }
        animators.remove(removed.id)
        if (adjustingCardId == id) adjustingCardId = null
        if (session?.card?.id == id) endSession()
        cards.forEach { card -> animateTo(card, effectiveLayout(card), defaultSpec) }
        commitLayout()
        onCardRemoved(id)
    }

    // ---------- Hit testing (grid content coordinates) ----------

    /** Hit-tests cards topmost-first (adjust-mode card first, then later-in-list on top, matching the draw order) */
    fun cardAt(position: Offset): GridCard? {
        val ordered = cards
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<GridCard>> { (_, card) ->
                    if (card.id == adjustingCardId) 1 else 0
                }.thenByDescending { it.index }
            )
        return ordered.firstOrNull { (_, card) ->
            rectFor(effectiveLayout(card)).contains(position)
        }?.value
    }

    /**
     * Hit-tests the resize handle hot zones of the adjust-mode card,
     * @return the card and the hit edge, or null on a miss
     */
    fun resizeEdgeAt(position: Offset): Pair<GridCard, ResizeEdge>? {
        val card = adjustingCardId?.let { id -> cards.firstOrNull { it.id == id } } ?: return null
        val rect = renderRectOf(card)
        val hitRadiusPx = EDGE_HIT_RADIUS_DP * densityFactor
        return ResizeEdge.entries
            .map { edge -> edge to edgeCenter(edge, rect) }
            .map { (edge, center) -> edge to (position - center).getDistance() }
            .filter { (_, distance) -> distance <= hitRadiusPx }
            .minByOrNull { (_, distance) -> distance }
            ?.let { (edge, _) -> card to edge }
    }

    private fun edgeCenter(edge: ResizeEdge, rect: Rect): Offset = when (edge) {
        ResizeEdge.Start -> Offset(rect.left, rect.center.y)
        ResizeEdge.Top -> Offset(rect.center.x, rect.top)
        ResizeEdge.End -> Offset(rect.right, rect.center.y)
        ResizeEdge.Bottom -> Offset(rect.center.x, rect.bottom)
    }

    // ---------- Adjust session: drag ----------

    /** Long-press succeeded: the card starts dragging (and enters adjust mode too) */
    fun onCardDragStart(card: GridCard, pointer: Offset) {
        val layout = effectiveLayout(card)
        session = AdjustSession(
            card = card,
            grabOffset = pointer - rectFor(layout).topLeft,
            mode = AdjustSession.Mode.Move
        )
        adjustingCardId = card.id
        pointerAnchorInRoot = areaOffsetInRoot + pointer
        dragRawRect = rectFor(layout)
        pointerPosition = pointer
        applyPreview(card.layout, emptyMap())
    }

    fun onCardDrag(pointer: Offset) {
        val current = session ?: return
        check(current.mode is AdjustSession.Mode.Move) { "Current session is not a drag session" }

        pointerAnchorInRoot = areaOffsetInRoot + pointer
        pointerPosition = pointer
        val size = rectFor(current.card.layout).size
        val topLeft = Offset(
            pointer.x - current.grabOffset.x,
            pointer.y - current.grabOffset.y
        )
        dragRawRect = Rect(offset = topLeft, size = size)
        // Snap to cells, clamped horizontally inside the grid
        // Covered cards run a live directional yield preview based on the finger position
        val target = IntOffset(
            (topLeft.x / cellPx).roundToInt().coerceIn(0, geometry.columns - current.card.layout.width),
            (topLeft.y / cellPx).roundToInt().coerceAtLeast(0)
        )
        val preview = current.card.layout.positionAt(target)
        applyPreview(
            preview,
            GridEngine.resolveDisplacements(
                moving = preview,
                columns = geometry.columns,
                cards = layouts(),
                pointer = IntOffset(
                    (pointer.x / cellPx).roundToInt(),
                    (pointer.y / cellPx).roundToInt()
                )
            )
        )
    }

    /** On release: settles the drop and the yielded cards, compacts and persists */
    fun onCardDragEnd() {
        val current = session ?: return
        commit(previewLayoutOf(current))
    }

    /** Drag cancelled: everything returns to the pre-session state */
    fun onCardDragCancel() {
        cancelSession()
    }

    // ---------- Adjust session: resize ----------

    /** Starts dragging an edge handle, adjusting the span in one direction */
    fun onResizeStart(card: GridCard, edge: ResizeEdge, pointer: Offset) {
        session = AdjustSession(
            card = card,
            grabOffset = pointer,
            mode = AdjustSession.Mode.Resize(edge)
        )
        adjustingCardId = card.id
        pointerAnchorInRoot = areaOffsetInRoot + pointer
        dragRawRect = rectFor(card.layout)
        pointerPosition = pointer
        applyPreview(card.layout, emptyMap())
    }

    fun onResize(pointer: Offset) {
        val current = session ?: return
        val edge = (current.mode as? AdjustSession.Mode.Resize)?.edge ?: return
        pointerAnchorInRoot = areaOffsetInRoot + pointer
        pointerPosition = pointer
        val result = GridEngine.resolveResize(
            current = current.card.layout,
            edge = edge,
            pointer = IntOffset(
                (pointer.x / cellPx).roundToInt(),
                (pointer.y / cellPx).roundToInt()
            ),
            columns = geometry.columns,
            limits = current.card.type.limits,
            obstacles = layouts().filterNot { it.id == current.card.id }
        )
        dragRawRect = rawRectForResize(current.card, edge, pointer, result.layout)
        applyPreview(result.layout, result.pushed)
    }

    fun onResizeEnd() {
        val current = session ?: return
        commit(previewLayoutOf(current))
    }

    fun onResizeCancel() {
        cancelSession()
    }

    /**
     * Recomputes the pointer position after an auto-scroll:
     * the finger's window anchor stays put while scrolling shifts the grid area's window offset,
     * so the pointer's grid coordinate is recomputed from both wholesale (no incremental drift).
     */
    internal fun onAutoScroll() {
        val anchor = pointerAnchorInRoot ?: return
        val local = anchor - areaOffsetInRoot
        when (session?.mode) {
            is AdjustSession.Mode.Move -> onCardDrag(local)
            is AdjustSession.Mode.Resize -> onResize(local)
            null -> Unit
        }
    }

    // ---------- Adjust mode ----------

    /** Current interaction state of the given card */
    fun interactionOf(cardId: String): CardInteraction {
        val current = session
        return when {
            current?.card?.id == cardId && current.mode is AdjustSession.Mode.Resize -> CardInteraction.Resizing
            current?.card?.id == cardId && current.mode is AdjustSession.Mode.Move -> CardInteraction.Dragging
            adjustingCardId == cardId -> CardInteraction.Adjusting
            else -> CardInteraction.Idle
        }
    }

    /** Whether the session is acting on the given card (the follow-finger rect replaces the animated rect when rendering) */
    fun isSessionCard(cardId: String): Boolean = session?.card?.id == cardId

    /** Exits adjust mode (tapping empty space or pressing Back) */
    fun exitAdjusting() {
        if (session != null) cancelSession()
        adjustingCardId = null
    }

    /** The card's own state handed to its content (follows the snap preview during resize so size changes are felt live) */
    fun cardStateOf(card: GridCard): CardState {
        val layout = if (isSessionCard(card.id)) dragPreview ?: card.layout else card.layout
        return CardState(
            spanWidth = layout.width,
            spanHeight = layout.height,
            columns = geometry.columns,
            interaction = interactionOf(card.id)
        )
    }

    // ---------- Rendering ----------

    /** Render rect animator of the given card */
    internal fun animatorFor(card: GridCard): Animatable<Rect, AnimationVector4D> =
        animators.getOrPut(card.id) {
            Animatable(rectFor(card.layout), Rect.VectorConverter)
        }

    /** A card's current render rect (session cards follow the finger, others use the animated value) */
    internal fun renderRectOf(card: GridCard): Rect {
        if (isSessionCard(card.id)) return dragRawRect ?: animatorFor(card).value
        return animatorFor(card).value
    }

    private fun animateTo(card: GridCard, layout: CardRect, spec: AnimationSpec<Rect>) {
        val animatable = animatorFor(card)
        val target = rectFor(layout)
        scope.launch { animatable.animateTo(target, spec) }
    }

    private fun animateAll() {
        cards.forEach { animateTo(it, effectiveLayout(it), defaultSpec) }
    }

    /**
     * Settling the session card: the animator first snaps to the follow-finger rect at release, then animates to the final layout,
     * avoiding a visual jump when switching from follow rendering back to animated rendering.
     */
    private fun settleSessionCard(card: GridCard, rawRect: Rect?, target: CardRect) {
        val animatable = animatorFor(card)
        scope.launch {
            rawRect?.let { animatable.snapTo(it) }
            animatable.animateTo(rectFor(target), defaultSpec)
        }
    }

    // ---------- Internals: computation ----------

    internal fun effectiveLayout(card: GridCard): CardRect =
        displaced[card.id] ?: card.layout

    private fun previewLayoutOf(session: AdjustSession): CardRect =
        dragPreview ?: session.card.layout

    private fun layouts(): List<CardRect> = cards.map { it.layout }

    /** Applies a new snap preview: animates cards entering and leaving the yielded set */
    private fun applyPreview(preview: CardRect, displacements: Map<String, CardRect>) {
        if (dragPreview == preview && displaced.keys == displacements.keys) return
        dragPreview = preview
        val affected = displaced.keys + displacements.keys
        displaced = displacements
        affected.forEach { id ->
            cards.firstOrNull { it.id == id }?.let { card ->
                animateTo(card, displacements[id] ?: card.layout, fastSpec)
            }
        }
    }

    /** Commits the session result: reuses the session's yield computation, compacts, and persists */
    private fun commit(target: CardRect) {
        val current = session ?: return
        // endSession clears the follow-finger rect, so it must be captured first for the animator snap
        val rawRect = dragRawRect
        val settled = displaced
        cards = cards.map { card ->
            when {
                card.id == current.card.id -> card.copy(layout = target)
                else -> settled[card.id]?.let { card.copy(layout = it) } ?: card
            }
        }
        cards = compactCards(cards)
        //Compaction may change the session card's final resting spot; the final layout from the list is authoritative
        val finalLayout = cards.firstOrNull { it.id == current.card.id }?.layout ?: target
        //The user-settled span becomes the new conversion basis
        cards = cards.map { card ->
            if (card.id == current.card.id) {
                card.copy(reflowBase = ReflowBase(card.layout.width, card.layout.height, geometry.columns))
            } else {
                card
            }
        }
        endSession()
        settleSessionCard(current.card, rawRect, finalLayout)
        cards.filterNot { it.id == current.card.id }
            .forEach { animateTo(it, effectiveLayout(it), defaultSpec) }
        commitLayout()
    }

    /** Cancels the session: yielded cards and the session card spring back with no computation */
    private fun cancelSession() {
        val current = session ?: return
        val rawRect = dragRawRect
        val affected = displaced.keys
        endSession()
        affected.forEach { id ->
            cards.firstOrNull { it.id == id }?.let { animateTo(it, it.layout, defaultSpec) }
        }
        settleSessionCard(current.card, rawRect, current.card.layout)
    }

    private fun endSession() {
        session = null
        dragPreview = null
        dragRawRect = null
        pointerPosition = null
        displaced = emptyMap()
    }

    /** Vertically compacts all cards, keeping the instance mapping */
    private fun compactCards(list: List<GridCard>): List<GridCard> {
        val compacted = GridEngine.compact(list.map { it.layout }).associateBy { it.id }
        return list.map { card -> card.copy(layout = compacted.getValue(card.id)) }
    }

    private fun reflowTo(newColumns: Int, oldColumns: Int) {
        //Spans convert from the basis in one shot, so stepwise rounding cannot swallow small growth into a fixed span
        val respanned = cards.map { card ->
            val base = card.reflowBase
            if (base == null) {
                card.layout
            } else {
                val lim = card.type.limits.clampedFor(newColumns)
                card.layout.copy(
                    width = (base.width * newColumns.toFloat() / base.columns).roundToInt()
                        .let(lim::clampWidth),
                    height = (base.height * newColumns.toFloat() / base.columns).roundToInt()
                        .let(lim::clampHeight)
                )
            }
        }
        //Spans are already converted; passing the same oldColumns makes the repack a pure position packing
        val layouts = GridEngine.reflow(
            cards = respanned,
            oldColumns = newColumns,
            columns = newColumns
        ) { rect -> cards.firstOrNull { it.id == rect.id }?.type?.limits ?: CardLimits.DEFAULT }
            .associateBy { it.id }
        cards = cards.map { card -> card.copy(layout = layouts.getValue(card.id)) }
        endSession()
        adjustingCardId = null
        animateAll()
        commitLayout()
    }

    // ---------- Internals: follow-finger rect ----------

    /** Computes the raw follow-finger rect while resizing from the pointer position; the dragged edge is clamped between the min span and the push-computed span */
    private fun rawRectForResize(
        card: GridCard,
        edge: ResizeEdge,
        pointer: Offset,
        settled: CardRect
    ): Rect {
        val layout = card.layout
        val lim = card.type.limits.clampedFor(geometry.columns)
        val minSpan = if (edge == ResizeEdge.Start || edge == ResizeEdge.End) lim.minWidth else lim.minHeight
        val maxSpan = when (edge) {
            ResizeEdge.Start, ResizeEdge.End -> settled.width
            ResizeEdge.Top, ResizeEdge.Bottom -> settled.height
        }
        val range = minSpan..maxSpan
        val left = layout.x * cellPx + cardInsetPx
        val top = layout.y * cellPx + cardInsetPx
        val right = layout.right * cellPx - cardInsetPx
        val bottom = layout.bottom * cellPx - cardInsetPx
        // Pixel position of the dragged edge for each span
        fun edgePxStart(span: Int) = (layout.right - span) * cellPx + cardInsetPx
        fun edgePxTop(span: Int) = (layout.bottom - span) * cellPx + cardInsetPx
        fun edgePxEnd(span: Int) = (layout.x + span) * cellPx - cardInsetPx
        fun edgePxBottom(span: Int) = (layout.y + span) * cellPx - cardInsetPx
        return when (edge) {
            ResizeEdge.End -> Rect(left, top, pointer.x.coerceIn(edgePxEnd(range.first), edgePxEnd(range.last)), bottom)
            ResizeEdge.Start -> Rect(pointer.x.coerceIn(edgePxStart(range.last), edgePxStart(range.first)), top, right, bottom)
            ResizeEdge.Bottom -> Rect(left, top, right, pointer.y.coerceIn(edgePxBottom(range.first), edgePxBottom(range.last)))
            ResizeEdge.Top -> Rect(left, pointer.y.coerceIn(edgePxTop(range.last), edgePxTop(range.first)), right, bottom)
        }
    }

    companion object {
        /** Resize handle hot-zone radius (dp) */
        private const val EDGE_HIT_RADIUS_DP = 24f
    }
}

/** Creates a [CardGridState] bound to the composition lifecycle */
@Composable
fun rememberCardGridState(): CardGridState {
    val scope = rememberCoroutineScope()
    return remember { CardGridState(scope) }
}
