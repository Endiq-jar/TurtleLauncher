package com.endiq.turtlelauncher.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatEditText
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.ui.miuix.MiuixSquircle
import com.endiq.turtlelauncher.ui.miuix.MiuixSquircleDrawable
import com.endiq.turtlelauncher.ui.miuix.MiuixTheme
import kotlin.math.roundToInt

/**
 * Exact Android View port of Miuix `TextField` (`top.yukonga.miuix.kmp.basic.TextField`):
 * - 16dp G2-continuous Miuix Squircle container (`secondaryContainer` `#434343` / `#F0F0F0`)
 * - Animated `0dp -> 2dp` `primary` (pastel green `#8FD9A8`) squircle border on focus (`150ms`)
 * - `onSecondaryContainer` (`#7C7C7C` / `#A9A9A9`) hint color and `onBackground` text color
 */
class AnimEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    private var squircleDrawable: MiuixSquircleDrawable? = null
    private var borderAnimator: ValueAnimator? = null
    private val maxStrokeWidthPx = MiuixTheme.dpToPx(context, 2f)

    init {
        stateListAnimator = null
        val colors = MiuixTheme.colors(context)

        var explicitBgRes = 0
        var hasBgAttr = false
        var hasTextColor = false
        var hasHintColor = false
        if (attrs != null) {
            val a = context.obtainStyledAttributes(
                attrs,
                intArrayOf(
                    android.R.attr.background,
                    android.R.attr.textColor,
                    android.R.attr.textColorHint
                )
            )
            hasBgAttr = a.hasValue(0)
            explicitBgRes = a.getResourceId(0, 0)
            hasTextColor = a.hasValue(1)
            hasHintColor = a.hasValue(2)
            a.recycle()
        }

        if (!hasTextColor) {
            setTextColor(colors.onBackground)
        }
        if (!hasHintColor) {
            setHintTextColor(colors.onSecondaryContainer)
        }

        if (!hasBgAttr || explicitBgRes == R.drawable.background_edit_box) {
            val radiusPx = MiuixTheme.dpToPx(context, MiuixTheme.CornerRadiiDp.TEXT_FIELD)
            val drawable = MiuixSquircleDrawable(
                fillColor = colors.secondaryContainer,
                cornerRadiusPx = radiusPx,
                strokeColor = colors.primary,
                strokeWidthPx = 0f,
                indicationBaseColor = colors.onSurface,
                enableIndication = false
            )
            squircleDrawable = drawable
            background = drawable
            MiuixSquircle.applyOutline(this, radiusPx, clipToOutline = true)

            val hPad = MiuixTheme.dpToPx(context, 14f).roundToInt()
            val vPad = MiuixTheme.dpToPx(context, 10f).roundToInt()
            if (paddingLeft == 0 && paddingRight == 0) {
                setPadding(hPad, paddingTop.coerceAtLeast(vPad), hPad, paddingBottom.coerceAtLeast(vPad))
            }
        }
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        val drawable = squircleDrawable ?: return
        borderAnimator?.cancel()
        val startWidth = drawable.strokeWidth
        val targetWidth = if (focused) maxStrokeWidthPx else 0f
        if (startWidth == targetWidth) return
        borderAnimator = ValueAnimator.ofFloat(startWidth, targetWidth).apply {
            duration = 150L
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                drawable.strokeWidth = anim.animatedValue as Float
            }
            start()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed) {
            pivotX = width / 2f
            pivotY = height / 2f
        }
    }
}
