package com.endiq.turtlelauncher.utils

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat

/**
 * Tap (or long-press) a warning icon to show a small dark tooltip bubble with [message].
 * Auto-dismisses after a few seconds or on outside touch.
 */
object WarningTooltip {
    private const val SHOW_MS = 4000L

    @JvmStatic
    fun attach(anchor: View, message: String) {
        TooltipCompat.setTooltipText(anchor, message)
        anchor.setOnClickListener { show(anchor, message) }
    }

    @JvmStatic
    fun show(anchor: View, message: String) {
        val ctx = anchor.context
        val dp = ctx.resources.displayMetrics.density
        val tv = TextView(ctx).apply {
            text = message
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            val pad = (12 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            maxWidth = (260 * dp).toInt()
            background = GradientDrawable().apply {
                setColor(0xFF303034.toInt())
                cornerRadius = 8 * dp
            }
        }
        val popup = PopupWindow(tv, PopupWindow.LayoutParams.WRAP_CONTENT,
            PopupWindow.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            elevation = 0f // no shadow, per launcher perf rules
        }
        // Show just below the icon, right-aligned with it.
        tv.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val xOff = anchor.width - tv.measuredWidth
        popup.showAsDropDown(anchor, xOff, 0, Gravity.NO_GRAVITY)
        anchor.postDelayed({ if (popup.isShowing) runCatching { popup.dismiss() } }, SHOW_MS)
    }
}
