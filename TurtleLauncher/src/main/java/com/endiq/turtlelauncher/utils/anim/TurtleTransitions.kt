package com.endiq.turtlelauncher.utils.anim

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.LayoutAnimationController
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
import com.endiq.anim.AnimPlayer
import com.endiq.anim.animations.Animations
import com.endiq.anim.animations.CustomAnimations
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.miuix.MiuixSpring
import kotlin.math.roundToLong

/**
 * Miuix-tuned fragment, sheet, and list transition animations (`MiuixEasing.kt` & `SpringUtils.kt`).
 */
object TurtleTransitions {
    private val SPRING_IN = MiuixSpring.folmeInterpolator(0.85f, 0.30f, 300L)
    private val SHEET_IN = MiuixSpring.folmeInterpolator(0.88f, 0.28f, 280L)
    private val EASE_OUT_FAST = MiuixSpring.decelerateEasing(1.5f)

    @JvmStatic
    fun enter(): Animations = Animations.Custom(CustomAnimations { view ->
        val density = view.resources.displayMetrics.density
        val dy = 12f * density
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(view, "translationY", dy, 0f),
                ObjectAnimator.ofFloat(view, "scaleX", 0.96f, 1f),
                ObjectAnimator.ofFloat(view, "scaleY", 0.96f, 1f)
            )
            interpolator = SPRING_IN
        }
    })

    @JvmStatic
    fun exit(): Animations = Animations.Custom(CustomAnimations { view ->
        val density = view.resources.displayMetrics.density
        val dy = -6f * density
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "alpha", 1f, 0f),
                ObjectAnimator.ofFloat(view, "translationY", 0f, dy),
                ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.97f),
                ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.97f)
            )
            interpolator = EASE_OUT_FAST
        }
    })

    @JvmStatic
    fun sheetEnter(): Animations = Animations.Custom(CustomAnimations { view ->
        val density = view.resources.displayMetrics.density
        val dy = 20f * density
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(view, "translationY", dy, 0f),
                ObjectAnimator.ofFloat(view, "scaleX", 0.97f, 1f),
                ObjectAnimator.ofFloat(view, "scaleY", 0.97f, 1f)
            )
            interpolator = SHEET_IN
        }
    })

    @JvmStatic
    fun sheetExit(): Animations = Animations.Custom(CustomAnimations { view ->
        val density = view.resources.displayMetrics.density
        val dy = 12f * density
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "alpha", 1f, 0f),
                ObjectAnimator.ofFloat(view, "translationY", 0f, dy),
                ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.97f),
                ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.97f)
            )
            interpolator = EASE_OUT_FAST
        }
    })

    @JvmStatic
    @JvmOverloads
    fun stagger(player: AnimPlayer, views: List<View>, stepMs: Long = 35L): AnimPlayer {
        val scaledStep = (stepMs * AllSettings.animationSpeed.getValue() / 600.0)
            .roundToLong()
            .coerceIn(15L, 70L)
        views.forEachIndexed { index, view ->
            player.apply(
                AnimPlayer.Entry(view, enter()).apply {
                    delay = index * scaledStep
                }
            )
        }
        return player
    }

    @JvmStatic
    fun listLayoutAnimationController(context: Context): LayoutAnimationController {
        val duration = (AllSettings.animationSpeed.getValue() * 0.45)
            .roundToLong()
            .coerceIn(140L, 320L)
        val density = context.resources.displayMetrics.density
        val set = AnimationSet(true).apply {
            interpolator = SPRING_IN
            this.duration = duration
            addAnimation(AlphaAnimation(0f, 1f))
            addAnimation(TranslateAnimation(0f, 0f, 10f * density, 0f))
            addAnimation(
                ScaleAnimation(
                    0.97f, 1f, 0.97f, 1f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f
                )
            )
        }
        return LayoutAnimationController(set, 0.08f).apply {
            order = LayoutAnimationController.ORDER_NORMAL
        }
    }
}
