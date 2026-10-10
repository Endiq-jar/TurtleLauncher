package com.endiq.turtlelauncher.support.touch_controller

/** Maps Android MotionEvent pointer IDs to the IDs expected by TouchController. */
internal class TouchPointerMap {
    private val androidToClientId = mutableMapOf<Int, Int>()
    private var nextClientId = 1

    fun register(androidPointerId: Int): Int = androidToClientId.getOrPut(androidPointerId) {
        val id = nextClientId
        nextClientId = if (nextClientId == Int.MAX_VALUE) 1 else nextClientId + 1
        id
    }

    fun clientIdFor(androidPointerId: Int): Int? = androidToClientId[androidPointerId]

    /** Removes by Android pointer ID and returns the corresponding TouchController ID. */
    fun remove(androidPointerId: Int): Int? = androidToClientId.remove(androidPointerId)

    fun clear() {
        androidToClientId.clear()
        nextClientId = 1
    }
}

internal data class NormalizedTouchOffset(val x: Float, val y: Float)

/** Convert local view coordinates to the normalized coordinates expected by TouchController. */
internal fun normalizeTouchOffset(
    x: Float,
    y: Float,
    width: Int,
    height: Int
): NormalizedTouchOffset? {
    if (width <= 0 || height <= 0 || !x.isFinite() || !y.isFinite()) return null
    return NormalizedTouchOffset(
        x = (x / width).coerceIn(0f, 1f),
        y = (y / height).coerceIn(0f, 1f)
    )
}
