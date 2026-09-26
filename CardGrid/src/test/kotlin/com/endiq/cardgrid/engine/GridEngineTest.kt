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
import com.endiq.cardgrid.model.CardSizeClass
import com.endiq.cardgrid.model.ResizeEdge
import com.endiq.cardgrid.model.computeGridGeometry
import com.endiq.cardgrid.model.deriveSizeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs

class GridEngineTest {

    private fun card(
        id: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ) = CardRect(id = id, x = x, y = y, width = width, height = height)

    // ---------- Grid geometry ----------

    @Test
    fun testGridColumnsAlwaysEven() {
        // 500dp / 20dp = 25 → rounds to the nearest even number 26
        assertEquals(26, computeGridGeometry(500f).columns)
        // 300dp / 20dp = 15 → rounds to the nearest even number 16
        assertEquals(16, computeGridGeometry(300f).columns)
        // 199dp / 20dp ≈ 10 → stays even
        assertEquals(10, computeGridGeometry(199f).columns)
    }

    @Test
    fun testGridEdgesAlignToContainer() {
        val geometry = computeGridGeometry(500f)
        assertEquals(500f, geometry.columns * geometry.cellSize, 0.01f)
        // Cell edge length stays near 20dp
        assertTrue(abs(geometry.cellSize - 20f) < 2f)
    }

    @Test
    fun testGridMinimumColumns() {
        assertEquals(4, computeGridGeometry(30f).columns)
        assertEquals(4, computeGridGeometry(0f).columns)
        assertEquals(4, computeGridGeometry(-10f).columns)
    }

    // ---------- Form factor classification ----------

    @Test
    fun testDeriveSizeClassHeightBySpan() {
        val columns = 16
        //Height: ≤4 cramped, =5 small, 6..7 medium, 8..9 large, ≥10 extra large
        assertEquals(CardSizeClass.COMPACT, deriveSizeClass(10, 2, columns).height)
        assertEquals(CardSizeClass.COMPACT, deriveSizeClass(10, 4, columns).height)
        assertEquals(CardSizeClass.SMALL, deriveSizeClass(10, 5, columns).height)
        assertEquals(CardSizeClass.MEDIUM, deriveSizeClass(10, 6, columns).height)
        assertEquals(CardSizeClass.MEDIUM, deriveSizeClass(10, 7, columns).height)
        assertEquals(CardSizeClass.LARGE, deriveSizeClass(10, 8, columns).height)
        assertEquals(CardSizeClass.LARGE, deriveSizeClass(10, 9, columns).height)
        assertEquals(CardSizeClass.EXTRA_LARGE, deriveSizeClass(10, 10, columns).height)
        assertEquals(CardSizeClass.EXTRA_LARGE, deriveSizeClass(10, 20, columns).height)
    }

    @Test
    fun testDeriveSizeClassWidthByFraction() {
        val columns = 16
        //Width tiers split by share of grid width: each tier spans 16/5 columns
        assertEquals(CardSizeClass.COMPACT, deriveSizeClass(3, 8, columns).width)
        assertEquals(CardSizeClass.SMALL, deriveSizeClass(4, 8, columns).width)
        assertEquals(CardSizeClass.SMALL, deriveSizeClass(6, 8, columns).width)
        assertEquals(CardSizeClass.MEDIUM, deriveSizeClass(7, 8, columns).width)
        assertEquals(CardSizeClass.LARGE, deriveSizeClass(10, 8, columns).width)
        assertEquals(CardSizeClass.LARGE, deriveSizeClass(12, 8, columns).width)
        //A share of ≥ 4/5 lands in the top tier
        assertEquals(CardSizeClass.EXTRA_LARGE, deriveSizeClass(13, 8, columns).width)
        //Filling the whole row lands in the top tier
        assertEquals(CardSizeClass.EXTRA_LARGE, deriveSizeClass(16, 8, columns).width)
        //Height is still bucketed by absolute span
        assertEquals(CardSizeClass.MEDIUM, deriveSizeClass(16, 7, columns).height)
    }

    @Test
    fun testDeriveSizeClassDimensionsIndependent() {
        val columns = 16
        //Width and height are tiered independently
        val size = deriveSizeClass(width = 3, height = 10, columns = columns)
        assertEquals(CardSizeClass.COMPACT, size.width)
        assertEquals(CardSizeClass.EXTRA_LARGE, size.height)

        val square = deriveSizeClass(width = 7, height = 7, columns = columns)
        assertEquals(CardSizeClass.MEDIUM, square.width)
        assertEquals(CardSizeClass.MEDIUM, square.height)
    }

