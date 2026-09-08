package com.movtery.zalithlauncher.utils.anim

import android.view.View
import com.movtery.anim.AnimCallback
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.task.TaskExecutors
import android.content.Context
import android.view.animation.AnimationUtils
import android.view.animation.LayoutAnimationController
import androidx.recyclerview.widget.RecyclerView
import com.movtery.zalithlauncher.R


/**
 * The one place the launcher's screen transitions are defined.
 *
 * Before this, every fragment hardcoded its own pair of [Animations] (about 33 of them, split
 * between `BounceIn*` on the way in and `FadeOut*` on the way out), so there was no way to
 * change the feel of the launcher without editing three dozen files - and zoom was impossible
 * entirely, because the animation library had no zoom animators.
 *
 * Everything that animates a screen or a panel now asks this class for its enter/exit
 * animation instead, so the two Settings pickers (Launcher settings -> Animation) actually
 * apply app-wide.
 */

/** How a screen arrives. Values are stored in AllSettings.animationEnter. */
enum class EnterTransition(val key: String, val animation: Animations) {
    SLIDE_UP("slide_up", Animations.SlideInUp),
    SLIDE_DOWN("slide_down", Animations.SlideInDown),
    SLIDE_LEFT("slide_left", Animations.SlideInLeft),
    SLIDE_RIGHT("slide_right", Animations.SlideInRight),
    BOUNCE("bounce", Animations.BounceInUp),
    FADE_IN("fade_in", Animations.FadeInUp),
    ZOOM_IN("zoom_in", Animations.ZoomInUp),
    ZOOM("zoom", Animations.ZoomIn),
    SHEET("sheet", Animations.SheetIn),
    ;

    companion object {
        fun fromKey(key: String?): EnterTransition =
            values().firstOrNull { it.key == key } ?: SLIDE_UP
    }
}

/** How a screen leaves. Values are stored in AllSettings.animationExit. */
enum class ExitTransition(val key: String, val animation: Animations) {
    SLIDE_UP("slide_up", Animations.SlideOutUp),
    SLIDE_DOWN("slide_down", Animations.SlideOutDown),
    SLIDE_LEFT("slide_left", Animations.SlideOutLeft),
    SLIDE_RIGHT("slide_right", Animations.SlideOutRight),
    BOUNCE("bounce", Animations.BounceShrink),
    FADE_OUT("fade_out", Animations.FadeOutUp),
    ZOOM_OUT("zoom_out", Animations.ZoomOutUp),
    ZOOM("zoom", Animations.ZoomOut),
    SHEET("sheet", Animations.SheetOut),
    ;

    companion object {
        fun fromKey(key: String?): ExitTransition =
            values().firstOrNull { it.key == key } ?: SLIDE_UP
    }
}

object TurtleTransitions {
    /**
     * The single gate for "should we animate right now".
     *
     * A fragment transition can fire while the launcher Activity is stopped behind a live
     * Minecraft session (e.g. an EventBus-driven page swap queued just before the game
     * started). Ticking an AnimatorSet nobody is looking at just burns CPU and GC at the worst
     * possible moment, so an active game session is treated exactly like the user's own
     * "disable animations" toggle.
     */
    @JvmStatic
    fun isEnabled(): Boolean =
        AllSettings.animation.getValue() && !TaskExecutors.isGameSessionActive

    // ============================== Screens ==============================

    @JvmStatic
    fun enter(): Animations = EnterTransition.fromKey(AllSettings.animationEnter.getValue()).animation

    @JvmStatic
    fun exit(): Animations = ExitTransition.fromKey(AllSettings.animationExit.getValue()).animation

    /** Called from FragmentWithAnim and from any fragment that overrides slideIn to animate a
     *  specific sub-panel rather than the whole root. The panel choice stays with the
     *  fragment; only the animation itself is centralised. */
    @JvmStatic
    fun applyEnter(animPlayer: AnimPlayer, view: View) {
        animPlayer.apply(AnimPlayer.Entry(view, enter()))
    }

    @JvmStatic
    fun applyExit(animPlayer: AnimPlayer, view: View) {
        animPlayer.apply(AnimPlayer.Entry(view, exit()))
    }

    // =============================== Views ===============================

    /**
     * Plays the configured enter/exit animation on a standalone view - use this anywhere a
     * view is shown or hidden in place (progress spinners, empty-state panels, the join-code
     * row, ...) so those follow the same style as the screen transitions.
     *
     * Returns false (and does nothing) when animations are off, so callers can skip any
     * work they'd only do to prepare for an animation.
     */
    @JvmStatic
    @JvmOverloads
    fun animateView(
        view: View,
        appearing: Boolean,
        onStart: AnimCallback? = null,
        onEnd: AnimCallback? = null,
    ): Boolean {
        if (!isEnabled()) return false

        val player = AnimPlayer.play().apply(AnimPlayer.Entry(view, if (appearing) enter() else exit()))
        if (onStart != null) player.setOnStart(onStart)
        if (onEnd != null) player.setOnEnd(onEnd)
        player.start()
        return true
    }

