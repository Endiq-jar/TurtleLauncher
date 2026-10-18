package com.endiq.turtlelauncher.utils.anim

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.endiq.turtlelauncher.R
import kotlin.math.abs

/**
 * Interaction animations for every vector icon in the launcher (the Tabler/Flowbite
 * set imported by tools/import_icons.py - see docs/ICONS.md).
 *
 * Same rules as the animated WebP logos in [AnimatedLogo] - icons never move on
 * their own:
 *
 *  * hover (mouse / stylus / trackpad over the icon or its row/card) - loops,
 *  * press (touch held down) - loops while held; releasing finishes the sweep,
 *  * click/tap - exactly one full play-through,
 *  * focus (keyboard / gamepad) - loops while focused.
 *
 * Each icon gets its own animation style: semantic ones first (refresh spins,
 * favourites heartbeat, downloads drop, eyes blink, warnings swing, deletes
 * shake...), everything else a stable per-icon pick, so neighbouring icons do
 * not all move the same way. Styles animate view properties only (scale /
 * rotation / translation), which is GPU-composited and allocation-light;
 * a fresh cycle is built per play so a cancelled animator can never leak state.
 */
object AnimatedIcon {

    enum class Style {
        PULSE, POP, HEARTBEAT, BOUNCE_Y, DROP, LIFT, LAUNCH, SLIDE_X,
        SPIN, GEAR, SHAKE, WOBBLE, WIGGLE, SWING, BLINK, SCAN, TADA, FLIP_X, FLIP_Y
    }

    /** Fallback pool for icons without a semantic style - picked by name hash. */
    private val FALLBACK = listOf(
        Style.PULSE, Style.BOUNCE_Y, Style.WOBBLE, Style.POP,
        Style.SWING, Style.FLIP_X, Style.LIFT, Style.TADA
    )

    /** Style for an icon resource, by resource entry name (stable across runs). */
    fun styleFor(entryName: String): Style {
        val n = entryName.lowercase()
        return when {
            "refresh" in n || "reset" in n || "spinner" in n -> Style.SPIN
            "favorite" in n || "heart" in n -> Style.HEARTBEAT
            "download" in n || "extract" in n || "chevron_down" in n -> Style.DROP
            "chevron" in n || "arrow" in n || "launch_link" in n -> Style.SLIDE_X
            "eye" in n -> Style.BLINK
            "search" in n -> Style.SCAN
            "delete" in n || "disabled" in n -> Style.SHAKE
            "close" in n || "cancel" in n -> Style.WIGGLE
            "warning" in n || "help" in n || "about" in n || "alert" in n -> Style.SWING
            "music" in n || "speaker" in n || "emotes" in n -> Style.WOBBLE
            "play" in n -> Style.PULSE
            "add" in n -> Style.POP
            "share" in n || "logout" in n -> Style.LAUNCH
            "check" in n || "tasks" in n -> Style.POP
            "copy" in n || "paste" in n || "file" in n || "folder" in n ||
                "document" in n || "zip" in n -> Style.LIFT
            "chat" in n || "_ai" in n || "ai_" in n -> Style.TADA
            "edit" in n || "hair" in n || "skin" in n || "cape" in n || "move" in n ||
                "handle" in n -> Style.WIGGLE
            "globe" in n || "friends" in n || "network" in n || "accounts" in n -> Style.FLIP_Y
            "setting" in n || "optimization" in n || "git" in n -> Style.GEAR
            else -> FALLBACK[abs(n.hashCode()) % FALLBACK.size]
        }
    }

    /** Reads `android:src` / `app:srcCompat` from XML attrs (0 when absent). */
    @JvmStatic
    fun readSource(context: Context, attrs: AttributeSet?): Int {
        if (attrs == null) return 0
        val values = context.obtainStyledAttributes(
            attrs, intArrayOf(android.R.attr.src, androidx.appcompat.R.attr.srcCompat)
        )
        val compat = values.getResourceId(1, 0)
        val source = if (compat != 0) compat else values.getResourceId(0, 0)
        values.recycle()
        return source
    }

    /** Routes WebP logos to [AnimatedLogo] and everything else to the icon styles. */
    @JvmStatic
    fun bindAny(view: ImageView, resource: Int) {
        if (resource == 0) return
        if (AnimatedLogo.isAnimatedLogo(resource)) {
            clear(view)
            AnimatedLogo.bind(view, resource)
        } else {
            bind(view, resource)
        }
    }

    @JvmStatic
    fun playOnceAny(view: ImageView) {
        playOnce(view)
        AnimatedLogo.playOnce(view)
    }

