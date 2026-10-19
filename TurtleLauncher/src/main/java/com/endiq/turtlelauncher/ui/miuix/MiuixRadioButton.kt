package com.endiq.turtlelauncher.ui.miuix

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatRadioButton
import kotlin.math.abs

/**
 * Miuix-styled circular RadioButton (`top.yukonga.miuix.kmp.basic.RadioButton` / `Checkbox`).
 * Subclasses [AppCompatRadioButton] (which extends [android.widget.RadioButton]) so all
 * ViewBinding and adapter callers (`VersionAdapter`, `ProfilePathAdapter`) work seamlessly.
 */
class MiuixRadioButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.radioButtonStyle
) : AppCompatRadioButton(context, attrs, defStyleAttr) {

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
        buttonDrawable = selectionDrawable
        background = null
        selectionDrawable.isControlEnabled = isEnabled
        selectionDrawable.progress = if (isChecked) 1f else 0f
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (selectionDrawable != null) {
            selectionDrawable.isControlEnabled = enabled
        }
    }

    override fun setChecked(checked: Boolean) {
        val changed = isChecked != checked
        super.setChecked(checked)
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
