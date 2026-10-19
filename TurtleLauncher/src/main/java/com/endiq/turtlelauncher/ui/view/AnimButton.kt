package com.endiq.turtlelauncher.ui.view

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatButton
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.ui.miuix.MiuixPressFeedback
import com.endiq.turtlelauncher.ui.miuix.MiuixSquircle
import com.endiq.turtlelauncher.ui.miuix.MiuixSquircleDrawable
import com.endiq.turtlelauncher.ui.miuix.MiuixTheme
import kotlin.math.roundToInt

/**
 * Miuix-styled Button (`top.yukonga.miuix.kmp.basic.Button` / `TextButton`):
 * - 16dp G2-continuous Miuix Squircle surface (`MiuixSquircleDrawable`)
 * - Secondary (`secondaryVariant` `#434343` / `#F0F0F0`), Primary (`primary` `#277AF7` / `#3482FF`),
 *   or Danger (`error` `#F12522` / `#E94634`) color schemes
 * - Folme spring `SinkFeedback` (`0.94f`, `damping = 0.85f, response = 0.28f`)
 * - `MiuixIndication` state overlay (`HOVER = 0.06f`, `FOCUS = 0.08f`, `PRESS = 0.10f`)
 *
 * `open` because the Java control-editor buttons still subclass it
 * (`net.endiq.launcher.customcontrols.handleview.AddSubButton` / `CloneButton` / `DeleteButton`).
 * Kotlin classes are final by default, so without `open` javac fails with
 * "cannot inherit from final AnimButton". Only the class is opened - members stay final on
 * purpose: [applyMiuixStyle] runs from the constructor, and a subclass override called that
 * early would see a half-initialised button.
 */