    @JvmStatic
    fun bind(view: ImageView, resource: Int) {
        AnimatedLogo.clear(view) // a view is either a vector icon or a WebP logo
        clear(view)
        if (resource == 0 || !TurtleTransitions.isEnabled()) return
        val name = runCatching { view.resources.getResourceEntryName(resource) }
            .getOrDefault("icon_$resource")
        view.setTag(R.id.animated_icon_request, Request(view, styleFor(name)))
    }

    /** One play-through for clicks without a touch sequence. No-op if playing. */
    @JvmStatic
    fun playOnce(view: ImageView) {
        (view.getTag(R.id.animated_icon_request) as? Request)?.playOnceIfIdle()
    }

    @JvmStatic
    fun clear(view: ImageView) {
        (view.getTag(R.id.animated_icon_request) as? Request)?.close()
        view.setTag(R.id.animated_icon_request, null)
    }

    // -- cycle factory -------------------------------------------------------

    private fun anim(view: View, property: String, vararg values: Float): ObjectAnimator =
        ObjectAnimator.ofFloat(view, property, *values)

    private fun cycle(view: View, style: Style): AnimatorSet {
        val d = 2f * view.resources.displayMetrics.density // 2dp travel unit
        val parts = when (style) {
            Style.PULSE -> listOf(
                anim(view, View.SCALE_X.name, 1f, 1.18f, 1f),
                anim(view, View.SCALE_Y.name, 1f, 1.18f, 1f))
            Style.POP -> listOf(
                anim(view, View.SCALE_X.name, 1f, 1.3f, 1f),
                anim(view, View.SCALE_Y.name, 1f, 1.3f, 1f))
            Style.HEARTBEAT -> listOf(
                anim(view, View.SCALE_X.name, 1f, 1.28f, 1f, 1.18f, 1f),
                anim(view, View.SCALE_Y.name, 1f, 1.28f, 1f, 1.18f, 1f))
            Style.BOUNCE_Y -> listOf(
                anim(view, View.TRANSLATION_Y.name, 0f, -1.6f * d, 0f, -0.7f * d, 0f))
            Style.DROP -> listOf(
                anim(view, View.TRANSLATION_Y.name, 0f, 1.6f * d, 0f, 0.6f * d, 0f))
            Style.LIFT -> listOf(
                anim(view, View.TRANSLATION_Y.name, 0f, -1.5f * d, 0f),
                anim(view, View.SCALE_X.name, 1f, 1.06f, 1f),
                anim(view, View.SCALE_Y.name, 1f, 1.06f, 1f))
            Style.LAUNCH -> listOf(
                anim(view, View.TRANSLATION_X.name, 0f, d, 0f),
                anim(view, View.TRANSLATION_Y.name, 0f, -d, 0f))
            Style.SLIDE_X -> listOf(
                anim(view, View.TRANSLATION_X.name, 0f, 1.6f * d, 0f))
            Style.SPIN -> listOf(anim(view, View.ROTATION.name, 0f, 360f))
            Style.GEAR -> listOf(anim(view, View.ROTATION.name, 0f, 35f, 0f))
            Style.SHAKE -> listOf(
                anim(view, View.TRANSLATION_X.name, 0f, -d, d, -0.6f * d, 0.6f * d, 0f))
            Style.WOBBLE -> listOf(
                anim(view, View.ROTATION.name, 0f, -8f, 6f, -4f, 0f),
                anim(view, View.TRANSLATION_X.name, 0f, -0.5f * d, 0.5f * d, 0f))
            Style.WIGGLE -> listOf(anim(view, View.ROTATION.name, 0f, -12f, 12f, 0f))
            Style.SWING -> listOf(anim(view, View.ROTATION.name, 0f, 14f, -10f, 5f, 0f))
            Style.BLINK -> listOf(anim(view, View.SCALE_Y.name, 1f, 0.12f, 1f))
            Style.SCAN -> listOf(
                anim(view, View.SCALE_X.name, 1f, 1.16f, 1f),
                anim(view, View.SCALE_Y.name, 1f, 1.16f, 1f),
                anim(view, View.ROTATION.name, 0f, -10f, 0f))
            Style.TADA -> listOf(
                anim(view, View.SCALE_X.name, 1f, 0.9f, 1.12f, 1.16f, 1f),
                anim(view, View.SCALE_Y.name, 1f, 0.9f, 1.12f, 1.16f, 1f),
                anim(view, View.ROTATION.name, 0f, -3f, 3f, -3f, 0f))
            Style.FLIP_X -> listOf(anim(view, View.ROTATION_X.name, 0f, 360f))
            Style.FLIP_Y -> listOf(anim(view, View.ROTATION_Y.name, 0f, 360f))
        }
        return AnimatorSet().apply {
            playTogether(parts)
            duration = if (style == Style.SPIN) 800L else 680L
            interpolator = when (style) {
                Style.SPIN, Style.FLIP_X, Style.FLIP_Y -> LinearInterpolator()
                Style.POP, Style.TADA -> OvershootInterpolator(1.4f)
                else -> AccelerateDecelerateInterpolator()
            }
        }
    }

