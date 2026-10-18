package com.endiq.turtlelauncher.utils.anim

import android.view.MotionEvent
import android.view.View
import com.endiq.turtlelauncher.R

/** Receives raw hover/touch events of an interaction target (see [InteractionWiring]). */
internal interface InteractionListener {
    fun onHover(event: MotionEvent)
    fun onTouch(event: MotionEvent)
}

/**
 * One shared pair of hover/touch listeners per interaction target, fanning out to
 * every animated logo/icon bound inside it (a row can carry several). Listeners
 * never consume events, and the last detach restores the target exactly - nothing
 * else in the launcher installs hover/touch listeners on rows, logos or icons.
 *
 * The target is the nearest clickable/focusable ancestor of the animated view (a
 * list row, a card, the version selector...), so the whole row counts as
 * "hovered or clicked", not just the pixels of the icon.
 */
internal class InteractionWiring private constructor(private val target: View) {
    private val listeners = mutableSetOf<InteractionListener>()

    private val hoverListener = View.OnHoverListener { _, event ->
        listeners.toList().forEach { it.onHover(event) }
        false
    }
    private val touchListener = View.OnTouchListener { _, event ->
        listeners.toList().forEach { it.onTouch(event) }
        false
    }

    fun attach(listener: InteractionListener) {
        if (listeners.isEmpty()) {
            target.setOnHoverListener(hoverListener)
            target.setOnTouchListener(touchListener)
        }
        listeners.add(listener)
    }

    fun detach(listener: InteractionListener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            target.setOnHoverListener(null)
            target.setOnTouchListener(null)
            target.setTag(R.id.animated_logo_target, null)
        }
    }

    companion object {
        /** How far up the hierarchy to look for the clickable row/card owning a view. */
        const val MAX_PARENT_HOPS = 5

        fun of(target: View): InteractionWiring =
            target.getTag(R.id.animated_logo_target) as? InteractionWiring
                ?: InteractionWiring(target).also { target.setTag(R.id.animated_logo_target, it) }

        /** The view itself, or its nearest clickable/focusable ancestor within a few hops. */
        fun resolveTarget(view: View): View {
            var current: View? = view
            repeat(MAX_PARENT_HOPS) {
                val candidate = current ?: return view
                if (candidate.isClickable || candidate.isFocusable) return candidate
                current = candidate.parent as? View
            }
            return view
        }

        /** True when [child] is [ancestor] or nested inside it (bounded walk). */
        fun isWithin(child: View, ancestor: View?): Boolean {
            if (ancestor == null) return false
            var current: View? = child
            repeat(MAX_PARENT_HOPS + 1) {
                if (current === ancestor) return true
                current = current?.parent as? View
            }
            return false
        }
    }
}
