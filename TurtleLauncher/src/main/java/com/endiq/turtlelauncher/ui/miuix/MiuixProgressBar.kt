package com.endiq.turtlelauncher.ui.miuix

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ProgressBar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Exact Android View port of Miuix `LinearProgressIndicator` and `CircularProgressIndicator`
 * (`top.yukonga.miuix.kmp.basic.ProgressIndicator`).
 *
 * Subclasses [android.widget.ProgressBar] so ViewBinding and all callers expecting `ProgressBar`
 * work without any type changes.
 */
class MiuixProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.progressBarStyle
) : ProgressBar(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rectF = RectF()
    private val pillRect = RectF()
    private val clipPath = Path()

    private var explicitHorizontal = false
    private var animFraction = 0f
    private var loopAnimator: ValueAnimator? = null

    init {
        if (attrs != null) {
            val styleAttr = attrs.styleAttribute
            if (styleAttr == android.R.attr.progressBarStyleHorizontal) {
                explicitHorizontal = true
            }
            val a = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.indeterminateOnly))
            val indetOnly = a.getBoolean(0, true)
            a.recycle()
            if (!indetOnly) {
                explicitHorizontal = true
            }
        }
        if (defStyleAttr == android.R.attr.progressBarStyleHorizontal) {
            explicitHorizontal = true
        }
    }

    private fun isHorizontalBar(): Boolean {
        if (explicitHorizontal || !isIndeterminate) return true
        val w = width - paddingLeft - paddingRight
        val h = height - paddingTop - paddingBottom
        return w > 0 && h > 0 && w > h * 2.2f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateLoopAnimator()
    }

    override fun onDetachedFromWindow() {
        loopAnimator?.cancel()
        loopAnimator = null
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        updateLoopAnimator()
    }

    override fun setIndeterminate(indeterminate: Boolean) {
        super.setIndeterminate(indeterminate)
        updateLoopAnimator()
    }

    private fun updateLoopAnimator() {
        val shouldAnimate = isAttachedToWindow && visibility == VISIBLE && isIndeterminate
        if (shouldAnimate) {
            if (loopAnimator?.isRunning == true) return
            loopAnimator?.cancel()
            loopAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1200L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    animFraction = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            loopAnimator?.cancel()
            loopAnimator = null
        }
    }

    @Synchronized
    override fun onDraw(canvas: Canvas) {
        val colors = MiuixTheme.colors(context)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val fgColor = if (isEnabled) colors.primary else colors.disabledPrimarySlider
        val bgColor = if (isEnabled) colors.sliderBackground else colors.disabledSecondary

        if (isHorizontalBar()) {
            drawLinearIndicator(canvas, w, h, fgColor, bgColor)
        } else {
            drawCircularIndicator(canvas, w, h, fgColor, bgColor)
        }
    }

    private fun drawLinearIndicator(
        canvas: Canvas,
        w: Float,
        h: Float,
        fgColor: Int,
        bgColor: Int
    ) {
        val left = paddingLeft.toFloat()
        val right = (w - paddingRight).coerceAtLeast(left + 1f)
        val availH = (h - paddingTop - paddingBottom).coerceAtLeast(4f * density)
        val barHeight = min(availH, 8f * density).coerceAtLeast(4f * density)
        val top = paddingTop + (availH - barHeight) / 2f
        val bottom = top + barHeight
        val barWidth = right - left
        val cornerRadius = barHeight / 2f

        rectF.set(left, top, right, bottom)
        clipPath.reset()
        clipPath.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)

        val saveCount = canvas.save()
        canvas.clipPath(clipPath)

        trackPaint.style = Paint.Style.FILL
        trackPaint.color = bgColor
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, trackPaint)

        indicatorPaint.style = Paint.Style.FILL
        indicatorPaint.color = fgColor

        if (!isIndeterminate) {
            val mn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) min else 0
            val mx = max
            val span = (mx - mn).coerceAtLeast(1)
            val fraction = ((progress - mn).toFloat() / span.toFloat()).coerceIn(0f, 1f)
            val minWidth = min(barHeight, barWidth)
            val fillWidth = minWidth + (barWidth - minWidth) * fraction
            pillRect.set(left, top, left + fillWidth, bottom)
            canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, indicatorPaint)
        } else {
            // Miuix 3-segment orbiting capsule animation (`ProgressIndicator.kt`)
            val positions = floatArrayOf(
                animFraction,
                (animFraction + 1f / 3f) % 1f,
                (animFraction + 2f / 3f) % 1f
            )
            for (pos in positions) {
                val s = sin(pos * PI.toFloat())
                val halfW = 0.06f + 0.19f * s * s
                for (shift in -1..1) {
                    val center = pos + shift
                    val segStart = (center - halfW) * barWidth
                    val segEnd = (center + halfW) * barWidth
                    if (segEnd > 0f && segStart < barWidth) {
                        val sLeft = left + max(0f, segStart)
                        val sRight = left + min(barWidth, segEnd)
                        val pillW = max(sRight - sLeft, barHeight)
                        pillRect.set(sLeft, top, min(right, sLeft + pillW), bottom)
                        canvas.drawRoundRect(pillRect, cornerRadius, cornerRadius, indicatorPaint)
                    }
                }
            }
        }

        canvas.restoreToCount(saveCount)
    }

    private fun drawCircularIndicator(
        canvas: Canvas,
        w: Float,
        h: Float,
        fgColor: Int,
        bgColor: Int
    ) {
        val availW = (w - paddingLeft - paddingRight).coerceAtLeast(1f)
        val availH = (h - paddingTop - paddingBottom).coerceAtLeast(1f)
        val size = min(availW, availH)
        val strokeWidth = (size * 0.11f).coerceIn(2.5f * density, 4.5f * density)
        val cx = paddingLeft + availW / 2f
        val cy = paddingTop + availH / 2f
        val radius = (size - strokeWidth) / 2f
        if (radius <= 0f) return

        rectF.set(cx - radius, cy - radius, cx + radius, cy + radius)

        trackPaint.style = Paint.Style.STROKE
        trackPaint.strokeWidth = strokeWidth
        trackPaint.color = bgColor
        canvas.drawOval(rectF, trackPaint)

        indicatorPaint.style = Paint.Style.STROKE
        indicatorPaint.strokeWidth = strokeWidth
        indicatorPaint.strokeCap = Paint.Cap.ROUND
        indicatorPaint.color = fgColor

        if (!isIndeterminate) {
            val mn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) min else 0
            val mx = max
            val span = (mx - mn).coerceAtLeast(1)
            val fraction = ((progress - mn).toFloat() / span.toFloat()).coerceIn(0f, 1f)
            val sweep = 360f * fraction
            if (sweep > 0.5f) {
                canvas.drawArc(rectF, -90f, sweep, false, indicatorPaint)
            }
        } else {
            // Miuix CircularProgressIndicator: rotating arc + orbiting inner dot
            val rotation = animFraction * 360f
            val breath = (sin(animFraction * 2f * PI.toFloat()) + 1f) / 2f
            val sweep = 100f + 130f * breath
            val startAngle = rotation - 90f
            canvas.drawArc(rectF, startAngle, sweep, false, indicatorPaint)

            // Orbiting dot inside the arc gap (signature Miuix circular spinner detail)
            val orbitRadius = (radius - strokeWidth * 1.4f).coerceAtLeast(strokeWidth)
            val gapCenterAngleDeg = startAngle + sweep + (360f - sweep) / 2f
            val gapRad = Math.toRadians(gapCenterAngleDeg.toDouble())
            val dotX = cx + orbitRadius * cos(gapRad).toFloat()
            val dotY = cy + orbitRadius * sin(gapRad).toFloat()
            dotPaint.color = fgColor
            canvas.drawCircle(dotX, dotY, strokeWidth * 0.45f, dotPaint)
        }
    }
}
