package com.endiq.turtlelauncher.ui.dialog

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.ui.miuix.MiuixTheme
import kotlin.math.roundToInt

/**
 * A content-first choice picker for settings fields in landscape/split-screen windows.
 * Unlike anchored PopupWindows, this always measures against the host Activity bounds
 * and owns the option rows, so options cannot disappear behind an empty popup surface.
 */
class ChoicePickerDialog(
    context: Context,
    private val title: CharSequence,
    private val options: List<String>,
    private val selectedIndex: Int,
    private val onSelected: (Int) -> Unit
) : Dialog(context, R.style.CustomAlertDialogTheme) {

    private val density = context.resources.displayMetrics.density
    private fun dp(value: Float): Int = (value * density + 0.5f).roundToInt()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            buildContent(),
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        setCancelable(true)
        setCanceledOnTouchOutside(true)

        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(if (MiuixTheme.isDarkMode(context)) 0.60f else 0.30f)
        }
    }

    override fun onStart() {
        super.onStart()
        val activityDecor = (context as? Activity)?.window?.decorView
        val availableWidth = activityDecor?.width?.takeIf { it > 0 }
            ?: context.resources.displayMetrics.widthPixels
        val maxWidth = minOf(dp(560f), (availableWidth - dp(32f)).coerceAtLeast(dp(1f)))
        window?.setLayout(maxWidth, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    private fun buildContent(): View {
        val colors = MiuixTheme.colors(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(18f), dp(18f), dp(14f))
            // Keep the surface rounded, but do not clip child glyphs to a platform
            // ViewOutline: some vendor renderers produce an empty outline for paths,
            // which leaves the panel visible while clipping every label away.
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(colors.background)
                cornerRadius = dp(24f).toFloat()
                setStroke(dp(1f), colors.outline)
            }
            clipChildren = false
            clipToPadding = false
        }

        root.addView(TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), dp(2f), dp(4f), dp(12f))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val choices = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        if (options.isEmpty()) {
            choices.addView(TextView(context).apply {
                text = context.getString(R.string.generic_default)
                setTextColor(Color.LTGRAY)
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14f), 0, dp(14f), 0)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52f)))
        } else {
            options.forEachIndexed { index, label ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    isClickable = true
                    isFocusable = true
                    setPadding(dp(14f), dp(4f), dp(14f), dp(4f))
                    setBackgroundResource(
                        if (index == selectedIndex) R.drawable.background_item_selected
                        else R.drawable.background_item_unselected
                    )
                    setOnClickListener {
                        onSelected(index)
                        dismiss()
                    }
                }
                row.addView(TextView(context).apply {
                    text = label
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    gravity = Gravity.CENTER_VERTICAL
                    maxLines = 2
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
                row.addView(TextView(context).apply {
                    text = if (index == selectedIndex) "✓" else ""
                    setTextColor(colors.primary)
                    textSize = 18f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(dp(28f), ViewGroup.LayoutParams.MATCH_PARENT))
                choices.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52f)).apply {
                    bottomMargin = dp(4f)
                })
            }
        }

        val activityDecor = (context as? Activity)?.window?.decorView
        val availableHeight = activityDecor?.height?.takeIf { it > 0 }
            ?: context.resources.displayMetrics.heightPixels
        val maxChoicesHeight = (availableHeight - dp(180f)).coerceAtLeast(dp(72f))
        val choicesHeight = minOf(maxChoicesHeight, (options.size.coerceAtLeast(1) * dp(56f)))
        val scroll = ScrollView(context).apply {
            isFillViewport = false
            clipToPadding = false
            addView(choices, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, choicesHeight))

        val cancel = TextView(context).apply {
            text = context.getString(android.R.string.cancel)
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setBackgroundResource(R.drawable.background_item_unselected)
            setOnClickListener { dismiss() }
        }
        root.addView(cancel, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48f)).apply {
            topMargin = dp(8f)
        })
        return root
    }
}
