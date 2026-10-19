package com.endiq.turtlelauncher.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatTextView
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.ui.miuix.MiuixPressFeedback
import com.endiq.turtlelauncher.ui.miuix.MiuixSquircle

/**
 * Interactive Miuix TextView with G2-continuous squircle clipping, `MiuixIndication`
 * press/hover/focus overlay, and Folme spring `SinkFeedback`.
 */
class AnimTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    var isRippleEnabled: Boolean = false
        set(value) {
            field = value
            @Suppress("UNNECESSARY_SAFE_CALL")
            pressFeedback?.enableIndication = value
        }

    private val pressFeedback = MiuixPressFeedback(
        view = this,
        sinkScale = 0.95f,
        damping = 0.85f,
        response = 0.26f,
        cornerRadiusDp = 12f,
        enableIndication = false,
        enableSink = true
    )

    init {
        stateListAnimator = null
        val styleable = context.obtainStyledAttributes(attrs, R.styleable.AnimTextView)
        isRippleEnabled = styleable.getBoolean(R.styleable.AnimTextView_ripple_for_text, false)
        styleable.recycle()

        if (background != null) {
            val radiusPx = 12f * resources.displayMetrics.density
            MiuixSquircle.applyOutline(this, radiusPx, clipToOutline = true)
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pressFeedback.drawIndication(canvas)
    }
}
