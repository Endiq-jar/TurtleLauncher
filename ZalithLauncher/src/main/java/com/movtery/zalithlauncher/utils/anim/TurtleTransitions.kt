package com.movtery.zalithlauncher.utils.anim

import android.view.View
import com.movtery.anim.AnimCallback
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.task.TaskExecutors

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
