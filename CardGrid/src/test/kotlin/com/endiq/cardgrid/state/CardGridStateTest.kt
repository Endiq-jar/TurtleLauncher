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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import com.endiq.cardgrid.engine.GridEngine
import com.endiq.cardgrid.model.CardInteraction
import com.endiq.cardgrid.model.CardRect
import com.endiq.cardgrid.model.CardType
import com.endiq.cardgrid.model.ResizeEdge
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardGridStateTest {

    // Suspended animation coroutines die with the scope; the state assertions only care about synchronous layout computation
    private val scope = CoroutineScope(
        SupervisorJob() + CoroutineExceptionHandler { _, _ -> }
    )

    private val testType = CardType(
        typeId = "test",
        defaultSpan = IntOffset(4, 4),
        content = { _ -> }
    )

    /** A geometry-ready 10-column grid with 20px cells */
    private fun state(): CardGridState {
        val state = CardGridState(scope)
        state.updateGeometry(200f, Density(1f))
        return state
    }

    private fun seededState(vararg layouts: CardRect): CardGridState {
        val state = state()
        state.seed(
            types = listOf(testType),
            seeds = layouts.map { CardSeed(it.id, "test", it) },
            storedColumns = 10
        )
        return state
    }

    private fun layoutOf(state: CardGridState, id: String): CardRect =
        state.cards.first { it.id == id }.layout

    @Test
    fun testViewportPositionedUpdatesState() {
        val state = state()
        state.onViewportPositioned(120f, 800f)
        assertEquals(120f, state.viewportTopPx, 0f)
        assertEquals(800f, state.viewportHeightPx, 0f)
    }

    // ---------- Seeding ----------

    @Test
    fun testSeedValidatesOverlappingLayouts() {
        val state = seededState(
            CardRect("A", 0, 0, 4, 4),
            CardRect("B", 0, 0, 4, 4)
        )
        assertEquals(2, state.cards.size)
        assertFalse(GridEngine.hasOverlap(state.cards.map { it.layout }))
        assertTrue(state.cards.all { GridEngine.isInGrid(it.layout, state.geometry.columns) })
    }

    @Test
    fun testSeedReflowsOnColumnMismatch() {
        val state = state()
        // Persisted with 5 columns vs current 10: spans convert by ratio
        state.seed(
            types = listOf(testType),
            seeds = listOf(CardSeed("A", "test", CardRect("A", 0, 0, 2, 2))),
            storedColumns = 5
        )
        assertEquals(CardRect("A", 0, 0, 4, 4), layoutOf(state, "A"))
    }

    @Test
    fun testSeedDropsUnknownType() {
        val state = state()
        state.seed(
            types = listOf(testType),
            seeds = listOf(CardSeed("X", "unknown", CardRect("X", 0, 0, 4, 4))),
            storedColumns = 10
        )
        assertTrue(state.cards.isEmpty())
    }

    @Test
    fun testSeedBeforeFirstMeasurementSkipsInitialGeometryReflow() {
        // Seeding before the first valid measurement: cards land at persisted spots instead of repacking against the initial default geometry
        val state = CardGridState(scope)
        state.seed(
            types = listOf(testType),
            seeds = listOf(CardSeed("A", "test", CardRect("A", 0, 0, 2, 2))),
            storedColumns = 10
        )
        state.updateGeometry(200f, Density(1f))
        assertEquals(CardRect("A", 0, 0, 4, 4), layoutOf(state, "A"))
    }

    @Test
    fun testSeedWithUnknownStoredColumnsValidates() {
        // Unknown stored column count: no ratio conversion, only validation/repair
        val state = state()
        state.seed(
            types = listOf(testType),
            seeds = listOf(CardSeed("A", "test", CardRect("A", 0, 0, 2, 2))),
            storedColumns = 0
        )
        assertEquals(CardRect("A", 0, 0, 4, 4), layoutOf(state, "A"))
    }

    @Test
    fun testSeedReplacesPreMaterializedCard() {
        // Filler cards with the same id added before geometry is ready must be replaced by the persisted layout at seeding, not appended again
        val state = CardGridState(scope)
        state.seed(
            types = listOf(testType),
            seeds = listOf(CardSeed("A", "test", CardRect("A", 2, 0, 4, 4))),
            storedColumns = 10
        )
        state.addCard(testType, "A")
        state.updateGeometry(200f, Density(1f))
        assertEquals(1, state.cards.size)
        assertEquals(CardRect("A", 2, 0, 4, 4), layoutOf(state, "A"))
    }

    @Test
    fun testZeroWidthMeasurementIgnored() {
        val state = seededState(CardRect("A", 0, 0, 4, 4))
        state.updateGeometry(0f, Density(1f))
        assertEquals(10, state.geometry.columns)
        assertEquals(CardRect("A", 0, 0, 4, 4), layoutOf(state, "A"))
    }

    @Test
    fun testReflowSpanGrowthNotEatenByPerStepRounding() {
        //Small spans must keep growing across stepwise column changes without being swallowed by rounding (else card width stalls on ultrawide screens)
        val state = CardGridState(scope)
        state.updateGeometry(1280f, Density(1f)) // 64 columns
        state.seed(
            types = listOf(testType),
            seeds = listOf(CardSeed("A", "test", CardRect("A", 0, 0, 10, 4))),
            storedColumns = 64
        )
        state.updateGeometry(1320f, Density(1f)) // 66 columns
        state.updateGeometry(1360f, Density(1f)) // 68 columns
        assertEquals(CardRect("A", 0, 0, 11, 4), layoutOf(state, "A"))
    }

    @Test
    fun testAddCardBeforeGeometryReadySkipsPersistence() {
        val state = CardGridState(scope)
        var committed = false
        state.onLayoutCommitted = { committed = true }
        state.addCard(testType, "A")
        assertFalse(committed)
    }

    // ---------- Card management ----------

    @Test
    fun testAddCardPlacesAtTopLeftFreeSlot() {
        val state = seededState(CardRect("A", 0, 0, 4, 4))
        val card = state.addCard(testType, "B")
        assertEquals(CardRect("B", 4, 0, 4, 4), card?.layout)
    }

    @Test
    fun testAddCardRejectsDuplicateId() {
        val state = seededState(CardRect("A", 0, 0, 4, 4))
        assertNull(state.addCard(testType, "A"))
    }

    @Test
    fun testRemoveCardCompactsAndNotifies() {
        var removed: String? = null
        var committed = false
        val state = seededState(
            CardRect("A", 0, 0, 4, 4),
            CardRect("B", 0, 8, 4, 4)
        )
        state.onCardRemoved = { removed = it }
        state.onLayoutCommitted = { committed = true }

        state.removeCard("A")
        assertEquals("A", removed)
        assertTrue(committed)
        assertEquals(CardRect("B", 0, 0, 4, 4), layoutOf(state, "B"))
    }

    // ---------- Hit testing ----------

    @Test
    fun testCardAtHitsPlacedCard() {
        val state = seededState(CardRect("A", 0, 0, 4, 4))
        assertEquals("A", state.cardAt(Offset(40f, 40f))?.id)
        assertNull(state.cardAt(Offset(200f, 200f)))
    }

    @Test
    fun testResizeEdgeAtRequiresAdjustingState() {
        val state = seededState(CardRect("A", 0, 0, 4, 4))
        assertNull(state.resizeEdgeAt(Offset(74f, 40f)))
    }

    // ---------- Drag session ----------

    @Test
    fun testDragSessionDisplacesLiveAndCommits() {
        val state = seededState(
            CardRect("A", 0, 0, 4, 4),
            CardRect("B", 4, 0, 4, 4)
        )
        state.onCardDragStart(state.cards.first { it.id == "A" }, Offset(50f, 50f))
        // Pointer over cell 8: B yields live (pointer on B's right half → slides left)
        state.onCardDrag(Offset(170f, 50f))

        assertEquals(CardRect("A", 6, 0, 4, 4), state.dragPreview)
        assertEquals(mapOf("B" to CardRect("B", 2, 0, 4, 4)), state.displaced)

        // Release commits the yield result and persists
        state.onCardDragEnd()
        assertEquals(CardRect("A", 6, 0, 4, 4), layoutOf(state, "A"))
        assertEquals(CardRect("B", 2, 0, 4, 4), layoutOf(state, "B"))
        assertFalse(state.hasSession)
    }

    @Test
    fun testDragCancelRestoresLayouts() {
        val state = seededState(
            CardRect("A", 0, 0, 4, 4),
            CardRect("B", 4, 0, 4, 4)
        )
        state.onCardDragStart(state.cards.first { it.id == "A" }, Offset(50f, 50f))
        state.onCardDrag(Offset(170f, 50f))
        state.onCardDragCancel()

        assertEquals(CardRect("A", 0, 0, 4, 4), layoutOf(state, "A"))
        assertEquals(CardRect("B", 4, 0, 4, 4), layoutOf(state, "B"))
        assertTrue(state.displaced.isEmpty())
    }

    // ---------- Resize session ----------

    @Test
    fun testResizeSessionCommit() {
        val state = seededState(CardRect("A", 0, 0, 4, 4))
        state.onResizeStart(state.cards.first(), ResizeEdge.End, Offset(74f, 40f))
        // Pointer dragged to the right edge of cell 8
        state.onResize(Offset(150f, 40f))

        assertEquals(CardRect("A", 0, 0, 8, 4), state.dragPreview)
        state.onResizeEnd()
        assertEquals(CardRect("A", 0, 0, 8, 4), layoutOf(state, "A"))
    }

    @Test
    fun testResizeBlockedByCardAtEdgeKeepsOriginal() {        val state = seededState(
            CardRect("A", 0, 0, 4, 4),
            CardRect("B", 4, 0, 6, 4)
        )
        state.onResizeStart(state.cards.first { it.id == "A" }, ResizeEdge.End, Offset(74f, 40f))
        state.onResize(Offset(190f, 40f))
        state.onResizeEnd()
        // Once B hugs the right edge the chain cannot move, so A's span stops in place
        assertEquals(CardRect("A", 0, 0, 4, 4), layoutOf(state, "A"))
    }

    // ---------- Adjust mode ----------

    @Test
    fun testInteractionStates() {
        val state = seededState(CardRect("A", 0, 0, 4, 4))
        val card = state.cards.first()
        assertEquals(CardInteraction.Idle, state.interactionOf("A"))

        state.onCardDragStart(card, Offset(50f, 50f))
        assertEquals(CardInteraction.Dragging, state.interactionOf("A"))

        state.onCardDragCancel()
        assertTrue(state.isAdjusting)
        assertEquals(CardInteraction.Adjusting, state.interactionOf("A"))

        state.exitAdjusting()
        assertFalse(state.isAdjusting)
        assertEquals(CardInteraction.Idle, state.interactionOf("A"))
    }
}
