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

package com.endiq.cardgrid.engine

import androidx.compose.ui.unit.IntOffset
import com.endiq.cardgrid.model.CardLimits
import com.endiq.cardgrid.model.CardRect
import com.endiq.cardgrid.model.ResizeEdge
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Card grid layout engine: every computation is a pure function with no side effects.
 *
 * Resizing uses push-box semantics: blocking cards are pushed away along the dragged edge as a chain, and when the chain cannot move, the span shrinks cell by cell;
 * Dragging uses squeeze-yield semantics: each covered card migrates to the nearest free spot without cascading onto other cards;
 * The layout stays vertically compacted at all times.
 */
object GridEngine {

    /** The dominant axis of a pushing motion */
    internal enum class PushAxis { Horizontal, Vertical }

    /** Resize result: the resized card's layout plus the pushed cards (id -> new layout) */
    data class ResizeResult(
        val layout: CardRect,
        val pushed: Map<String, CardRect>
    )

    /** Whether a card lies fully inside the grid bounds (vertically unbounded) */
    fun isInGrid(rect: CardRect, columns: Int): Boolean =
        rect.x >= 0 && rect.y >= 0 && rect.right <= columns

    /** Whether any cards in the set overlap */
    fun hasOverlap(cards: List<CardRect>): Boolean {
        for (i in cards.indices) {
            for (j in i + 1 until cards.size) {
                if (cards[i].intersects(cards[j])) return true
            }
        }
        return false
    }

    /** Total rows currently occupied by the grid (0 when empty) */
    fun totalRows(cards: List<CardRect>): Int = cards.maxOfOrNull { it.bottom } ?: 0