    // ---------- Nearest free spot search ----------

    @Test
    fun testFindNearestSlotPrefersSidewaysWhenBlockedBelow() {
        // The origin and the spot right below are occupied, so (0,0) on the left becomes the nearest free spot
        val obstacles = listOf(
            card("block1", 8, 0, 8, 4),
            card("block2", 8, 4, 8, 4)
        )
        val slot = GridEngine.findNearestFreeSlot(8, 4, IntOffset(8, 0), 16, obstacles)
        assertEquals(IntOffset(0, 0), slot)
    }

    @Test
    fun testFindNearestSlotFallsBelowFullRow() {
        // The whole row is occupied, so it can only land on the next row
        val obstacles = listOf(card("block", 0, 0, 16, 4))
        val slot = GridEngine.findNearestFreeSlot(16, 4, IntOffset(0, 0), 16, obstacles)
        assertEquals(IntOffset(0, 4), slot)
    }

    @Test
    fun testFindNearestSlotGuaranteedBelowAllObstacles() {
        // With obstacles stacked full vertically, the solution lands below all of them
        val obstacles = listOf(
            card("a", 0, 0, 4, 4),
            card("b", 0, 4, 4, 4)
        )
        val slot = GridEngine.findNearestFreeSlot(4, 4, IntOffset(0, 4), 4, obstacles)
        assertEquals(IntOffset(0, 8), slot)
    }

    @Test
    fun testFindNearestSlotImpossibleWidth() {
        assertNull(
            GridEngine.findNearestFreeSlot(20, 4, IntOffset(0, 0), 16, emptyList())
        )
    }

    // ---------- Squeeze computation ----------

    @Test
    fun testDisplacedCardSlidesSidewaysWithoutCascade() {
        val columns = 16
        val moving = card("A", 0, 0, 8, 4)
        val others = listOf(
            card("B", 4, 0, 8, 4),   // overlaps A and gets squeezed
            card("C", 0, 4, 8, 4)    // not overlapped; must not be cascaded onto
        )
        val result = GridEngine.resolveDisplacements(moving, columns, others)
        // B slides right of A, size unchanged
        assertEquals(card("B", 8, 0, 8, 4), result["B"])
        // C is completely unaffected
        assertFalse(result.containsKey("C"))
    }

    @Test
    fun testDisplacedCardFallsToNextRowWhenRowFull() {
        val columns = 16
        val moving = card("A", 0, 0, 16, 4)
        val others = listOf(card("B", 0, 0, 8, 4))
        val result = GridEngine.resolveDisplacements(moving, columns, others)
        // No room in-row, so B lands on the next row
        assertEquals(card("B", 0, 4, 8, 4), result["B"])
    }

    @Test
    fun testDragOverlapsTwoCardsDisplacesBoth() {
        val columns = 16
        // The drag squeeze covers cards on both left and right at once
        val moving = card("B", 4, 0, 8, 4)
        val others = listOf(
            card("L", 0, 0, 6, 4),
            card("R", 10, 0, 6, 4)
        )
        val result = GridEngine.resolveDisplacements(moving, columns, others)
        assertEquals(2, result.size)
        val settled = others.map { result[it.id] ?: it } + moving
        assertFalse(GridEngine.hasOverlap(settled))
    }

    @Test
    fun testDisplacementDirectionPicksDominantSide() {
        // Whichever side of the card center the pointer is on, the card yields the opposite way
        val b = card("B", 4, 4, 4, 4)
        // Pointer left of center → yields right
        assertEquals(IntOffset(1, 0), GridEngine.displacementDirection(IntOffset(3, 6), b))
        // Pointer right of center → yields left
        assertEquals(IntOffset(-1, 0), GridEngine.displacementDirection(IntOffset(9, 6), b))
        // Pointer above center → yields down
        assertEquals(IntOffset(0, 1), GridEngine.displacementDirection(IntOffset(6, 3), b))
        // Pointer below center → yields up
        assertEquals(IntOffset(0, -1), GridEngine.displacementDirection(IntOffset(6, 9), b))
        // On a dominant-axis tie, horizontal wins
        assertEquals(IntOffset(-1, 0), GridEngine.displacementDirection(IntOffset(8, 8), b))
    }

