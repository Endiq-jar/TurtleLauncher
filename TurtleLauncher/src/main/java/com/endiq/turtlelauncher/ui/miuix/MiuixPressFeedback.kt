package com.endiq.turtlelauncher.ui.miuix

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Implements Miuix's `SinkFeedback` (`top.yukonga.miuix.kmp.utils.PressFeedback`)
 * and `MiuixIndication` (`top.yukonga.miuix.kmp.utils.MiuixIndication`) for Android Views.
 *
 * Importantly, this helper operates via [onTouchEvent], [onHoverEvent], [onDrawableStateChanged],
 * and [drawIndication] called from the owning View subclass — it NEVER installs an external
 * `OnTouchListener` or `OnHoverListener`, preserving `InteractionWiring` compatibility.
 */
class MiuixPressFeedback(
    private val view: View,
    var sinkScale: Float = 0.96f,
    var damping: Float = 0.85f,
    var response: Float = 0.28f,
    var cornerRadiusDp: Float = MiuixTheme.CornerRadiiDp.CARD,
    var enableIndication: Boolean = true,
    var enableSink: Boolean = true
) {
    private var isPressedState = false
    private var isHoveredState = false
    private var isFocusedState = false

    private var currentIndicationAlpha = 0f
    private var targetIndicationAlpha = 0f
    private var indicationAnimator: ValueAnimator? = null

    private val indicationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val squirclePath = Path()

    fun onTouchEvent(event: MotionEvent?) {
        if (!view.isEnabled) return
        when (event?.actionMasked) {
            MotionEvent.ACTION_DOWN -> setPressedInternal(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> setPressedInternal(false)
        }
    }

    fun onHoverEvent(event: MotionEvent?) {
        if (!view.isEnabled) return
        when (event?.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER -> setHoveredInternal(true)
            MotionEvent.ACTION_HOVER_EXIT -> setHoveredInternal(false)
        }
    }

    fun onDrawableStateChanged() {
        val state = view.drawableState
        var pressed = false
        var hovered = false
        var focused = false
        var enabled = false
        for (s in state) {
            when (s) {
                android.R.attr.state_pressed -> pressed = true
                android.R.attr.state_hovered -> hovered = true
                android.R.attr.state_focused -> focused = true
                android.R.attr.state_enabled -> enabled = true
            }
        }
        if (!enabled) {
            pressed = false
            hovered = false
            focused = false
        }
        val stateChanged = (pressed != isPressedState) || (hovered != isHoveredState) || (focused != isFocusedState)
        isPressedState = pressed
        isHoveredState = hovered
        isFocusedState = focused
        if (stateChanged) {
            updateScaleAndIndication()
        }
    }

    private fun setPressedInternal(pressed: Boolean) {
        if (isPressedState != pressed) {
            isPressedState = pressed
            updateScaleAndIndication()
        }
    }

    private fun setHoveredInternal(hovered: Boolean) {
        if (isHoveredState != hovered) {
            isHoveredState = hovered
            updateScaleAndIndication()
        }
    }

    private fun updateScaleAndIndication() {
        if (enableSink && view.isEnabled && (view.isClickable || view.isLongClickable || view.isFocusable)) {
            val targetScale = when {
                isPressedState -> sinkScale
                else -> 1f
            }
            MiuixSpring.animateScale(view, targetScale, damping, response)
        } else if (view.scaleX != 1f || view.scaleY != 1f) {
            MiuixSpring.animateScale(view, 1f, damping, response)
        }

        if (enableIndication) {
            var newAlpha = 0f
            if (view.isEnabled && (view.isClickable || view.isLongClickable || view.isFocusable)) {
                if (isHoveredState) newAlpha += MiuixTheme.IndicationAlpha.HOVER
                if (isFocusedState) newAlpha += MiuixTheme.IndicationAlpha.FOCUS
                if (isPressedState) newAlpha += MiuixTheme.IndicationAlpha.PRESS
            }
            newAlpha = newAlpha.coerceIn(0f, 1f)
            if (newAlpha != targetIndicationAlpha) {
                targetIndicationAlpha = newAlpha
                indicationAnimator?.cancel()
                val startAlpha = currentIndicationAlpha
                indicationAnimator = ValueAnimator.ofFloat(startAlpha, newAlpha).apply {
                    duration = 150L
                    interpolator = LinearInterpolator()
                    addUpdateListener { anim ->
                        currentIndicationAlpha = anim.animatedValue as Float
                        view.invalidate()
                    }
                    start()
                }
            }
        }
    }

    /**
     * Draws the Miuix indication overlay clipped to a G2-continuous squircle.
     */
    fun drawIndication(canvas: Canvas) {
        if (!enableIndication || currentIndicationAlpha <= 0.001f) return
        val w = view.width.toFloat()
        val h = view.height.toFloat()
        if (w <= 0f || h <= 0f) return

        val onSurface = MiuixTheme.colors(view.context).onSurface
        val baseAlpha = Color.alpha(onSurface) / 255f
        val alphaInt = (currentIndicationAlpha * baseAlpha * 255f).roundToInt().coerceIn(0, 255)
        if (alphaInt <= 0) return

        indicationPaint.color = Color.argb(
            alphaInt,
            Color.red(onSurface),
            Color.green(onSurface),
            Color.blue(onSurface)
        )
        val radiusPx = min(MiuixTheme.dpToPx(view.context, cornerRadiusDp), min(w, h) / 2f)
        MiuixSquircle.buildPath(w, h, radiusPx, MiuixSquircle.SQUIRCLE_EXTENSION, squirclePath)
        canvas.drawPath(squirclePath, indicationPaint)
    }
}
