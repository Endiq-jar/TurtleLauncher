package com.endiq.turtlelauncher.ui.miuix

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Exact port of `top.yukonga.miuix.kmp.anim.MiuixEasing` and `top.yukonga.miuix.kmp.utils.SpringUtils`
 * (https://github.com/compose-miuix-ui/miuix).
 */
object MiuixSpring {
    const val DEFAULT_DAMPING = 0.85f
    const val DEFAULT_RESPONSE = 0.3f

    /**
     * Converts Folme `(damping, response)` parameters into physical spring stiffness:
     * `stiffness = (2 * PI / response)^2`.
     */
    @JvmStatic
    fun stiffnessFromResponse(response: Float): Float {
        val r = response.coerceAtLeast(0.05f)
        val omega = (2.0 * PI / r).toFloat()
        return omega * omega
    }

    /**
     * Evaluates an analytical damped harmonic oscillator step response at time [seconds].
     */
    @JvmStatic
    fun evaluateSpring(
        seconds: Float,
        dampingRatio: Float = DEFAULT_DAMPING,
        stiffness: Float = stiffnessFromResponse(DEFAULT_RESPONSE)
    ): Float {
        if (seconds <= 0f) return 0f
        val zeta = dampingRatio.coerceAtLeast(0.05f)
        val omega0 = sqrt(stiffness.coerceAtLeast(1f))
        return if (zeta < 0.999f) {
            val omegaD = omega0 * sqrt(1f - zeta * zeta)
            val envelope = exp(-zeta * omega0 * seconds)
            val harmonic = cos(omegaD * seconds) + (zeta * omega0 / omegaD) * sin(omegaD * seconds)
            1f - envelope * harmonic
        } else {
            val envelope = exp(-omega0 * seconds)
            1f - (1f + omega0 * seconds) * envelope
        }
    }

    /**
     * Creates an Android [TimeInterpolator] using Folme spring parameters `(damping, response)`.
     */
    @JvmStatic
    @JvmOverloads
    fun folmeInterpolator(
        damping: Float = DEFAULT_DAMPING,
        response: Float = DEFAULT_RESPONSE,
        durationMs: Long = (response * 1100f).toLong().coerceIn(180L, 650L)
    ): TimeInterpolator {
        val stiffness = stiffnessFromResponse(response)
        val totalSeconds = (durationMs / 1000f).coerceAtLeast(0.1f)
        return TimeInterpolator { fraction ->
            if (fraction <= 0f) 0f
            else if (fraction >= 1f) 1f
            else evaluateSpring(fraction * totalSeconds, damping, stiffness)
        }
    }

    /**
     * Creates an Android [TimeInterpolator] from raw `(dampingRatio, stiffness)` parameters
     * (matching Compose `spring(dampingRatio, stiffness)` in Miuix components).
     */
    @JvmStatic
    @JvmOverloads
    fun springInterpolator(
        dampingRatio: Float,
        stiffness: Float,
        durationMs: Long = 320L
    ): TimeInterpolator {
        val totalSeconds = (durationMs / 1000f).coerceAtLeast(0.1f)
        return TimeInterpolator { fraction ->
            if (fraction <= 0f) 0f
            else if (fraction >= 1f) 1f
            else evaluateSpring(fraction * totalSeconds, dampingRatio, stiffness)
        }
    }

    /**
     * Exact port of `DecelerateEasing(degree)` from `MiuixEasing.kt`.
     */
    @JvmStatic
    @JvmOverloads
    fun decelerateEasing(degree: Float = 1.5f): TimeInterpolator {
        val exponent = 2f * degree
        return TimeInterpolator { fraction ->
            1f - (1f - fraction.coerceIn(0f, 1f)).pow(exponent)
        }
    }

    /**
     * Exact port of `AccelerateEasing(degree)` from `MiuixEasing.kt`.
     */
    @JvmStatic
    @JvmOverloads
    fun accelerateEasing(degree: Float = 1.5f): TimeInterpolator {
        val exponent = 2f * degree
        return TimeInterpolator { fraction ->
            fraction.coerceIn(0f, 1f).pow(exponent)
        }
    }

    /**
     * Exact port of `SinOutEasing` from `MiuixEasing.kt`.
     */
    @JvmStatic
    fun sinOutEasing(): TimeInterpolator {
        return TimeInterpolator { fraction ->
            sin(fraction.coerceIn(0f, 1f) * PI.toFloat() / 2f)
        }
    }

    /**
     * Animates a float property with a Folme spring curve.
     */
    @JvmStatic
    @JvmOverloads
    fun animateFloat(
        from: Float,
        to: Float,
        dampingRatio: Float = DEFAULT_DAMPING,
        stiffness: Float = stiffnessFromResponse(DEFAULT_RESPONSE),
        durationMs: Long = 300L,
        onUpdate: (Float) -> Unit
    ): ValueAnimator {
        return ValueAnimator.ofFloat(from, to).apply {
            duration = durationMs
            interpolator = springInterpolator(dampingRatio, stiffness, durationMs)
            addUpdateListener { anim ->
                onUpdate(anim.animatedValue as Float)
            }
            start()
        }
    }

    /**
     * Animates a [View]'s scale with Miuix `SinkFeedback` spring physics.
     */
    @JvmStatic
    @JvmOverloads
    fun animateScale(
        view: View,
        targetScale: Float,
        damping: Float = DEFAULT_DAMPING,
        response: Float = DEFAULT_RESPONSE
    ) {
        val durationMs = (response * 1000f).toLong().coerceIn(180L, 420L)
        view.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .setDuration(durationMs)
            .setInterpolator(folmeInterpolator(damping, response, durationMs))
            .start()
    }
}