    @Test
    fun testDirectionalFreeSlotSlidesSidewaysFirst() {
        // Regression for over-eager vertical yielding: pointer on B's left half → B slides right against the drag card instead of dropping a row
        val others = listOf(card("B", 4, 0, 4, 4))
        val result = GridEngine.resolveDisplacements(card("A", 0, 0, 6, 4), 16, others, IntOffset(5, 1))
        assertEquals(mapOf("B" to card("B", 6, 0, 4, 4)), result)

        // Pointer on B's upper half → B yields by sliding down
        val downward = GridEngine.resolveDisplacements(card("A", 0, 0, 16, 6), 16, listOf(card("B", 0, 0, 4, 4)), IntOffset(2, 1))
        assertEquals(mapOf("B" to card("B", 0, 6, 4, 4)), downward)
    }

    @Test
    fun testDirectionalFreeSlotFallsBackWhenBlocked() {
        // When the yield direction is blocked, degrade to the nearest free spot
        val a = card("A", 0, 0, 4, 4)
        val others = listOf(
            card("B", 4, 0, 4, 4),
            card("C", 8, 0, 4, 4),
            card("D", 12, 0, 4, 4)
        )
        // A grows to 8 wide onto B; pointer on B's left half → right, but C/D block the right side → degrade to nearest free spot
        val result = GridEngine.resolveDisplacements(card("A", 0, 0, 8, 4), 16, others, IntOffset(5, 1))
        val settled = others.map { result[it.id] ?: it } + card("A", 0, 0, 8, 4)
        assertFalse(GridEngine.hasOverlap(settled))
        // B yields downward (no room on the right)
        assertTrue(result.getValue("B").y > 0)
    }

    @Test
    fun testDirectionalFreeSlotScansAlongAxis() {
        val obstacles = listOf(
            card("A", 0, 0, 4, 4),
            card("B", 4, 0, 4, 4),
            card("C", 8, 0, 4, 4)
        )
        // B scans right from (4,0): hits C at (5,0), clear at (12,0)
        val slot = GridEngine.findDirectionalFreeSlot(card("B", 4, 0, 4, 4), IntOffset(1, 0), 16, obstacles)
        assertEquals(IntOffset(12, 0), slot)
        // Going above the grid top returns null
        assertNull(GridEngine.findDirectionalFreeSlot(card("B", 4, 0, 4, 4), IntOffset(0, -1), 16, obstacles))
        // Downward it lands below the obstacle
        val down = GridEngine.findDirectionalFreeSlot(card("B", 4, 0, 4, 4), IntOffset(0, 1), 16, obstacles)
        assertEquals(IntOffset(4, 4), down)
    }

    // ---------- Resize computation ----------

