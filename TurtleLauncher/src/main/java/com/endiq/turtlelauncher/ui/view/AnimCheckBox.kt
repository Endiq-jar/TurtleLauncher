package com.endiq.turtlelauncher.ui.view

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatCheckBox
import com.endiq.turtlelauncher.ui.miuix.MiuixPressFeedback
import com.endiq.turtlelauncher.ui.miuix.MiuixSelectionDrawable
import com.endiq.turtlelauncher.ui.miuix.MiuixSpring
import com.endiq.turtlelauncher.ui.miuix.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Exact Android View port of Miuix `Checkbox` (`top.yukonga.miuix.kmp.basic.Checkbox`):
 * - 22dp circular indicator (`secondary` unchecked -> `primary` checked)
 * - Animated path-trimmed white checkmark (`(7.59, 12.08) -> (10.3, 14.9) -> (15.48, 8.22)`)
 * - Spring `SinkFeedback` (`0.88f`) on press with haptic feedback
 */
class AnimCheckBox @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.checkboxStyle
) : AppCompatCheckBox(context, attrs, defStyleAttr) {

    private val selectionDrawable = MiuixSelectionDrawable(context, isRadio = false, sizeDp = 22f)
    private var progressAnimator: ValueAnimator? = null
    private val pressFeedback = MiuixPressFeedback(
        view = this,
        sinkScale = 0.88f,
        damping = 0.9f,
        response = 0.22f,
        enableIndication = false,
        enableSink = true
    )

    init {
        stateListAnimator = null
        background = null
        buttonDrawable = selectionDrawable
        selectionDrawable.isControlEnabled = isEnabled
        selectionDrawable.progress = if (isChecked) 1f else 0f
        if (!text.isNullOrEmpty() && compoundDrawablePadding == 0) {
            compoundDrawablePadding = MiuixTheme.dpToPx(context, 8f).roundToInt()
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        @Suppress("UNNECESSARY_SAFE_CALL")
        selectionDrawable?.isControlEnabled = enabled
    }

    override fun setChecked(checked: Boolean) {
        val changed = isChecked != checked
        super.setChecked(checked)
        @Suppress("SENSELESS_COMPARISON")
        if (selectionDrawable == null) return
        val target = if (checked) 1f else 0f
        if (changed && isAttachedToWindow && width > 0) {
            progressAnimator?.cancel()
            val start = selectionDrawable.progress
            if (abs(start - target) > 0.01f) {
                progressAnimator = MiuixSpring.animateFloat(
                    from = start,
                    to = target,
                    dampingRatio = 0.85f,
                    stiffness = 700f,
                    durationMs = 240L
                ) { value ->
                    selectionDrawable.progress = value
                }
            } else {
                selectionDrawable.progress = target
            }
        } else {
            progressAnimator?.cancel()
            selectionDrawable.progress = target
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            pivotX = width / 2f
            pivotY = height / 2f
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        pressFeedback.onTouchEvent(event)
        if (event?.actionMasked == MotionEvent.ACTION_UP && isEnabled) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        return super.onTouchEvent(event)
    }

    override fun onHoverEvent(event: MotionEvent?): Boolean {
        pressFeedback.onHoverEvent(event)
        return super.onHoverEvent(event)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        @Suppress("UNNECESSARY_SAFE_CALL")
        pressFeedback?.onDrawableStateChanged()
    }
}
