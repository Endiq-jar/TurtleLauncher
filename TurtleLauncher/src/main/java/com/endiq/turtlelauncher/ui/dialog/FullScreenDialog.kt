package com.endiq.turtlelauncher.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.miuix.MiuixSpring
import com.endiq.turtlelauncher.ui.miuix.MiuixSquircle
import com.endiq.turtlelauncher.ui.miuix.MiuixSquircleDrawable
import com.endiq.turtlelauncher.ui.miuix.MiuixTheme
import kotlin.math.roundToInt

/**
 * Base dialog class upgraded with Miuix `WindowDialog` / `DialogContentLayout` styling
 * (`top.yukonga.miuix.kmp.layout.DialogContentLayout`):
 * - 28dp G2-continuous Miuix Squircle container (`background` `#242424` / `#FFFFFF`)
 * - Miuix `windowDimming` (`0.60f` dark / `0.30f` light)
 * - Folme spring entrance animation (`damping = 0.9f, response = 0.3f`, scale `0.85 -> 1.0`)
 */
abstract class FullScreenDialog(context: Context) : Dialog(context, R.style.FullScreenDialog) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.apply {
            requestFeature(Window.FEATURE_NO_TITLE)
            setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(if (MiuixTheme.isDarkMode(context)) 0.60f else 0.30f)

            decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            setGravity(Gravity.CENTER)
        }
    }

    override fun setContentView(view: View) {
        applyMiuixDialogSurface(view)
        super.setContentView(view)
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams?) {
        applyMiuixDialogSurface(view)
        super.setContentView(view, params)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        window?.decorView?.findViewById<ViewGroup>(android.R.id.content)?.getChildAt(0)?.let {
            applyMiuixDialogSurface(it)
        }
    }

    private fun applyMiuixDialogSurface(rootView: View) {
        val colors = MiuixTheme.colors(context)
        val radiusPx = MiuixTheme.dpToPx(context, 28f)
        val strokePx = MiuixTheme.dpToPx(context, 0.75f)
        val drawable = MiuixSquircleDrawable(
            fillColor = colors.background,
            cornerRadiusPx = radiusPx,
            strokeColor = colors.outline,
            strokeWidthPx = strokePx,
            indicationBaseColor = colors.onSurface,
            enableIndication = false
        )
        rootView.background = drawable
        MiuixSquircle.applyOutline(rootView, radiusPx, clipToOutline = true)

        val minPad = MiuixTheme.dpToPx(context, 18f).roundToInt()
        if (rootView.paddingLeft < minPad && rootView.paddingRight < minPad) {
            rootView.setPadding(
                maxOf(rootView.paddingLeft, minPad),
                maxOf(rootView.paddingTop, minPad),
                maxOf(rootView.paddingRight, minPad),
                maxOf(rootView.paddingBottom, minPad)
            )
        }
    }

    override fun onStart() {
        super.onStart()
        if (AllSettings.animation.getValue()) {
            val content = window?.decorView?.findViewById<View>(android.R.id.content) ?: return
            content.alpha = 0f
            content.scaleX = 0.86f
            content.scaleY = 0.86f
            val durationMs = 280L
            content.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(durationMs)
                .setInterpolator(MiuixSpring.folmeInterpolator(0.9f, 0.3f, durationMs))
                .start()
        }
    }
}