    // -- request -------------------------------------------------------------

    private class Request(
        private val view: ImageView,
        private val style: Style
    ) : View.OnAttachStateChangeListener, DefaultLifecycleObserver, InteractionListener {

        private var owner: LifecycleOwner? = null
        private var target: View? = null
        private var wiring: InteractionWiring? = null
        private var focusObserver: ViewTreeObserver? = null

        private var hovered = false
        private var pressed = false
        private var focused = false
        /** Interaction ended: let the current sweep finish, then rest. */
        private var finishing = false
        private var running = false
        private var animator: AnimatorSet? = null
        private var closed = false

        override fun onHover(event: MotionEvent) {
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE ->
                    if (!hovered) { hovered = true; update() }
                MotionEvent.ACTION_HOVER_EXIT ->
                    if (hovered) { hovered = false; finishSoon() }
            }
        }

        override fun onTouch(event: MotionEvent) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { pressed = true; update() }
                MotionEvent.ACTION_UP -> { pressed = false; finishSoon() }
                MotionEvent.ACTION_CANCEL -> { pressed = false; finishSoon() }
            }
        }

        private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
            val now = newFocus != null && (newFocus === view || InteractionWiring.isWithin(newFocus, target))
            if (now != focused) { focused = now; if (now) update() else finishSoon() }
        }

        init {
            view.addOnAttachStateChangeListener(this)
            if (view.isAttachedToWindow) onViewAttachedToWindow(view)
        }

        private fun isInteracting() = hovered || pressed || focused

        private fun update() {
            if (isInteracting()) {
                finishing = false
                startCycle()
            } else {
                finishSoon()
            }
        }

        private fun finishSoon() {
            finishing = true
            if (!running) { finishing = false; resetProps() }
        }

        fun playOnceIfIdle() {
            if (running || closed || !TurtleTransitions.isEnabled()) return
            finishing = true
            startCycle()
        }

        private fun startCycle() {
            if (running || closed || !TurtleTransitions.isEnabled()) return
            if (style == Style.SWING) { // hang from the top edge like a bell
                view.pivotX = view.width / 2f
                view.pivotY = 0f
            }
            running = true
            animator = cycle(view, style).apply {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        running = false
                        animator = null
                        resetProps()
                        // Hover/press/focus keeps looping; a finished tap rests.
                        if (!closed && isInteracting()) startCycle() else finishing = false
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        running = false
                        animator = null
                        resetProps()
                    }
                })
                start()
            }
        }

        private fun resetProps() {
            view.scaleX = 1f; view.scaleY = 1f
            view.rotation = 0f; view.rotationX = 0f; view.rotationY = 0f
            view.translationX = 0f; view.translationY = 0f
            view.alpha = 1f
            view.pivotX = view.width / 2f; view.pivotY = view.height / 2f
        }

        private fun stopNow() {
            finishing = false
            animator?.cancel() // cancel() also resets via the listener
            animator = null
            running = false
            resetProps()
        }

        // -- wiring / lifecycle (same contract as AnimatedLogo) --------------

        override fun onViewAttachedToWindow(v: View) {
            owner = view.findViewTreeLifecycleOwner()
            owner?.lifecycle?.addObserver(this)
            removeWiring()
            val resolved = InteractionWiring.resolveTarget(view)
            target = resolved
            wiring = InteractionWiring.of(resolved).also { it.attach(this) }
            hovered = resolved.isHovered
            focused = resolved.isFocused || view.isFocused
            val observer = view.viewTreeObserver
            if (observer.isAlive) {
                observer.addOnGlobalFocusChangeListener(focusListener)
                focusObserver = observer
            }
        }

        override fun onViewDetachedFromWindow(v: View) {
            hovered = false; pressed = false; focused = false
            stopNow()
            removeWiring()
            owner?.lifecycle?.removeObserver(this)
            owner = null
        }

        private fun removeWiring() {
            wiring?.detach(this)
            wiring = null
            target = null
            focusObserver?.let { if (it.isAlive) it.removeOnGlobalFocusChangeListener(focusListener) }
            focusObserver = null
        }

        override fun onPause(owner: LifecycleOwner) {
            pressed = false
            stopNow()
        }

        override fun onDestroy(owner: LifecycleOwner) = close()

        fun close() {
            if (closed) return
            closed = true
            stopNow()
            removeWiring()
            owner?.lifecycle?.removeObserver(this)
            owner = null
            view.removeOnAttachStateChangeListener(this)
        }
    }
}
