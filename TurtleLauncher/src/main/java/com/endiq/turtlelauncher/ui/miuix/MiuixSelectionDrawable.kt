package com.endiq.turtlelauncher.ui.miuix

import android.animation.ArgbEvaluator
import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Exact Android Drawable port of Miuix `Checkbox` (`top.yukonga.miuix.kmp.basic.Checkbox`)
 * and `RadioButton`:
 * - Circular `22dp` indicator (`CircleShape`)
 * - Unchecked fill: `secondary` (`#505050` dark / `#E6E6E6` light)
 * - Checked fill: `primary` (pastel green `#8FD9A8`)
 * - Animated white round-cap checkmark (`(7.59, 12.08) -> (10.3, 14.9) -> (15.48, 8.22)` in 23x23 viewport)
 *   or animated inner radio circle.
 */
class MiuixSelectionDrawable(
    private val context: Context,
    private val isRadio: Boolean = false,
    sizeDp: Float = 22f
) : Drawable() {

    private val density = context.resources.displayMetrics.density
    private val sizePx = (sizeDp * density).roundToInt()
    private val padPx = (2f * density).roundToInt()
    private val argbEvaluator = ArgbEvaluator()

    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateSelf()
        }

    var isControlEnabled: Boolean = true
        set(value) {
            field = value
            invalidateSelf()
        }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = if (isRadio) Paint.Style.FILL else Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fullCheckPath = Path()
    private val trimmedCheckPath = Path()
    private val pathMeasure = PathMeasure()

    override fun getIntrinsicWidth(): Int = sizePx + padPx * 2
    override fun getIntrinsicHeight(): Int = sizePx + padPx * 2

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val colors = MiuixTheme.colors(context)

        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val diameter = min(b.width(), b.height()).toFloat() - padPx * 2f
        val radius = (diameter / 2f).coerceAtLeast(1f)

        val p = progress.coerceIn(0f, 1f)
        val uncheckedBg = if (isControlEnabled) colors.secondary else colors.disabledSecondary
        val checkedBg = if (isControlEnabled) colors.primary else colors.disabledPrimary
        bgPaint.color = argbEvaluator.evaluate(p, uncheckedBg, checkedBg) as Int
        canvas.drawCircle(cx, cy, radius, bgPaint)

        if (p > 0.01f) {
            val fgColor = if (isControlEnabled) colors.onPrimary else colors.disabledOnPrimary
            fgPaint.color = fgColor

            if (isRadio) {
                fgPaint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, radius * 0.44f * p, fgPaint)
            } else {
                fgPaint.style = Paint.Style.STROKE
                fgPaint.strokeWidth = diameter * 0.095f

                val left = cx - radius
                val top = cy - radius
                val scale = diameter / 23f

                fullCheckPath.reset()
                fullCheckPath.moveTo(left + 7.59f * scale, top + 12.08f * scale)
                fullCheckPath.lineTo(left + 10.3f * scale, top + 14.9f * scale)
                fullCheckPath.lineTo(left + 15.48f * scale, top + 8.22f * scale)

                pathMeasure.setPath(fullCheckPath, false)
                val length = pathMeasure.length
                trimmedCheckPath.reset()
                pathMeasure.getSegment(0f, length * p, trimmedCheckPath, true)
                canvas.drawPath(trimmedCheckPath, fgPaint)
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        bgPaint.alpha = alpha
        fgPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        bgPaint.colorFilter = colorFilter
        fgPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
