package com.endiq.turtlelauncher.utils.anim

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.task.TaskExecutors
import com.endiq.turtlelauncher.utils.ZHTools

/**
 * Finite animated WebP logos (grass block, command block, classic blocks, app title, turtle).
 *
 * The statics in `res/drawable` are the poster frame and the API 26/27/reduced-motion
 * fallback. The animated copies in `res/raw` are decoded off the UI thread but are
 * **never auto-played**: a logo only moves while the user is interacting with it -
 *
 *  * hover (mouse / stylus / trackpad pointer over the logo or its clickable row/card),
 *  * press (touch held down) - releasing the press lets the current sweep finish once,
 *    which is also what a plain tap/click gets: one full play-through,
 *  * focus (keyboard / gamepad / D-pad focus on the logo or its container).
 *
 * As soon as the interaction ends the static poster is restored, so idle screens cost
 * no decode/render work at all. Interaction is observed on the nearest clickable
 * ancestor (a list row, a card, the version selector...) when there is one, so the
 * whole row counts as "hovered or clicked", not just the pixels of the icon. All
 * listeners installed here are non-consuming and are removed again on detach/clear.
 */
object AnimatedLogo {
    private fun rawResourceFor(resource: Int): Int = when (resource) {
        R.drawable.ic_minecraft -> R.raw.ic_minecraft
        R.drawable.ic_turtle_full -> R.raw.ic_turtle_full_animated
        R.drawable.ic_command_block -> R.raw.ic_command_block
        R.drawable.app_name_title -> R.raw.app_name_title
        R.drawable.ic_old_cobblestone -> R.raw.ic_old_cobblestone
        R.drawable.ic_old_grass_block -> R.raw.ic_old_grass_block
        else -> 0
    }

    /** True when [resource] has an animated counterpart that [bind] can play. */
    @JvmStatic
    fun isAnimatedLogo(resource: Int): Boolean = rawResourceFor(resource) != 0

    @JvmStatic
    fun bind(view: ImageView, resource: Int) {
        AnimatedIcon.clear(view) // a view is either a WebP logo or a vector icon
        clear(view)
        // The turtle is the launcher's replaceable logo: when the user picked another one
        // (LogoPickerFragment) it replaces the artwork, and its animated WebP is skipped,
        // since that frame set belongs to the built-in turtle and would bring it back.
        val picked = if (resource == R.drawable.ic_turtle_full) ZHTools.customLogoOrNull() else null
        val static = picked ?: ContextCompat.getDrawable(view.context, resource)
        view.setImageDrawable(static)
        // Kinetic layer: every logo has its own slow transform style (glide, bounce,
        // twist, shimmer, rattle, sway). It is independent of WebP decoding, so logos
        // still move on API 26/27 and while the animated frames are loading.
        AnimatedIcon.bindLogoMotion(view, resource)
        if (picked != null) return
        prepare(view, resource, static) { drawable ->
            view.setImageDrawable(drawable)
            // AnimatedIconButton.setImageDrawable() drops its icon request, so make
            // sure a logo hosted by one keeps its kinetic layer after frame swaps.
            AnimatedIcon.bindLogoMotion(view, resource)
        }
    }

    /**
     * Same interaction-gated playback for a logo used as watermark art inside a
     * [LayerDrawable] (version-series card banners). The static layer already in
     * place at [layerIndex] stays as the poster.
     */
    @JvmStatic
    fun bindWatermark(view: ImageView, layers: LayerDrawable, layerIndex: Int, resource: Int) {
        AnimatedIcon.clear(view)
        clear(view)
        val static = layers.getDrawable(layerIndex)
        AnimatedIcon.bindLogoMotion(view, resource)
        prepare(view, resource, static) { drawable ->
            if (drawable != null) layers.setDrawable(layerIndex, drawable)
        }
    }

    /**
     * One full play-through for clicks that produce no touch sequence on the view
     * (keyboard/gamepad Enter, accessibility click). No-op while already playing.
     */
    @JvmStatic
    fun playOnce(view: ImageView) {
        // One transform sweep even when the animated frames cannot play (API < 28,
        // reduced motion, decode still in flight).
        AnimatedIcon.playOnce(view)
        (view.getTag(R.id.animated_logo_request) as? Request)?.playOnceIfIdle()
    }

    @JvmStatic
    fun clear(view: ImageView) {
        (view.getTag(R.id.animated_logo_request) as? Request)?.close()
        view.setTag(R.id.animated_logo_request, null)
        // Drop the logo's kinetic request too. AnimatedIcon.clear() only closes its own
        // tag, so this cannot recurse back into AnimatedLogo.
        AnimatedIcon.clear(view)
    }

