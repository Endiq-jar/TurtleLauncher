package com.movtery.anim.animations

import android.animation.Animator
import android.view.View
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.TimeInterpolator
import android.view.animation.AnticipateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator


/**
 * Base for every animation in the launcher.
 *
 * TurtleLauncher rewrite. The originals were straight from daimajia/AndroidViewAnimations and
 * had three problems that showed up badly in a full-screen launcher UI:
 *
 * 1. **Fixed pixel distances** (`100f`, `60f`, `120f`). A 100px slide is a big shove on a
 *    small phone and barely a twitch on a tablet, so the same animation read completely
 *    differently per device. Travel is now a fraction of the screen, clamped to dp.
 * 2. **Distances taken from `target.width / 4`** - which is **0** before the view is laid
 *    out. Every directional Fade silently degraded to a plain fade in exactly the situation
 *    it's used for (a fragment entering), so most of that family never actually moved.
 * 3. **No interpolators at all.** Everything ran on the platform default, and "bounce" was
 *    faked with hand-written keyframes (`60f, -12f, 6f, 0f`) linearly interpolated - a
 *    jitter, not a bounce. Curves are now real: Material decelerate on the way in,
 *    accelerate on the way out, and a genuine overshoot-and-settle for bounce.
 */
abstract class BaseAnimator {

    abstract fun getAnimators(target: View): Array<Animator>

    // ============================== Easing ==============================

    /** Material "decelerate" - fast start, gentle landing. Right for anything arriving. */
    protected val easeOut: TimeInterpolator = PathInterpolator(0.0f, 0.0f, 0.2f, 1.0f)

    /** Material "accelerate" - slow start, leaves quickly. Right for anything departing. */
    protected val easeIn: TimeInterpolator = PathInterpolator(0.4f, 0.0f, 1.0f, 1.0f)

    /** Material "standard" - for moves that neither arrive nor leave (pulse, wobble). */
    protected val easeStandard: TimeInterpolator = PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f)

    protected val linear: TimeInterpolator = LinearInterpolator()

    /** Overshoots the target and springs back - a real bounce, unlike the old keyframes. */
    protected fun overshoot(tension: Float = 1.4f): TimeInterpolator = OvershootInterpolator(tension)

    /** Pulls back briefly before moving - gives a shrink a little "puff" first. */
    protected fun anticipate(tension: Float = 2.0f): TimeInterpolator = AnticipateInterpolator(tension)

    // ============================= Distances =============================

    protected fun dp(target: View, value: Float): Float =
        value * target.resources.displayMetrics.density

    /**
     * How far a slide should travel: a fraction of the **screen's** shorter edge (not the
     * view's - see problem 2 above), clamped so it stays sensible across phone/tablet.
     */
    protected fun travel(
        target: View,
        fraction: Float = 0.30f,
        minDp: Float = 140f,
        maxDp: Float = 420f,
    ): Float {
        val dm = target.resources.displayMetrics
        val base = minOf(dm.widthPixels, dm.heightPixels).toFloat()
        val min = dp(target, minDp)
        val max = dp(target, maxDp)
        return (base * fraction).coerceIn(min, max)
    }

    /** A shorter hop, for the fade family's subtle drift. */
    protected fun drift(target: View): Float = travel(target, 0.11f, 48f, 160f)

    protected fun screenHeight(target: View): Float =
        target.resources.displayMetrics.heightPixels.toFloat()

    protected fun screenWidth(target: View): Float =
        target.resources.displayMetrics.widthPixels.toFloat()

    // ============================== Builders ==============================

    protected fun fadeIn(target: View): ObjectAnimator =
        ObjectAnimator.ofFloat(target, "alpha", 0f, 1f).apply { interpolator = easeOut }

    protected fun fadeOut(target: View): ObjectAnimator =
        ObjectAnimator.ofFloat(target, "alpha", 1f, 0f).apply { interpolator = easeIn }

    protected fun translate(
        target: View,
        property: String,
        from: Float,
        to: Float,
        interpolator: TimeInterpolator = easeOut,
    ): ObjectAnimator =
        ObjectAnimator.ofFloat(target, property, from, to).apply { this.interpolator = interpolator }

    protected fun scale(
        target: View,
        from: Float,
        to: Float,
        interpolator: TimeInterpolator,
    ): Array<ObjectAnimator> = arrayOf(
        ObjectAnimator.ofFloat(target, "scaleX", from, to).apply { this.interpolator = interpolator },
        ObjectAnimator.ofFloat(target, "scaleY", from, to).apply { this.interpolator = interpolator },
    )

    /**
     * Slides `target` from [fromPx] back to rest **with a bounce that settles**.
     *
     * The bounce amplitude is a fixed dp value, NOT a fraction of the travel. That matters:
     * a proportional overshoot on a bottom sheet crossing the entire screen would fling it
     * hundreds of pixels past the top, while a small panel would barely twitch. Fixed dp
     * means a sheet and a 200px panel both settle by the same tasteful amount.
     *
     * Timing is encoded in the keyframe fractions and run on a [LinearInterpolator], so the
     * curve is fully controlled here rather than being distorted by a second easing pass.
     */
    protected fun slideWithSettle(
        target: View,
        property: String,
        fromPx: Float,
        bounceDp: Float = 14f,
    ): ObjectAnimator {
        val bounce = dp(target, bounceDp)
        return ObjectAnimator.ofPropertyValuesHolder(
            target,
            PropertyValuesHolder.ofKeyframe(
                property,
                Keyframe.ofFloat(0.00f, fromPx),
                Keyframe.ofFloat(0.62f, fromPx * 0.12f), // covers most of the ground early
                Keyframe.ofFloat(0.78f, -bounce),        // overshoots past rest
                Keyframe.ofFloat(0.89f, bounce * 0.42f),
                Keyframe.ofFloat(0.96f, -bounce * 0.16f),
                Keyframe.ofFloat(1.00f, 0f),
            )
        ).apply { interpolator = linear }
    }
}