    /**
     * Searches for the free spot closest to [origin] that fits a [width]×[height] card.
     *
     * Closeness is the Euclidean distance between card centers; ties prefer the higher, then lefter spot;
     * [obstacles] is the set of rectangles to avoid; the grid is vertically unbounded,
     * and the search stops at the deepest obstacle bottom (the row below is certainly free, so a solution always exists).
     *
     * @return the nearest free spot, or null if the size cannot fit the grid width
     */
    fun findNearestFreeSlot(
        width: Int,
        height: Int,
        origin: IntOffset,
        columns: Int,
        obstacles: List<CardRect>
    ): IntOffset? {
        if (width <= 0 || height <= 0 || width > columns) return null
        val maxRow = obstacles.maxOfOrNull { it.bottom } ?: 0
        val originCenterX = origin.x + width / 2f
        val originCenterY = origin.y + height / 2f
        var best: IntOffset? = null
        var bestDistance = Float.MAX_VALUE
        for (cy in 0..maxRow) {
            for (cx in 0..columns - width) {
                val candidate = CardRect("", cx, cy, width, height)
                if (obstacles.any { it.intersects(candidate) }) continue
                val dx = cx + width / 2f - originCenterX
                val dy = cy + height / 2f - originCenterY
                val distance = dx * dx + dy * dy
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = IntOffset(cx, cy)
                }
            }
        }
        return best
    }

    /**
     * Drag computation: [moving] is the preview (or drop position) of the card being dragged,
     * overlapping cards are relocated one by one in reading order without changing size,
     * and cards not directly overlapped are never cascaded onto
     * @return the relocated cards (id -> new layout), excluding unaffected ones
     */
    fun resolveDisplacements(
        moving: CardRect,
        columns: Int,
        cards: List<CardRect>,
        pointer: IntOffset? = null
    ): Map<String, CardRect> {
        val displaced = cards
            .filter { it.id != moving.id && it.intersects(moving) }
            .sortedWith(readingOrder())
        val occupied = mutableListOf<CardRect>()
        occupied.add(moving)
        occupied.addAll(cards.filter { it.id != moving.id && !it.intersects(moving) })
        val result = mutableMapOf<String, CardRect>()
        for (card in displaced) {
            val nearest: () -> IntOffset? = {
                findNearestFreeSlot(
                    width = card.width,
                    height = card.height,
                    origin = IntOffset(card.x, card.y),
                    columns = columns,
                    obstacles = occupied
                )
            }
            val slot = pointer?.let {
                findDirectionalFreeSlot(card, displacementDirection(it, card), columns, occupied) ?: nearest()
            } ?: nearest() ?: continue
            val relocated = card.positionAt(slot)
            occupied.add(relocated)
            result[card.id] = relocated
        }
        return result
    }

    /**
     * Decides the yield direction from the pointer's dominant direction relative to the covered card's center
     * Whichever side of the card the pointer covers, the card yields along that axis away from the pointer
     */
    internal fun displacementDirection(pointer: IntOffset, card: CardRect): IntOffset {
        val dx = pointer.x - (card.x + card.width / 2f)
        val dy = pointer.y - (card.y + card.height / 2f)
        return if (abs(dx) >= abs(dy)) {
            IntOffset(if (dx > 0) -1 else 1, 0)
        } else {
            IntOffset(0, if (dy > 0) -1 else 1)
        }
    }

    /**
     * Scans cell by cell from [card]'s current position along [direction] (unit vector) for the first
     * spot that does not overlap [obstacles]; the coordinate perpendicular to the motion axis stays fixed,
     * the grid clamps horizontally and is unbounded downward.
     * @return the yield spot, or null if none exists in that direction
     */
    fun findDirectionalFreeSlot(
        card: CardRect,
        direction: IntOffset,
        columns: Int,
        obstacles: List<CardRect>
    ): IntOffset? {
        var x = card.x
        var y = card.y
        while (true) {
            x += direction.x
            y += direction.y
            if (direction.x != 0 && (x < 0 || x + card.width > columns)) return null
            if (direction.y < 0 && y < 0) return null
            val candidate = card.positionAt(IntOffset(x, y))
            if (obstacles.none { it.intersects(candidate) }) return IntOffset(x, y)
        }
    }

    /**
     * Resize computation: derives the resized layout from the cell under the pointer [pointer],
     * anchoring the side opposite the dragged edge [edge]; the grid edge and [limits] are hard bounds along the growth direction.
     *
     * Cards covered by the growth are pushed flush with the front along the dragged edge, pushing more cards down the chain;
     * when the chain cannot move, the span backs off cell by cell to the furthest movable position; shrinking is unaffected by pushing.
     */
    fun resolveResize(
        current: CardRect,
        edge: ResizeEdge,
        pointer: IntOffset,
        columns: Int,
        limits: CardLimits,
        obstacles: List<CardRect>
    ): ResizeResult {
        val lim = limits.clampedFor(columns)
        val horizontal = edge == ResizeEdge.Start || edge == ResizeEdge.End
        val minSpan = if (horizontal) lim.minWidth else lim.minHeight
        val maxSpan = if (horizontal) lim.maxWidth else lim.maxHeight
        // Maximum span of the dragged edge under the hard bounds, regardless of blocking cards
        val hardMax = when (edge) {
            ResizeEdge.End -> columns - current.x
            ResizeEdge.Start -> current.right
            ResizeEdge.Bottom -> Int.MAX_VALUE
            ResizeEdge.Top -> current.bottom
        }.coerceAtLeast(1)
        val upper = minOf(maxSpan, hardMax).coerceAtLeast(1)
        val span = when (edge) {
            ResizeEdge.End -> pointer.x - current.x
            ResizeEdge.Start -> current.right - pointer.x
            ResizeEdge.Bottom -> pointer.y - current.y
            ResizeEdge.Top -> current.bottom - pointer.y
        }.coerceIn(minSpan.coerceAtMost(upper), upper)

        val desired = withSpan(current, edge, span)
        if (span <= spanOf(current, edge)) return ResizeResult(desired, emptyMap())

        val axis = if (horizontal) PushAxis.Horizontal else PushAxis.Vertical
        val forward = edge == ResizeEdge.End || edge == ResizeEdge.Bottom
        var candidate = desired
        while (true) {
            val pushed = push(candidate, axis, forward, columns, obstacles)
            if (pushed != null) return ResizeResult(candidate, pushed)
            val shrunk = spanOf(candidate, edge) - 1
            if (shrunk < spanOf(current, edge)) return ResizeResult(current, emptyMap())
            candidate = withSpan(candidate, edge, shrunk)
        }
    }

    /**
     * Push solver: pushes the cards that actually overlap [mover] along the [forward] direction of [axis]
     * one after another until flush with mover's front; cards covered by the pushed positions are dragged along.
     * If any card would be pushed off the grid (sideways or past the opposite edge), the whole chain cannot move.
     *
     * @return the pushed cards (id -> new layout), or null when the chain cannot move
     */
    internal fun push(
        mover: CardRect,
        axis: PushAxis,
        forward: Boolean,
        columns: Int,
        obstacles: List<CardRect>
    ): Map<String, CardRect>? {
        val horizontal = axis == PushAxis.Horizontal

        // The side of a card facing the front
        fun leading(card: CardRect): Int = when {
            horizontal && forward -> card.x
            horizontal -> card.right
            forward -> card.y
            else -> card.bottom
        }
        fun span(card: CardRect): Int = if (horizontal) card.width else card.height
        fun placedAt(card: CardRect, front: Int): CardRect = when {
            horizontal && forward -> card.copy(x = front)
            horizontal -> card.copy(x = front - card.width)
            forward -> card.copy(y = front)
            else -> card.copy(y = front - card.height)
        }

        // The front starts at mover's advancing side and moves with the push
        var front = when {
            horizontal && forward -> mover.right
            horizontal -> mover.x
            forward -> mover.bottom
            else -> mover.y
        }
        val pushed = mutableMapOf<String, CardRect>()
        // Only cards actually overlapping mover get pushed; a pushed rectangle may cover cards outside the growth band, so the push queue grows dynamically
        val queue = obstacles
            .filter { it.id != mover.id && it.intersects(mover) }
            .toMutableList()
        while (queue.isNotEmpty()) {
            queue.sortBy { if (forward) leading(it) else -leading(it) }
            val card = queue.removeAt(0)
            // The card nearest the front already sits beyond it, and the rest are farther: pushing is done
            if (if (forward) leading(card) >= front else leading(card) <= front) break
            val placed = placedAt(card, front)
            front += if (forward) span(card) else -span(card)
            val blocked = when {
                horizontal && forward -> front > columns
                forward -> false
                else -> front < 0
            }
            if (blocked) return null
            pushed[card.id] = placed
            queue.addAll(
                obstacles.filter {
                    it.id != mover.id && it.id !in pushed && it.intersects(placed) && it !in queue
                }
            )
        }
        return pushed
    }

    private fun spanOf(rect: CardRect, edge: ResizeEdge): Int = when (edge) {
        ResizeEdge.Start, ResizeEdge.End -> rect.width
        ResizeEdge.Top, ResizeEdge.Bottom -> rect.height
    }

    private fun withSpan(rect: CardRect, edge: ResizeEdge, span: Int): CardRect = when (edge) {
        ResizeEdge.End -> rect.copy(width = span)
        ResizeEdge.Start -> rect.copy(x = rect.right - span, width = span)
        ResizeEdge.Bottom -> rect.copy(height = span)
        ResizeEdge.Top -> rect.copy(y = rect.bottom - span, height = span)
    }

    /**
     * Finds the topmost-leftmost free spot for a [width]×[height] card (greedy packing).
     */
    fun findTopLeftFreeSlot(
        width: Int,
        height: Int,
        columns: Int,
        obstacles: List<CardRect>
    ): IntOffset {
        val maxRow = obstacles.maxOfOrNull { it.bottom } ?: 0
        for (cy in 0..maxRow) {
            for (cx in 0..columns - width) {
                val candidate = CardRect("", cx, cy, width, height)
                if (obstacles.none { it.intersects(candidate) }) {
                    return IntOffset(cx, cy)
                }
            }
        }
        return IntOffset(0, maxRow)
    }

    /**
     * Vertical compaction: processed in reading order; each card, keeping its horizontal position,
     * floats up as far as possible until it hugs the grid top or rests on another card.
     * After compaction no card can move further up; horizontal gaps within and at the end of rows are kept.
     */
    fun compact(cards: List<CardRect>): List<CardRect> {
        val sorted = cards.sortedWith(readingOrder())
        val placed = mutableListOf<CardRect>()
        for (card in sorted) {
            var y = 0
            while (true) {
                val blocking = placed.firstOrNull { it.intersects(card.positionAt(IntOffset(card.x, y))) }
                if (blocking == null) break
                y = blocking.bottom
            }
            placed.add(card.positionAt(IntOffset(card.x, y)))
        }
        return placed
    }

    /**
     * Greedy repack in reading order (top-to-bottom, left-to-right),
     * used to migrate layouts after the grid width changed: card sizes are scaled by the old/new column ratio,
     * clamped by the bounds declared in [limits], then placed one by one into the topmost-leftmost free spot.
     */
    fun reflow(
        cards: List<CardRect>,
        oldColumns: Int,
        columns: Int,
        limits: (CardRect) -> CardLimits = { CardLimits.DEFAULT }
    ): List<CardRect> {
        if (cards.isEmpty()) return cards
        val scale = columns.toFloat() / oldColumns.coerceAtLeast(1)
        val placed = mutableListOf<CardRect>()
        for (card in cards.sortedWith(readingOrder())) {
            val lim = limits(card).clampedFor(columns)
            val width = (card.width * scale).roundToInt().let { lim.clampWidth(it) }
            val height = (card.height * scale).roundToInt().let { lim.clampHeight(it) }
            val slot = findTopLeftFreeSlot(width, height, columns, placed)
            placed.add(card.copy(x = slot.x, y = slot.y, width = width, height = height))
        }
        return placed
    }

    /**
     * Load validation: clamps out-of-bounds and invalid cards, resolves overlaps,
     * then runs one final vertical compaction. Cards with duplicate ids keep only the first occurrence.
     */
    fun validate(
        cards: List<CardRect>,
        columns: Int,
        limits: (CardRect) -> CardLimits = { CardLimits.DEFAULT }
    ): List<CardRect> {
        val seen = mutableSetOf<String>()
        val clamped = mutableListOf<CardRect>()
        for (card in cards) {
            if (!seen.add(card.id)) continue
            val lim = limits(card).clampedFor(columns)
            val width = lim.clampWidth(card.width.coerceAtLeast(1))
            val height = lim.clampHeight(card.height.coerceAtLeast(1))
            val x = card.x.coerceIn(0, columns - width)
            val y = card.y.coerceAtLeast(0)
            clamped.add(card.copy(x = x, y = y, width = width, height = height))
        }
        val settled = mutableListOf<CardRect>()
        for (card in clamped.sortedWith(readingOrder())) {
            val position = if (settled.any { it.intersects(card) }) {
                findNearestFreeSlot(
                    width = card.width,
                    height = card.height,
                    origin = IntOffset(card.x, card.y),
                    columns = columns,
                    obstacles = settled
                ) ?: IntOffset(0, totalRows(settled))
            } else {
                IntOffset(card.x, card.y)
            }
            settled.add(card.positionAt(position))
        }
        return compact(settled)
    }

    private fun readingOrder() = compareBy<CardRect>({ it.y }, { it.x })
}