    /**
     * The bottom-sheet entrance: rises from the bottom edge of the screen and settles with a
     * bounce, instead of sliding a fixed distance from somewhere mid-screen.
     *
     * Used by the settings screen opened from the home page - a panel you reach *down* to
     * should feel like it came up off the bottom of the display, not like it drifted in from
     * the side. Also selectable globally via the "Sheet" entry in both pickers.
     */
    @JvmStatic
    fun sheetEnter(): Animations = Animations.SheetIn

    @JvmStatic
    fun sheetExit(): Animations = Animations.SheetOut

    /**
     * Plays the enter animation across a group of views, each one a beat after the last.
     *
     * Animating a row of panels all at once reads as one flat block moving; staggering them
     * by a few tens of milliseconds is what makes a screen feel like it assembled itself.
     */
    @JvmStatic
    @JvmOverloads
    fun stagger(animPlayer: AnimPlayer, views: List<View?>, stepMs: Long = 45L) {
        if (!isEnabled()) return
        var index = 0
        for (view in views) {
            if (view == null) continue
            // Each entry gets its own startDelay inside the one AnimatorSet, so the panels
            // arrive one after another instead of as a single flat block.
            val animators = enter().animator.getAnimators(view)
            animators.forEach { it.startDelay = index * stepMs }
            animPlayer.addAll(animators)
            index++
        }
    }

    /** Convenience for callers that aren't inside a fragment's slideIn() and so don't have an
     *  AnimPlayer handed to them. */
    @JvmStatic
    @JvmOverloads
    fun stagger(views: List<View?>, stepMs: Long = 45L) {
        if (!isEnabled()) return
        val player = AnimPlayer.play()
        stagger(player, views, stepMs)
        player.start()
    }

    /**
     * Staggered entry for every row currently on screen in a list. Call once after the
     * adapter's first data set is in (`recyclerView.post { ... }` is usually needed so the
     * children exist) - it animates what's visible and leaves scrolling alone.
     */
    @JvmStatic
    @JvmOverloads
    fun animateList(recyclerView: RecyclerView, stepMs: Long = 28L) {
        if (!isEnabled()) return
        val children = (0 until recyclerView.childCount).mapNotNull { recyclerView.getChildAt(it) }
        stagger(children, stepMs)
    }

    /**
     * The per-item entry animation for a list, built the same way everywhere.
     *
     * `LayoutAnimationController` spreads children by `delay * animationDuration`, and its
     * default delay is 0.5 - so with the 210ms entry in `R.anim.fade_downwards` each row
     * would start ~105ms after the one above it and a 20-row list would still be arriving
     * two seconds later. Pinning the delay to a small fraction keeps the cascade snappy
     * no matter how long the entry animation is.
     */
    @JvmStatic
    fun listLayoutAnimationController(context: Context): LayoutAnimationController =
        TurtleTransitions.listLayoutAnimationController(context).apply {
            delay = 0.12f
            order = LayoutAnimationController.ORDER_NORMAL
        }

    /**
     * Press feedback for views that aren't an `AnimRelativeLayout` (which already carries a
     * press-scale state list animator). Squashes to [scale] on touch and springs back on
     * release, so tapping a plain button feels like tapping something.
     */
    @JvmStatic
    @JvmOverloads
    fun attachPressFeedback(view: View, scale: Float = 0.94f) {
        if (!isEnabled()) return
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(scale).scaleY(scale)
                        .setDuration(110).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f)
                        .setDuration(180)
                        .setInterpolator(android.view.animation.OvershootInterpolator(2.0f)).start()
                    // Let the click still happen.
                    if (event.actionMasked == android.view.MotionEvent.ACTION_UP) v.performClick()
                }
            }
            false
        }
    }

    /** Show/hide a view with the configured transition, including the visibility flip.
     *  No-op if the view is already in the target state or animations are disabled (in which
     *  case the visibility is simply set immediately). */
    @JvmStatic
    fun setVisibilityAnimated(view: View, visible: Boolean) {
        val target = if (visible) View.VISIBLE else View.GONE
        if (view.visibility == target) return

        if (!isEnabled()) {
            view.visibility = target
            return
        }

        view.visibility = View.VISIBLE
        animateView(view, visible, onEnd = { view.visibility = target })
    }
}
