package com.endiq.turtlelauncher.ui.miuix

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatSeekBar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Exact Android View port of Miuix `Slider` (`top.yukonga.miuix.kmp.basic.Slider`):
 * - 28dp capsule track (`CircleShape`) with `sliderBackground` fill
 * - Thick round-cap `primary` foreground fill
 * - Internal circular `onPrimary` thumb (`radius = 0.72 * (barHeight / 2) * thumbScale`)
 * - Spring-animated thumb scale (`1.0f -> 1.127f` when dragging/pressed/hovered)
 * - Smooth spring-interpolated visual fraction and edge haptic feedback
 *
 * Subclasses [AppCompatSeekBar] (which extends [android.widget.SeekBar]) so ViewBinding,
 * `AppCompatViewInflater`, and all `OnSeekBarChangeListener` callers work without any type changes.
 */
open class MiuixSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.seekBarStyle
) : AppCompatSeekBar(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val defaultTrackHeightPx = 28f * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val dragOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb((0.044f * 255f).roundToInt(), 0, 0, 0)
    }
    private val foregroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val clipPath = Path()
    private val trackRect = RectF()

    private var animatedFraction: Float = currentFraction()
    private var thumbScale: Float = 1f
    private var isDraggingState = false
    private var isHoveredState = false
    private var edgeFeedbackTriggered = false

    private var fractionAnimator: ValueAnimator? = null
    private var scaleAnimator: ValueAnimator? = null

    init {
        // Suppress stock SeekBar progress/thumb drawables; we render the Miuix capsule directly.
        super.setProgressDrawable(ColorDrawable(Color.TRANSPARENT))
        super.setThumb(ColorDrawable(Color.TRANSPARENT))
        splitTrack = false
        background = null
        animatedFraction = currentFraction()
    }

    private fun minVal(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) min else 0
    }

    private fun currentFraction(): Float {
        val mn = minVal()
        val mx = max
        val span = (mx - mn).coerceAtLeast(1)
        return ((progress - mn).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }

    override fun setProgress(progress: Int) {
        super.setProgress(progress)
        syncFraction(animate = isAttachedToWindow && width > 0 && !isDraggingState)
    }

    override fun setProgress(progress: Int, animate: Boolean) {
        super.setProgress(progress, animate)
        syncFraction(animate = animate && isAttachedToWindow && width > 0)
    }

    private fun syncFraction(animate: Boolean) {
        val target = currentFraction()
        if (!animate || abs(animatedFraction - target) < 0.002f) {
            fractionAnimator?.cancel()
            animatedFraction = target
            invalidate()
            return
        }
        fractionAnimator?.cancel()
        // Miuix Slider: spring(0.96f, 322f) when settling
        fractionAnimator = MiuixSpring.animateFloat(
            from = animatedFraction,
            to = target,
            dampingRatio = 0.96f,
            stiffness = 322f,
            durationMs = 200L
        ) { value ->
            animatedFraction = value.coerceIn(0f, 1f)
            invalidate()
        }
    }

    private fun updateThumbScale() {
        val active = isEnabled && (isDraggingState || isHoveredState)
        val targetScale = if (active) 1.127f else 1.0f
        val stiffness = if (active) 987f else 444f
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
            stiffness = stiffness,
            durationMs = 240L
        ) { value ->
            thumbScale = value
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val desiredH = max(
            defaultTrackHeightPx.roundToInt() + paddingTop + paddingBottom,
            suggestedMinimumHeight
        )
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSize = MeasureSpec.getSize(heightMeasureSpec)
        val finalH = when (hMode) {
            MeasureSpec.EXACTLY -> hSize
            MeasureSpec.AT_MOST -> min(desiredH, hSize)
            else -> max(measuredHeight, desiredH)
        }
        setMeasuredDimension(measuredWidth, finalH)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDraggingState = true
                val frac = currentFraction()
                edgeFeedbackTriggered = (frac <= 0.001f || frac >= 0.999f)
                parent?.requestDisallowInterceptTouchEvent(true)
                updateThumbScale()
            }
            MotionEvent.ACTION_MOVE -> {
                isDraggingState = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingState = false
                updateThumbScale()
            }
        }
        val handled = super.onTouchEvent(event)
        if (isDraggingState) {
            fractionAnimator?.cancel()
            animatedFraction = currentFraction()
            val atEdge = animatedFraction <= 0.001f || animatedFraction >= 0.999f
            if (atEdge && !edgeFeedbackTriggered) {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                edgeFeedbackTriggered = true
            } else if (!atEdge) {
                edgeFeedbackTriggered = false
            }
            invalidate()
        }
        return handled
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

        val availH = (h - paddingTop - paddingBottom).coerceAtLeast(12f * density)
        val barHeight = min(availH, defaultTrackHeightPx)
        val top = paddingTop + (availH - barHeight) / 2f
        val bottom = top + barHeight
        val left = 0f
        val right = w
        val barWidth = (right - left).coerceAtLeast(barHeight)
        val thumbRadius = barHeight / 2f

        trackRect.set(left, top, right, bottom)
        clipPath.reset()
        clipPath.addRoundRect(trackRect, thumbRadius, thumbRadius, Path.Direction.CW)

        val saveCount = canvas.save()
        canvas.clipPath(clipPath)

        // 1. Background capsule fill (`sliderBackground` / `disabledSecondary`)
        trackPaint.color = if (isEnabled) colors.sliderBackground else colors.disabledSecondary
        canvas.drawRect(trackRect, trackPaint)
        if (isDraggingState && isEnabled) {
            canvas.drawRect(trackRect, dragOverlayPaint)
        }

        // 2. Foreground active bar (`primary` / `disabledPrimarySlider`)
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val fraction = animatedFraction.coerceIn(0f, 1f)
        val effectiveFraction = if (isRtl) 1f - fraction else fraction
        val availableWidth = (barWidth - 2f * thumbRadius).coerceAtLeast(0f)
        val centerX = left + thumbRadius + effectiveFraction * availableWidth
        val centerY = top + barHeight / 2f
        val startX = if (isRtl) right else left

        foregroundPaint.color = if (isEnabled) colors.primary else colors.disabledPrimarySlider
        foregroundPaint.strokeWidth = barHeight
        canvas.drawLine(startX, centerY, centerX, centerY, foregroundPaint)

        // 3. Thumb (`onPrimary` / `disabledOnPrimary`, radius = thumbRadius * 0.72f * thumbScale)
        thumbPaint.color = if (isEnabled) colors.onPrimary else colors.disabledOnPrimary
        val currentThumbRadius = thumbRadius * 0.72f * thumbScale
        canvas.drawCircle(centerX, centerY, currentThumbRadius, thumbPaint)

        canvas.restoreToCount(saveCount)
    }
}
