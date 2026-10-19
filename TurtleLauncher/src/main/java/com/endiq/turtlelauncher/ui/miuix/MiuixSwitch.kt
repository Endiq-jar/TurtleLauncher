package com.endiq.turtlelauncher.ui.miuix

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Switch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Exact Android View port of Miuix `Switch` (`top.yukonga.miuix.kmp.basic.Switch`):
 * - 49dp x 28dp capsule track (`CircleShape`)
 * - 20dp circular thumb (4dp inset from track edges)
 * - Spring-animated thumb offset (`dampingRatio = 0.7f, stiffness = 987f`)
 * - Spring-animated thumb scale (`1.0f -> 1.127f` on press/drag/hover, `dampingRatio = 0.6f, stiffness = 987f`)
 * - Smooth track & thumb color transitions (`secondary -> primary`)
 * - Interactive horizontal drag & tap with haptic feedback
 *
 * Subclasses [android.widget.Switch] so ViewBinding and all callers expecting `Switch`
 * or `CompoundButton` work without any type changes.
 */
@Suppress("DEPRECATION")
class MiuixSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.switchStyle
) : Switch(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val trackWidthPx = 49f * density
    private val trackHeightPx = 28f * density
    private val thumbRadiusRestPx = 10f * density
    private val thumbInsetPx = 4f * density
    private val thumbTravelPx = 21f * density // 25dp - 4dp = 21dp

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val trackRect = RectF()
    private val argbEvaluator = ArgbEvaluator()

    private var thumbProgress: Float = if (isChecked) 1f else 0f
    private var thumbScale: Float = 1f

    private var progressAnimator: ValueAnimator? = null
    private var scaleAnimator: ValueAnimator? = null

    private var isInteracting = false
    private var isHoveredState = false
    private var isDraggingThumb = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var dragStartProgress = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        // Suppress stock Switch track/thumb rendering; we draw the Miuix capsule directly.
        super.setTrackDrawable(ColorDrawable(Color.TRANSPARENT))
        super.setThumbDrawable(ColorDrawable(Color.TRANSPARENT))
        showText = false
        splitTrack = false
        background = null
        isClickable = true
        isFocusable = true
        thumbProgress = if (isChecked) 1f else 0f
    }

    override fun setChecked(checked: Boolean) {
        val changed = isChecked != checked
        super.setChecked(checked)
        val target = if (checked) 1f else 0f
        if (changed && isAttachedToWindow && width > 0) {
            animateThumbProgressTo(target)
        } else {
            progressAnimator?.cancel()
            thumbProgress = target
            invalidate()
        }
    }

    private fun animateThumbProgressTo(target: Float) {
        progressAnimator?.cancel()
        val start = thumbProgress
        if (abs(start - target) < 0.001f) {
            thumbProgress = target
            invalidate()
            return
        }
        // Miuix Switch: spring(dampingRatio = 0.7f, stiffness = 987f)
        progressAnimator = MiuixSpring.animateFloat(
            from = start,
            to = target,
            dampingRatio = 0.7f,
            stiffness = 987f,
            durationMs = 280L
        ) { value ->
            thumbProgress = value
            invalidate()
        }
    }

    private fun updateThumbScale() {
        // Miuix Switch: 1.127f when pressed/dragged/hovered, 1.0f at rest
        // spring(dampingRatio = 0.6f, stiffness = 987f)
        val active = isEnabled && (isInteracting || isDraggingThumb || isHoveredState)
        val targetScale = if (active) 1.127f else 1.0f
        scaleAnimator?.cancel()
        if (abs(thumbScale - targetScale) < 0.001f) {
            thumbScale = targetScale
            invalidate()
            return
        }
        scaleAnimator = MiuixSpring.animateFloat(
            from = thumbScale,
            to = targetScale,
            dampingRatio = 0.6f,
            stiffness = 987f,
            durationMs = 260L
        ) { value ->
            thumbScale = value
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val label = text
        val hasLabel = !TextUtils.isEmpty(label)
        val minW = (trackWidthPx.roundToInt() + paddingLeft + paddingRight).let {
            if (hasLabel) {
                val textW = paint.measureText(label, 0, label.length).roundToInt()
                it + textW + (12f * density).roundToInt()
            } else {
                it
            }
        }
        val minH = max(trackHeightPx.roundToInt() + paddingTop + paddingBottom, suggestedMinimumHeight)
        val measuredW = resolveSize(minW, widthMeasureSpec)
        val measuredH = resolveSize(minH, heightMeasureSpec)
        setMeasuredDimension(measuredW, measuredH)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || !isClickable) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                dragStartProgress = thumbProgress
                isInteracting = true
                isDraggingThumb = false
                updateThumbScale()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - touchDownX
                val dy = event.y - touchDownY
                if (!isDraggingThumb && abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    isDraggingThumb = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    progressAnimator?.cancel()
                }
                if (isDraggingThumb) {
                    val deltaProgress = (if (layoutDirection == LAYOUT_DIRECTION_RTL) -dx else dx) / thumbTravelPx
                    thumbProgress = (dragStartProgress + deltaProgress).coerceIn(0f, 1f)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                val wasDragging = isDraggingThumb
                isInteracting = false
                isDraggingThumb = false
                updateThumbScale()
                if (wasDragging) {
                    val newChecked = thumbProgress >= 0.5f
                    if (newChecked != isChecked) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        isChecked = newChecked
                    } else {
                        animateThumbProgressTo(if (isChecked) 1f else 0f)
                    }
                } else {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                isInteracting = false
                isDraggingThumb = false
                updateThumbScale()
                animateThumbProgressTo(if (isChecked) 1f else 0f)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (isEnabled) {
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    isHoveredState = true
                    updateThumbScale()
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    isHoveredState = false
                    updateThumbScale()
                }
            }
        }
        return super.onHoverEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        val colors = MiuixTheme.colors(context)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val label = text
        val hasLabel = !TextUtils.isEmpty(label)
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL

        val trackLeft: Float
        if (hasLabel) {
            trackLeft = if (isRtl) {
                paddingLeft.toFloat()
            } else {
                w - paddingRight - trackWidthPx
            }
            // Draw inline label
            paint.color = if (isEnabled) colors.onSurface else colors.disabledOnSurface
            paint.isAntiAlias = true
            val textY = h / 2f - (paint.descent() + paint.ascent()) / 2f
            val textX = if (isRtl) {
                trackLeft + trackWidthPx + 12f * density
            } else {
                paddingLeft.toFloat()
            }
            canvas.drawText(label, 0, label.length, textX, textY, paint)
        } else {
            trackLeft = (w - trackWidthPx) / 2f
        }

        val trackTop = (h - trackHeightPx) / 2f
        trackRect.set(trackLeft, trackTop, trackLeft + trackWidthPx, trackTop + trackHeightPx)

        val clampedProgress = thumbProgress.coerceIn(0f, 1f)
        val uncheckedTrack = if (isEnabled) colors.secondary else colors.disabledSecondary
        val checkedTrack = if (isEnabled) colors.primary else colors.disabledPrimary
        trackPaint.color = argbEvaluator.evaluate(clampedProgress, uncheckedTrack, checkedTrack) as Int

        val trackRadius = trackHeightPx / 2f
        canvas.drawRoundRect(trackRect, trackRadius, trackRadius, trackPaint)

        val uncheckedThumb = if (isEnabled) colors.onSecondary else colors.disabledOnSecondary
        val checkedThumb = if (isEnabled) colors.onPrimary else colors.disabledOnPrimary
        thumbPaint.color = argbEvaluator.evaluate(clampedProgress, uncheckedThumb, checkedThumb) as Int

        val effectiveProgress = if (isRtl) 1f - thumbProgress else thumbProgress
        val thumbCenterX = trackLeft + thumbInsetPx + thumbRadiusRestPx + effectiveProgress * thumbTravelPx
        val thumbCenterY = trackTop + trackHeightPx / 2f
        val currentThumbRadius = thumbRadiusRestPx * thumbScale

        canvas.drawCircle(thumbCenterX, thumbCenterY, currentThumbRadius, thumbPaint)
    }
}
