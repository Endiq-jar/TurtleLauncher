package com.endiq.turtlelauncher.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatImageButton
import com.endiq.turtlelauncher.ui.miuix.MiuixPressFeedback
import com.endiq.turtlelauncher.utils.anim.AnimatedIcon

/**
 * Miuix `IconButton` + animated vector icon button (`top.yukonga.miuix.kmp.basic.IconButton`).
 * Combines `AnimatedIcon` keyframe animation with Miuix capsule `MiuixIndication` and
 * Folme spring `SinkFeedback` (`0.90f`).
 */
class AnimatedIconButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.imageButtonStyle
) : AppCompatImageButton(context, attrs, defStyleAttr) {

    private var suppressRebind = false
    private val pressFeedback = MiuixPressFeedback(
        view = this,
        sinkScale = 0.90f,
        damping = 0.85f,
        response = 0.25f,
        cornerRadiusDp = 20f,
        enableIndication = true,
        enableSink = true
    )

    init {
        val values = context.obtainStyledAttributes(
            attrs,
            intArrayOf(android.R.attr.src, androidx.appcompat.R.attr.srcCompat)
        )
        val source = values.getResourceId(0, 0).takeIf { it != 0 } ?: values.getResourceId(1, 0)
        values.recycle()
        if (source != 0) {
            bindSource(source)
        }
    }

    override fun setImageResource(@DrawableRes resId: Int) {
        super.setImageResource(resId)
        if (!suppressRebind) bindSource(resId)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        if (!suppressRebind) AnimatedIcon.clear(this)
        super.setImageDrawable(drawable)
    }

    /**
     * WebP logos are routed to AnimatedLogo and vector icons get an AnimatedIcon style by
     * `bindAny`; anything else simply keeps the drawable `super.setImageResource` already applied.
     * `suppressRebind` covers the `setImageDrawable` calls AnimatedLogo.bind makes while it swaps
     * in its frames, so binding a logo cannot clear the binding again mid-flight.
     */
    private fun bindSource(@DrawableRes resId: Int) {
        suppressRebind = true
        try {
            if (resId == 0) AnimatedIcon.clear(this) else AnimatedIcon.bindAny(this, resId)
        } finally {
            suppressRebind = false
        }
    }

    override fun performClick(): Boolean {
        AnimatedIcon.playOnceAny(this)
        return super.performClick()
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
        pressFeedback.drawIndication(canvas)
        super.onDraw(canvas)
    }
}
