package com.endiq.turtlelauncher.ui.miuix

import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.animation.LinearInterpolator
import androidx.annotation.ColorInt
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Drawable that renders a G2-continuous Miuix Squircle surface (`SquirclePath`) with
 * optional stroke and integrated `MiuixIndication` state overlay (`HOVER = 0.06f`,
 * `FOCUS = 0.08f`, `PRESS = 0.10f` from `top.yukonga.miuix.kmp.utils.MiuixIndication`).
 */
class MiuixSquircleDrawable(
    @ColorInt fillColor: Int,
    cornerRadiusPx: Float,
    @ColorInt strokeColor: Int = Color.TRANSPARENT,
    strokeWidthPx: Float = 0f,
    @ColorInt indicationBaseColor: Int = Color.WHITE,
    var enableIndication: Boolean = true
) : Drawable() {

    private var fillColorList: ColorStateList = ColorStateList.valueOf(fillColor)
    private var strokeColorList: ColorStateList = ColorStateList.valueOf(strokeColor)

    var cornerRadius: Float = cornerRadiusPx
        set(value) {
            if (field != value) {
                field = value
                rebuildPaths()
                invalidateSelf()
            }
        }

    var strokeWidth: Float = strokeWidthPx
        set(value) {
            if (field != value) {
                field = value
                strokePaint.strokeWidth = value
                rebuildPaths()
                invalidateSelf()
            }
        }

    @ColorInt
    var indicationColor: Int = indicationBaseColor
        set(value) {
            field = value
            invalidateSelf()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        color = strokeColor
    }

    private val indicationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val fillPath = Path()
    private val strokePath = Path()
    private val boundsF = RectF()
    private val strokeBoundsF = RectF()

    private var currentIndicationAlpha = 0f
    private var targetIndicationAlpha = 0f
    private var indicationAnimator: ValueAnimator? = null

    fun setFillColor(@ColorInt color: Int) {
        fillColorList = ColorStateList.valueOf(color)
        fillPaint.color = color
        invalidateSelf()
    }

    fun setFillColorStateList(colors: ColorStateList) {
        fillColorList = colors
        fillPaint.color = colors.getColorForState(state, colors.defaultColor)
        invalidateSelf()
    }

    fun setStroke(@ColorInt color: Int, widthPx: Float) {
        strokeColorList = ColorStateList.valueOf(color)
        strokePaint.color = color
        strokeWidth = widthPx
        rebuildPaths()
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        boundsF.set(bounds)
        rebuildPaths()
    }

    private fun rebuildPaths() {
        if (boundsF.isEmpty) return
        MiuixSquircle.buildPath(boundsF, cornerRadius, MiuixSquircle.SQUIRCLE_EXTENSION, fillPath)
        if (strokeWidth > 0f) {
            val inset = strokeWidth / 2f
            strokeBoundsF.set(
                boundsF.left + inset,
                boundsF.top + inset,
                boundsF.right - inset,
                boundsF.bottom - inset
            )
            val strokeRadius = (cornerRadius - inset).coerceAtLeast(0f)
            MiuixSquircle.buildPath(strokeBoundsF, strokeRadius, MiuixSquircle.SQUIRCLE_EXTENSION, strokePath)
        }
    }

    override fun isStateful(): Boolean = true

    override fun onStateChange(stateSet: IntArray): Boolean {
        var changed = false
        val newFill = fillColorList.getColorForState(stateSet, fillColorList.defaultColor)
        if (fillPaint.color != newFill) {
            fillPaint.color = newFill
            changed = true
        }
        val newStroke = strokeColorList.getColorForState(stateSet, strokeColorList.defaultColor)
        if (strokePaint.color != newStroke) {
            strokePaint.color = newStroke
            changed = true
        }

        if (enableIndication) {
            var isEnabled = false
            var isPressed = false
            var isHovered = false
            var isFocused = false
            for (s in stateSet) {
                when (s) {
                    android.R.attr.state_enabled -> isEnabled = true
                    android.R.attr.state_pressed -> isPressed = true
                    android.R.attr.state_hovered -> isHovered = true
                    android.R.attr.state_focused -> isFocused = true
                }
            }
            var newTarget = 0f
            if (isEnabled) {
                if (isHovered) newTarget += MiuixTheme.IndicationAlpha.HOVER
                if (isFocused) newTarget += MiuixTheme.IndicationAlpha.FOCUS
                if (isPressed) newTarget += MiuixTheme.IndicationAlpha.PRESS
            }
            newTarget = newTarget.coerceIn(0f, 1f)
            if (newTarget != targetIndicationAlpha) {
                animateIndicationTo(newTarget)
                changed = true
            }
        }
        return changed || super.onStateChange(stateSet)
    }

    private fun animateIndicationTo(target: Float) {
        targetIndicationAlpha = target
        indicationAnimator?.cancel()
        val start = currentIndicationAlpha
        if (start == target) return
        indicationAnimator = ValueAnimator.ofFloat(start, target).apply {
            duration = 150L
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                currentIndicationAlpha = anim.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    override fun draw(canvas: Canvas) {
        if (boundsF.isEmpty) return
        if (Color.alpha(fillPaint.color) > 0) {
            canvas.drawPath(fillPath, fillPaint)
        }
        if (enableIndication && currentIndicationAlpha > 0.001f) {
            val baseAlpha = Color.alpha(indicationColor) / 255f
            val alphaInt = (currentIndicationAlpha * baseAlpha * 255f).roundToInt().coerceIn(0, 255)
            if (alphaInt > 0) {
                indicationPaint.color = Color.argb(
                    alphaInt,
                    Color.red(indicationColor),
                    Color.green(indicationColor),
                    Color.blue(indicationColor)
                )
                canvas.drawPath(fillPath, indicationPaint)
            }
        }
        if (strokeWidth > 0f && Color.alpha(strokePaint.color) > 0) {
            canvas.drawPath(strokePath, strokePaint)
        }
    }

    override fun getOutline(outline: Outline) {
        val b = bounds
        if (b.isEmpty) return
        val r = cornerRadius.coerceIn(0f, min(b.width(), b.height()) / 2f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !fillPath.isEmpty) {
            outline.setPath(fillPath)
        } else {
            outline.setRoundRect(b, r)
        }
        outline.alpha = (Color.alpha(fillPaint.color) / 255f).coerceIn(0f, 1f)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
