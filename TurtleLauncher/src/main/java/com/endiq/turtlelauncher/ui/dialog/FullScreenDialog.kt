package com.endiq.turtlelauncher.ui.dialog

import android.app.Activity
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
import com.endiq.turtlelauncher.ui.miuix.MiuixSpring
import com.endiq.turtlelauncher.ui.miuix.MiuixSquircle
import com.endiq.turtlelauncher.ui.miuix.MiuixSquircleDrawable
import com.endiq.turtlelauncher.ui.miuix.MiuixTheme
import com.endiq.turtlelauncher.utils.anim.TurtleTransitions
import kotlin.math.roundToInt

/**
 * Base dialog class upgraded with Miuix `WindowDialog` / `DialogContentLayout` styling
 * (`top.yukonga.miuix.kmp.layout.DialogContentLayout`):
 * - 28dp G2-continuous Miuix Squircle container (`background` `#242424` / `#FFFFFF`)
 * - Miuix `windowDimming` (`0.60f` dark / `0.30f` light)
 * - Folme spring entrance animation (`damping = 0.9f, response = 0.3f`, scale `0.85 -> 1.0`)
 */
abstract class FullScreenDialog(context: Context) : Dialog(context, R.style.FullScreenDialog) {

    /** Guards against a second dismiss arriving while the exit spring is running. */
    private var dismissing = false

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
        // Dialog windows are not part of the Activity's decor view, so
        // BaseActivity.installMotion never reaches their RecyclerViews. Wire the
        // dialog's own list motion here (ModDependenciesDialog, SelectItemDialog, ...).
        TurtleTransitions.installMotion(rootView)
    }

    override fun onStart() {
        super.onStart()
        // The window exists and the content is attached here, so this is the first point
        // where the dialog's own lists can be tagged for their staggered first pass.
        window?.decorView?.let { TurtleTransitions.installMotion(it) }
        if (TurtleTransitions.isEnabled()) {
            val content = window?.decorView?.findViewById<View>(android.R.id.content) ?: return
            content.animate().cancel()
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

    /**
     * Spring the surface out before the window disappears. Previously [dismiss] removed
     * the dialog instantly, so every one of the 15 dialogs built on this base class had
     * an entrance animation but no exit - the "removed transition" this restores.
     *
     * The reverse animation is intentionally shorter and slightly more damped than the
     * entrance: a dismissal should feel decisive, not bounce back at the user.
     */
    override fun dismiss() {
        if (dismissing) return
        if (!TurtleTransitions.isEnabled()) {
            super.dismiss()
            return
        }
        val content = window?.decorView?.findViewById<View>(android.R.id.content)
        if (content == null || !content.isAttachedToWindow) {
            super.dismiss()
            return
        }
        dismissing = true
        val durationMs = 220L
        content.animate().cancel()
        content.animate()
            .alpha(0f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(durationMs)
            .setInterpolator(MiuixSpring.folmeInterpolator(0.95f, 0.26f, durationMs))
            .withEndAction {
                dismissing = false
                finishDismiss()
            }
            .start()
    }

    /** Called from the animation callback: [super.dismiss] outside the lambda body. */
    private fun finishDismiss() {
        super.dismiss()
    }

    /**
     * Sizes the dialog to its content, capping it at the screen height minus [dpValue] dp and
     * handing the overflow to [scrollView]. Every subclass calls this once its content view is
     * inflated (`ControlInfoDialog`, `EditControlInfoDialog`, `EditTextDialog`, `SkinCapeDialog`,
     * `TipDialog`, `WardrobeDialog`), so it is part of this base class' contract - the Miuix
     * surface restyle above must not drop it.
     *
     * Adapted from FCL [FCLAlertDialog.java](https://github.com/FCL-Team/FoldCraftLauncher/blob/e7d82af/FCLLibrary/src/main/java/com/tungsten/fcllibrary/component/dialog/FCLAlertDialog.java#L62-L77)
     */
    protected fun checkHeight(root: View, content: View, scrollView: View, dpValue: Int = 50) {
        root.post {
            val density = context.resources.displayMetrics.density
            val marginPx = (dpValue * density + 0.5f).toInt()
            // Use the activity's actual window bounds, not the physical display. Display
            // metrics report the whole panel in split-screen/freeform mode and let a
            // WRAP_CONTENT dialog expand across the entire pane.
            val activityDecor = (context as? Activity)?.window?.decorView
            val availableWidth = activityDecor?.width?.takeIf { it > 0 }
                ?: context.resources.displayMetrics.widthPixels
            val availableHeight = activityDecor?.height?.takeIf { it > 0 }
                ?: context.resources.displayMetrics.heightPixels
            val maxWidth = (560f * density + 0.5f).toInt()
            val horizontalMargin = (16f * density + 0.5f).toInt()
            val dialogWidth = minOf(maxWidth, (availableWidth - 2 * horizontalMargin).coerceAtLeast(1))
            val maxHeight = (availableHeight - marginPx).coerceAtLeast(1)

            // Bound the scroll region itself. Limiting only the window leaves its
            // WRAP_CONTENT children laid out at their full height, which can clip the
            // title and buttons (or leave an apparently empty dialog) on short windows.
            val fixedHeight = (root.measuredHeight - scrollView.measuredHeight).coerceAtLeast(0)
            val maxScrollHeight = (maxHeight - fixedHeight).coerceAtLeast(1)
            val scrollParams = scrollView.layoutParams
            scrollParams.height = minOf(content.measuredHeight, maxScrollHeight)
            scrollView.layoutParams = scrollParams

            window?.setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }
}