open class AnimButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.buttonStyle
) : AppCompatButton(context, attrs, defStyleAttr) {

    enum class MiuixButtonStyle {
        SECONDARY,
        PRIMARY,
        DANGER,
        TRANSPARENT
    }

    private var squircleBackground: MiuixSquircleDrawable? = null
    private val pressFeedback = MiuixPressFeedback(
        view = this,
        sinkScale = 0.94f,
        damping = 0.85f,
        response = 0.28f,
        cornerRadiusDp = MiuixTheme.CornerRadiiDp.BUTTON,
        enableIndication = true,
        enableSink = true
    )

    init {
        isAllCaps = false
        stateListAnimator = null
        elevation = 0f

        var explicitBgRes = 0
        var hasBgAttr = false
        var hasTextColorAttr = false
        if (attrs != null) {
            val a = context.obtainStyledAttributes(
                attrs,
                intArrayOf(android.R.attr.background, android.R.attr.textColor)
            )
            hasBgAttr = a.hasValue(0)
            explicitBgRes = a.getResourceId(0, 0)
            hasTextColorAttr = a.hasValue(1)
            a.recycle()
        }

        val initialStyle = when {
            hasBgAttr && explicitBgRes == 0 -> MiuixButtonStyle.TRANSPARENT
            explicitBgRes == android.R.color.transparent -> MiuixButtonStyle.TRANSPARENT
            explicitBgRes == R.drawable.bg_launch_button ||
                explicitBgRes == R.drawable.background_pill_filled ||
                explicitBgRes == R.drawable.background_pill_install -> MiuixButtonStyle.PRIMARY
            explicitBgRes != 0 -> MiuixButtonStyle.SECONDARY
            isPrimaryActionId(id) -> MiuixButtonStyle.PRIMARY
            else -> MiuixButtonStyle.SECONDARY
        }
        applyMiuixStyle(initialStyle, preserveExplicitTextColor = hasTextColorAttr)

        // Ensure comfortable Miuix button padding when none was specified
        val hPad = MiuixTheme.dpToPx(context, 14f).roundToInt()
        val vPad = MiuixTheme.dpToPx(context, 8f).roundToInt()
        if (paddingLeft == 0 && paddingRight == 0) {
            setPadding(hPad, paddingTop.coerceAtLeast(vPad), hPad, paddingBottom.coerceAtLeast(vPad))
        }
    }

    private fun isPrimaryActionId(viewId: Int): Boolean {
        return viewId == R.id.confirm_button ||
            viewId == R.id.play_button ||
            viewId == R.id.update_button ||
            viewId == R.id.install_button ||
            viewId == R.id.notice_got_button ||
            viewId == R.id.crash_repair_button
    }

    @JvmOverloads
    fun applyMiuixStyle(style: MiuixButtonStyle, preserveExplicitTextColor: Boolean = false) {
        val colors = MiuixTheme.colors(context)
        val radiusPx = MiuixTheme.dpToPx(context, MiuixTheme.CornerRadiiDp.BUTTON)

        when (style) {
            MiuixButtonStyle.TRANSPARENT -> {
                squircleBackground = null
                super.setBackgroundColor(Color.TRANSPARENT)
                pressFeedback.enableIndication = true
            }
            MiuixButtonStyle.PRIMARY -> {
                val states = arrayOf(
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf()
                )
                val fillColors = ColorStateList(
                    states,
                    intArrayOf(colors.disabledPrimaryButton, colors.primary)
                )
                val textColors = ColorStateList(
                    states,
                    intArrayOf(colors.disabledOnPrimaryButton, colors.onPrimary)
                )
                val drawable = MiuixSquircleDrawable(
                    fillColor = colors.primary,
                    cornerRadiusPx = radiusPx,
                    indicationBaseColor = colors.onPrimary,
                    enableIndication = true
                ).apply {
                    setFillColorStateList(fillColors)
                }
                squircleBackground = drawable
                super.setBackground(drawable)
                pressFeedback.enableIndication = false
                if (!preserveExplicitTextColor) {
                    setTextColor(textColors)
                }
                MiuixSquircle.applyOutline(this, radiusPx, clipToOutline = true)
            }
            MiuixButtonStyle.DANGER -> {
                val states = arrayOf(
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf()
                )
                val fillColors = ColorStateList(
                    states,
                    intArrayOf(colors.disabledSecondaryVariant, colors.error)
                )
                val textColors = ColorStateList(
                    states,
                    intArrayOf(colors.disabledOnSecondaryVariant, colors.onPrimary)
                )
                val drawable = MiuixSquircleDrawable(
                    fillColor = colors.error,
                    cornerRadiusPx = radiusPx,
                    indicationBaseColor = colors.onPrimary,
                    enableIndication = true
                ).apply {
                    setFillColorStateList(fillColors)
                }
                squircleBackground = drawable
                super.setBackground(drawable)
                pressFeedback.enableIndication = false
                setTextColor(textColors)
                MiuixSquircle.applyOutline(this, radiusPx, clipToOutline = true)
            }
            MiuixButtonStyle.SECONDARY -> {
                val states = arrayOf(
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf()
                )
                val fillColors = ColorStateList(
                    states,
                    intArrayOf(colors.disabledSecondaryVariant, colors.secondaryVariant)
                )
                val textColors = ColorStateList(
                    states,
                    intArrayOf(colors.disabledOnSecondaryVariant, colors.onSecondaryVariant)
                )
                val drawable = MiuixSquircleDrawable(
                    fillColor = colors.secondaryVariant,
                    cornerRadiusPx = radiusPx,
                    indicationBaseColor = colors.onSurface,
                    enableIndication = true
                ).apply {
                    setFillColorStateList(fillColors)
                }
                squircleBackground = drawable
                super.setBackground(drawable)
                pressFeedback.enableIndication = false
                if (!preserveExplicitTextColor) {
                    setTextColor(textColors)
                }
                MiuixSquircle.applyOutline(this, radiusPx, clipToOutline = true)
            }
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
        if (event?.actionMasked == MotionEvent.ACTION_DOWN && isEnabled) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pressFeedback.drawIndication(canvas)
    }
}