    @Test
    fun testResizeEndPushesOverlappedCard() {
        // Growing onto B: B is pushed flush with the front until it hugs the grid's right edge and cannot move
        val a = card("A", 0, 0, 4, 4)
        val obstacles = listOf(card("B", 4, 0, 8, 4))
        val result = GridEngine.resolveResize(a, ResizeEdge.End, IntOffset(16, 0), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 0, 0, 8, 4), result.layout)
        assertEquals(mapOf("B" to card("B", 8, 0, 8, 4)), result.pushed)
    }

    @Test
    fun testResizeEndBlockedWhenSideFull() {
        // B fills A's right side against the grid edge: totally stuck, the span stops in place
        val a = card("A", 0, 0, 4, 4)
        val obstacles = listOf(card("B", 4, 0, 12, 4))
        val result = GridEngine.resolveResize(a, ResizeEdge.End, IntOffset(16, 0), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 0, 0, 4, 4), result.layout)
        assertTrue(result.pushed.isEmpty())
    }

    @Test
    fun testResizeEndGrowsIntoFreeSpaceWithLeftNeighbor() {
        // Regression: left-side cards on the same row are not in the growth path, so it must grow all the way to the right edge when the right is empty
        val a = card("A", 4, 0, 3, 4)
        val obstacles = listOf(card("B", 0, 0, 4, 4))
        val result = GridEngine.resolveResize(a, ResizeEdge.End, IntOffset(10, 0), 10, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 4, 0, 6, 4), result.layout)
        assertTrue(result.pushed.isEmpty())
    }

    @Test
    fun testResizeBottomKeepsCardAbove() {
        // Regression: vertical growth ignores cards above; they must not be teleported
        val a = card("A", 0, 4, 4, 4)
        val obstacles = listOf(card("B", 0, 0, 8, 4))
        val result = GridEngine.resolveResize(a, ResizeEdge.Bottom, IntOffset(0, 100), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 0, 4, 4, 12), result.layout)
        assertTrue(result.pushed.isEmpty())
    }

    @Test
    fun testResizeEndPushesChainOfCards() {
        // Push chain: B gets pushed and drags C along until C hugs the grid's right edge
        val a = card("A", 0, 0, 4, 4)
        val obstacles = listOf(
            card("B", 4, 0, 4, 4),
            card("C", 8, 0, 4, 4)
        )
        val result = GridEngine.resolveResize(a, ResizeEdge.End, IntOffset(10, 0), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 0, 0, 8, 4), result.layout)
        assertEquals(
            mapOf(
                "B" to card("B", 8, 0, 4, 4),
                "C" to card("C", 12, 0, 4, 4)
            ),
            result.pushed
        )
    }

    @Test
    fun testResizeStartPushesOverlappedCard() {
        // Left-edge growth: B is pushed toward the grid's left edge
        val a = card("A", 4, 0, 4, 4)
        val obstacles = listOf(card("B", 2, 0, 2, 4))
        val result = GridEngine.resolveResize(a, ResizeEdge.Start, IntOffset(0, 0), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 2, 0, 6, 4), result.layout)
        assertEquals(mapOf("B" to card("B", 0, 0, 2, 4)), result.pushed)
    }

    @Test
    fun testResizeStartBlockedByFlushCard() {
        // A card hugs the left edge: the left edge cannot grow
        val a = card("A", 4, 0, 4, 4)
        val obstacles = listOf(card("B", 0, 0, 4, 4))
        val result = GridEngine.resolveResize(a, ResizeEdge.Start, IntOffset(0, 0), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 4, 0, 4, 4), result.layout)
        assertTrue(result.pushed.isEmpty())
    }

    @Test
    fun testResizeBottomPushesCardBelow() {
        // Downward is unbounded: B keeps being pushed below A's bottom edge
        val a = card("A", 0, 0, 4, 4)
        val obstacles = listOf(card("B", 0, 8, 4, 4))
        val result = GridEngine.resolveResize(a, ResizeEdge.Bottom, IntOffset(0, 100), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 0, 0, 4, 12), result.layout)
        assertEquals(mapOf("B" to card("B", 0, 12, 4, 4)), result.pushed)
    }

    @Test
    fun testResizeTopPushesCardAbove() {
        // Top-edge growth: B is pushed to the grid top; A stops at B's old spot
        val a = card("A", 0, 4, 4, 4)
        val obstacles = listOf(card("B", 0, 1, 4, 2))
        val result = GridEngine.resolveResize(a, ResizeEdge.Top, IntOffset(0, 0), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 0, 2, 4, 6), result.layout)
        assertEquals(mapOf("B" to card("B", 0, 0, 4, 2)), result.pushed)
    }

    @Test
    fun testResizeTopBlockedByGridEdge() {
        // With nothing in the way, the top edge stops at the grid top
        val a = card("A", 0, 4, 4, 4)
        val result = GridEngine.resolveResize(a, ResizeEdge.Top, IntOffset(0, 0), 16, CardLimits.DEFAULT, emptyList())
        assertEquals(card("A", 0, 0, 4, 8), result.layout)
    }

    @Test
    fun testResizeShrinkUnaffectedByPush() {
        // Shrinking ignores pushing and is only bounded by the minimum span
        val a = card("A", 0, 0, 8, 4)
        val obstacles = listOf(card("B", 8, 0, 4, 4))
        val result = GridEngine.resolveResize(a, ResizeEdge.End, IntOffset(0, 0), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 0, 0, 4, 4), result.layout)
        assertTrue(result.pushed.isEmpty())
    }

    @Test
    fun testResizePushResolvesPerpendicularOverlap() {
        // B's row range exceeds A's: pushed, it covers C outside the row band, so C must yield along
        val a = card("A", 0, 0, 4, 4)
        val obstacles = listOf(
            card("B", 4, 0, 4, 6),
            card("C", 10, 4, 4, 4)
        )
        val result = GridEngine.resolveResize(a, ResizeEdge.End, IntOffset(12, 0), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 0, 0, 8, 4), result.layout)
        assertEquals(
            mapOf(
                "B" to card("B", 8, 0, 4, 6),
                "C" to card("C", 12, 4, 4, 4)
            ),
            result.pushed
        )
    }

    @Test
    fun testResizeIgnoresCardsOutsideSpan() {
        // Cards outside the overlapping row range do not take part
        val a = card("A", 0, 0, 4, 4)
        val obstacles = listOf(card("B", 6, 4, 4, 4))   // B sits below A
        val result = GridEngine.resolveResize(a, ResizeEdge.End, IntOffset(16, 0), 16, CardLimits.DEFAULT, obstacles)
        assertEquals(card("A", 0, 0, 16, 4), result.layout)

        // A partially-overlapping card is pushed away instead of blocking
        val tall = card("A", 0, 0, 4, 8)
        val partial = listOf(card("C", 6, 2, 4, 2))     // overlaps A's row range 2..4
        val partialResult = GridEngine.resolveResize(tall, ResizeEdge.End, IntOffset(16, 0), 16, CardLimits.DEFAULT, partial)
        assertEquals(card("A", 0, 0, 12, 8), partialResult.layout)
        assertEquals(mapOf("C" to card("C", 12, 2, 4, 2)), partialResult.pushed)
    }

    @Test
    fun testResizeEndClampsToGridEdge() {
        // With no blocking cards, growth stops at the grid's right edge
        val a = card("A", 0, 0, 4, 4)
        val result = GridEngine.resolveResize(a, ResizeEdge.End, IntOffset(16, 0), 6, CardLimits.DEFAULT, emptyList())
        assertEquals(card("A", 0, 0, 6, 4), result.layout)
    }

    // ---------- Vertical compaction ----------

    @Test
    fun testCompactFillsVerticalGap() {
        val cards = listOf(
            card("A", 0, 0, 8, 4),
            card("B", 0, 8, 8, 4)
        )
        val compacted = GridEngine.compact(cards)
        // B floats up to fill the gap between A and B
        assertEquals(4, compacted.first { it.id == "B" }.y)
    }

    @Test
    fun testCompactBlockedByOtherCard() {
        val cards = listOf(
            card("A", 0, 0, 8, 4),
            card("B", 0, 8, 8, 4),
            card("C", 0, 4, 8, 4)
        )
        val compacted = GridEngine.compact(cards)
        val a = compacted.first { it.id == "A" }
        val b = compacted.first { it.id == "B" }
        val c = compacted.first { it.id == "C" }
        assertEquals(0, a.y)
        assertEquals(4, c.y)
        // C sits in between, so B can only rest on top of C
        assertEquals(8, b.y)
    }

    @Test
    fun testCompactPreservesHorizontalPosition() {
        val cards = listOf(card("D", 8, 8, 8, 4))
        val compacted = GridEngine.compact(cards)
        assertEquals(card("D", 8, 0, 8, 4), compacted.first())
    }

    @Test
    fun testCompactAlreadyCompactedIsStable() {
        val cards = listOf(
            card("A", 0, 0, 8, 4),
            card("B", 8, 0, 8, 4),
            card("C", 0, 4, 8, 4)
        )
        assertEquals(cards.sortedWith(compareBy({ it.y }, { it.x })), GridEngine.compact(cards))
    }

    // ---------- Repack ----------

    @Test
    fun testReflowScalesSpansProportionally() {
        val cards = listOf(card("A", 0, 0, 4, 4))
        val result = GridEngine.reflow(cards, oldColumns = 8, columns = 16)
        assertEquals(card("A", 0, 0, 8, 8), result.first())
    }

    @Test
    fun testReflowKeepsReadingOrder() {
        val cards = listOf(
            card("A", 0, 0, 8, 4),
            card("B", 0, 4, 8, 4)
        )
        val result = GridEngine.reflow(cards, oldColumns = 8, columns = 16)
        val a = result.first { it.id == "A" }
        val b = result.first { it.id == "B" }
        assertTrue(a.y <= b.y)
        assertFalse(GridEngine.hasOverlap(result))
    }

    @Test
    fun testReflowClampsToLimits() {
        val cards = listOf(card("A", 0, 0, 16, 16))
        val limits: (CardRect) -> CardLimits = { CardLimits(maxWidth = 8, maxHeight = 8) }
        val result = GridEngine.reflow(cards, oldColumns = 16, columns = 16, limits = limits)
        assertEquals(card("A", 0, 0, 8, 8), result.first())
    }

    @Test
    fun testReflowShrinkingColumnsNeverOverflows() {
        val cards = listOf(
            card("A", 0, 0, 16, 4),
            card("B", 0, 4, 16, 4)
        )
        val result = GridEngine.reflow(cards, oldColumns = 16, columns = 8)
        assertTrue(result.all { GridEngine.isInGrid(it, 8) })
        assertFalse(GridEngine.hasOverlap(result))
    }

    // ---------- Load validation ----------

    @Test
    fun testValidateClampsOutOfBounds() {
        val result = GridEngine.validate(
            listOf(card("A", 14, -2, 8, 4)),
            columns = 16
        )
        assertEquals(card("A", 8, 0, 8, 4), result.first())
    }

    @Test
    fun testValidateResolvesOverlap() {
        val result = GridEngine.validate(
            listOf(
                card("A", 0, 0, 8, 4),
                card("B", 0, 0, 8, 4)
            ),
            columns = 16
        )
        assertFalse(GridEngine.hasOverlap(result))
        assertEquals(2, result.size)
    }

    @Test
    fun testValidateDeduplicatesIds() {
        val result = GridEngine.validate(
            listOf(
                card("A", 0, 0, 8, 4),
                card("A", 0, 0, 8, 4)
            ),
            columns = 16
        )
        assertEquals(1, result.size)
    }

    @Test
    fun testValidateClampsSizeToLimits() {
        val limits: (CardRect) -> CardLimits = { CardLimits(minWidth = 4, minHeight = 4, maxWidth = 8, maxHeight = 8) }
        val result = GridEngine.validate(
            listOf(card("A", 0, 0, 16, 1)),
            columns = 16,
            limits = limits
        )
        assertEquals(card("A", 0, 0, 8, 4), result.first())
    }

    @Test
    fun testValidateEndsCompacted() {
        val result = GridEngine.validate(
            listOf(card("A", 4, 8, 8, 4)),
            columns = 16
        )
        assertEquals(0, result.first().y)
    }

    // ---------- Aggregation ----------

    @Test
    fun testTotalRowsAndOverlap() {
        val cards = listOf(
            card("A", 0, 0, 8, 4),
            card("B", 0, 4, 8, 4)
        )
        assertEquals(8, GridEngine.totalRows(cards))
        assertFalse(GridEngine.hasOverlap(cards))
        assertTrue(GridEngine.hasOverlap(cards + card("C", 4, 2, 8, 4)))
    }

    // ---------- Randomized invariants ----------

    /**
     * Runs many random drags and resizes over random layouts,
     * asserting the layout invariants after every computation: no overlap, no sideways overflow, nothing above the top.
     */
    @Test
    fun testRandomOperationsPreserveInvariants() {
        val random = Random(2026L)
        repeat(200) { round ->
            val columns = if (round % 2 == 0) 8 else 12
            val cards = buildList {
                repeat(10) { index ->
                    val width = (2 + random.nextInt(3)) * 2
                    val height = (2 + random.nextInt(3)) * 2
                    val slot = GridEngine.findTopLeftFreeSlot(width, height, columns, this)
                    add(card("c$index", slot.x, slot.y, width, height))
                }
            }

            repeat(20) {
                val mover = cards[random.nextInt(cards.size)]
                val others = cards.filter { it.id != mover.id }
                val settled: List<CardRect> = if (random.nextBoolean()) {
                    val edge = ResizeEdge.entries[random.nextInt(ResizeEdge.entries.size)]
                    val pointer = when (edge) {
                        ResizeEdge.End -> IntOffset(mover.x + random.nextInt(columns * 2), mover.y + random.nextInt(4))
                        ResizeEdge.Start -> IntOffset(random.nextInt(columns + 8) - 4, mover.y + random.nextInt(4))
                        ResizeEdge.Bottom -> IntOffset(mover.x, mover.y + random.nextInt(30) - 5)
                        ResizeEdge.Top -> IntOffset(mover.x, random.nextInt(20) - 5)
                    }
                    val resized = GridEngine.resolveResize(mover, edge, pointer, columns, CardLimits.DEFAULT, others)
                    others.map { resized.pushed[it.id] ?: it } + resized.layout
                } else {
                    // Drag squeeze: preview drop plus the squeezed cards
                    val target = IntOffset(random.nextInt(columns - mover.width + 1), random.nextInt(40))
                    val preview = mover.positionAt(target)
                    val displaced = GridEngine.resolveDisplacements(preview, columns, others)
                    others.map { displaced[it.id] ?: it } + preview
                }
                assertFalse(
                    "Round $round: settled layout must not overlap: $settled",
                    GridEngine.hasOverlap(settled)
                )
                assertTrue(
                    "Round $round: settled layout must stay in grid: $settled",
                    settled.all { GridEngine.isInGrid(it, columns) }
                )
            }
        }
    }
}
