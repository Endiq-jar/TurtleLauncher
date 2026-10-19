package com.endiq.turtlelauncher.ui.layout

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.RelativeLayout
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.ui.miuix.MiuixPressFeedback
import com.endiq.turtlelauncher.ui.miuix.MiuixSquircle
import com.endiq.turtlelauncher.ui.miuix.MiuixTheme

/**
 * Miuix `Card` / `BasicComponent` RelativeLayout container (`top.yukonga.miuix.kmp.basic.Card`
 * and `top.yukonga.miuix.kmp.basic.BasicComponent`).
 * Applies a G2-continuous Miuix Squircle outline, Folme spring `SinkFeedback` (`0.96f`),
 * and `MiuixIndication` press/hover/focus overlay.
 */
class AnimRelativeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    var isRippleEnabled: Boolean = true
        set(value) {
            field = value
            @Suppress("UNNECESSARY_SAFE_CALL")
            pressFeedback?.enableIndication = value
        }

    private val pressFeedback = MiuixPressFeedback(
        view = this,
        sinkScale = 0.96f,
        damping = 0.85f,
        response = 0.28f,
        cornerRadiusDp = MiuixTheme.CornerRadiiDp.CARD,
        enableIndication = true,
        enableSink = true
    )

    init {
        val styleable = context.obtainStyledAttributes(attrs, R.styleable.AnimRelativeLayout)
        isRippleEnabled = styleable.getBoolean(R.styleable.AnimRelativeLayout_ripple_for_relative, true)
        styleable.recycle()

        stateListAnimator = null
        if (background != null) {
            val radiusPx = MiuixTheme.dpToPx(context, MiuixTheme.CornerRadiiDp.CARD)
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

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        pressFeedback.drawIndication(canvas)
    }
}