    private fun prepare(view: ImageView, resource: Int, static: Drawable?, show: (Drawable?) -> Unit) {
        if (Build.VERSION.SDK_INT < 28 || !TurtleTransitions.isEnabled()) return
        val raw = rawResourceFor(resource)
        if (raw == 0) return
        // The request exists (and tracks hover/press/focus) before decoding finishes,
        // so an interaction that started during the decode still gets its animation.
        val request = Request(view, static, show)
        view.setTag(R.id.animated_logo_request, request)
        // Decoding is never on the scroll/UI thread. Small frames cap decoded memory.
        TaskExecutors.getDefault().execute {
            if (TaskExecutors.isGameSessionActive) return@execute
            val drawable = runCatching {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(view.resources, raw))
            }.getOrNull() as? AnimatedImageDrawable ?: return@execute
            view.post {
                if (view.getTag(R.id.animated_logo_request) !== request) return@post
                request.install(drawable)
            }
        }
    }

    private class Request(
        private val view: ImageView,
        private val static: Drawable?,
        private val show: (Drawable?) -> Unit
    ) : View.OnAttachStateChangeListener, DefaultLifecycleObserver, InteractionListener {

        private var owner: LifecycleOwner? = null
        private var animated: AnimatedImageDrawable? = null

        /** View whose hover/press/focus drives playback (row/card, or the logo itself). */
        private var target: View? = null
        private var wiring: InteractionWiring? = null
        private var focusObserver: ViewTreeObserver? = null

        private var hovered = false
        private var pressed = false
        private var focused = false

        /** A tap/press ended: finish the current sweep once, then back to the poster. */
        private var finishing = false
        private var showingAnimated = false
        private var closed = false

        /** Fed by [InteractionWiring], the shared hover/touch listeners of the target row. */
        override fun onHover(event: MotionEvent) {
            when (event.actionMasked) {
                // HOVER_MOVE also (re)arms hover after a pause/resume missed the ENTER.
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE ->
                    if (!hovered) { hovered = true; update() }
                MotionEvent.ACTION_HOVER_EXIT ->
                    if (hovered) { hovered = false; update() }
            }
        }

        override fun onTouch(event: MotionEvent) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { pressed = true; update() }
                MotionEvent.ACTION_UP -> { pressed = false; finishAfterTap() }
                MotionEvent.ACTION_CANCEL -> { pressed = false; update() }
            }
        }

        private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
            val nowFocused = newFocus != null && (newFocus === view || isWithin(newFocus, target))
            if (nowFocused != focused) { focused = nowFocused; update() }
        }

        init {
            view.addOnAttachStateChangeListener(this)
            if (view.isAttachedToWindow) onViewAttachedToWindow(view)
        }

        fun install(drawable: AnimatedImageDrawable) {
            if (closed || Build.VERSION.SDK_INT < 28) return
            animated = drawable
            static?.let { drawable.alpha = it.alpha }
            drawable.registerAnimationCallback(object : android.graphics.drawable.Animatable2.AnimationCallback() {
                override fun onAnimationEnd(d: Drawable?) {
                    if (finishing && !isInteracting()) stopNow()
                }
            })
            // Deliberately not started/shown yet: only an interaction (possibly one
            // already in progress while we decoded) may set it in motion.
            update()
        }

        fun playOnceIfIdle() {
            if (Build.VERSION.SDK_INT < 28) return
            val drawable = animated ?: return
            if (finishing || drawable.isRunning || !TurtleTransitions.isEnabled()) return
            finishing = true
            drawable.repeatCount = 0 // exactly one sweep
            showAnimated()
            drawable.start()
        }

        private fun isInteracting() = hovered || pressed || focused

        private fun update() {
            if (isInteracting()) playLooping() else if (!finishing) stopNow()
        }

        private fun playLooping() {
            if (closed || Build.VERSION.SDK_INT < 28) return
            val drawable = animated ?: return
            if (!TurtleTransitions.isEnabled()) { stopNow(); return }
            finishing = false
            drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            showAnimated()
            if (!drawable.isRunning) drawable.start()
        }

        private fun finishAfterTap() {
            if (hovered || focused) { update(); return }
            if (Build.VERSION.SDK_INT < 28) return
            val drawable = animated ?: return
            if (drawable.isRunning) {
                // End at the next loop boundary; the end callback restores the poster.
                finishing = true
                drawable.repeatCount = 0
            } else {
                playOnceIfIdle()
            }
        }

        private fun stopNow() {
            finishing = false
            if (Build.VERSION.SDK_INT >= 28) animated?.stop()
            if (showingAnimated) {
                showingAnimated = false
                show(static)
            }
        }

        private fun showAnimated() {
            if (!showingAnimated) {
                showingAnimated = true
                show(animated)
            }
        }

        // -- interaction target wiring --------------------------------------

        private fun resolveTarget(): View = InteractionWiring.resolveTarget(view)

        private fun isWithin(child: View, ancestor: View?): Boolean =
            InteractionWiring.isWithin(child, ancestor)

        private fun installTargetListeners() {
            removeTargetListeners()
            val resolved = resolveTarget()
            target = resolved
            wiring = InteractionWiring.of(resolved).also { it.attach(this) }
            // Seed from the live state in case the pointer/focus is already here.
            hovered = resolved.isHovered
            focused = resolved.isFocused || view.isFocused
            val observer = view.viewTreeObserver
            if (observer.isAlive) {
                observer.addOnGlobalFocusChangeListener(focusListener)
                focusObserver = observer
            }
        }

        private fun removeTargetListeners() {
            wiring?.detach(this)
            wiring = null
            target = null
            focusObserver?.let { if (it.isAlive) it.removeOnGlobalFocusChangeListener(focusListener) }
            focusObserver = null
        }

        // -- attach / lifecycle ---------------------------------------------

        override fun onViewAttachedToWindow(v: View) {
            owner = view.findViewTreeLifecycleOwner()
            owner?.lifecycle?.addObserver(this)
            installTargetListeners()
        }

        override fun onViewDetachedFromWindow(v: View) {
            hovered = false
            pressed = false
            focused = false
            stopNow()
            removeTargetListeners()
            owner?.lifecycle?.removeObserver(this)
            owner = null
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
            removeTargetListeners()
            owner?.lifecycle?.removeObserver(this)
            owner = null
            view.removeOnAttachStateChangeListener(this)
            if (Build.VERSION.SDK_INT >= 28) animated?.clearAnimationCallbacks()
            animated = null
        }
    }

}
