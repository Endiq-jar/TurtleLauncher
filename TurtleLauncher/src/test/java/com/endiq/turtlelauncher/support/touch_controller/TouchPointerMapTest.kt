package com.endiq.turtlelauncher.support.touch_controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchPointerMapTest {
    @Test
    fun removeUsesAndroidPointerIdAndAllowsThatIdToBeReused() {
        val pointers = TouchPointerMap()
        val firstClientId = pointers.register(42)
        val secondClientId = pointers.register(7)

        assertTrue(firstClientId != secondClientId)
        assertEquals(firstClientId, pointers.remove(42))
        assertNull(pointers.clientIdFor(42))

        val reusedClientId = pointers.register(42)
        assertTrue(reusedClientId != firstClientId)
        assertEquals(reusedClientId, pointers.clientIdFor(42))
    }

    @Test
    fun clearResetsMappingsAndStartsANewGestureNamespace() {
        val pointers = TouchPointerMap()
        assertEquals(1, pointers.register(8))
        pointers.clear()

        assertNull(pointers.clientIdFor(8))
        assertEquals(1, pointers.register(3))
    }

    @Test
    fun offsetIsNormalizedClampedAndRejectsInvalidViewDimensions() {
        assertEquals(
            NormalizedTouchOffset(0.5f, 0.25f),
            normalizeTouchOffset(50f, 25f, 100, 100)
        )
        assertEquals(
            NormalizedTouchOffset(1f, 0f),
            normalizeTouchOffset(150f, -4f, 100, 100)
        )
        assertNull(normalizeTouchOffset(1f, 1f, 0, 100))
        assertNull(normalizeTouchOffset(Float.NaN, 1f, 100, 100))
    }
}
