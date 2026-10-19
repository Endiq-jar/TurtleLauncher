package com.endiq.turtlelauncher.ui.fragment.settings.wrapper

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.setting.Settings
import com.endiq.turtlelauncher.setting.unit.StringSettingUnit
import com.endiq.turtlelauncher.ui.miuix.MiuixIconsDrawables
import com.endiq.turtlelauncher.ui.miuix.MiuixSpring
import com.endiq.turtlelauncher.ui.miuix.MiuixSquircle
import com.endiq.turtlelauncher.ui.miuix.MiuixSquircleDrawable
import com.endiq.turtlelauncher.ui.miuix.MiuixTheme
import kotlin.math.roundToInt

/**
 * Miuix `OverlayDropdownPreference` / `WindowDropdownPreference` wrapper:
 * - Displays the selected entry label with Miuix `onSurfaceVariantActions` color and
 *   trailing `MiuixIcons.Basic.ArrowUpDown` indicator (`10x16dp`)
 * - Opens a Miuix-styled selection dialog with 28dp G2-continuous squircle surface and
 *   Folme spring entrance animation.
 */
@SuppressLint("UseSwitchCompatOrMaterialCode")
class ListSettingsWrapper(
    private val context: Context,
    private val unit: StringSettingUnit,
    val parentView: View,
    val titleView: TextView,
    val valueView: TextView,
    val items: Array<String>,
    val itemValues: Array<String>
) : AbstractSettingsWrapper(parentView) {

    private var listener: OnItemChangedListener? = null

    init {
        require(items.size == itemValues.size) {
            "items (${items.size}) and itemValues (${itemValues.size}) must have the same length"
        }

        val colors = MiuixTheme.colors(context)
        valueView.setTextColor(colors.onSurfaceVariantActions)
        valueView.compoundDrawablePadding = MiuixTheme.dpToPx(context, 6f).roundToInt()
        valueView.setCompoundDrawablesRelativeWithIntrinsicBounds(
            null,
            null,
            MiuixIconsDrawables.arrowUpDown(context, colors.onSurfaceVariantActions),
            null
        )

        updateDisplayedValue()

        parentView.setOnClickListener {
            showMiuixPickerDialog()
        }
    }

    private fun currentIndex(): Int {
        val current = unit.getValue()
        val index = itemValues.indexOfFirst { it == current }
        return if (index >= 0) index else 0
    }

    private fun updateDisplayedValue() {
        val idx = currentIndex()
        if (items.isNotEmpty()) {
            valueView.text = items[idx]
        }
    }

    private fun showMiuixPickerDialog() {
        val currentIdx = currentIndex()
        val dialog = AlertDialog.Builder(context, R.style.CustomAlertDialogTheme)
            .setTitle(titleView.text)
            .setSingleChoiceItems(items, currentIdx) { dlg, which ->
                if (which in itemValues.indices) {
                    val chosen = itemValues[which]
                    Settings.Manager.put(unit.key, chosen).save()
                    updateDisplayedValue()
                    listener?.onChange(chosen)
                    checkShowRebootDialog(context)
                }
                dlg.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val colors = MiuixTheme.colors(context)
            val radiusPx = MiuixTheme.dpToPx(context, 28f)
            val strokePx = MiuixTheme.dpToPx(context, 0.75f)
            dialog.window?.let { win ->
                win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                win.setDimAmount(if (MiuixTheme.isDarkMode(context)) 0.60f else 0.30f)
                val content = win.decorView.findViewById<View>(android.R.id.content)
                if (content != null) {
                    content.background = MiuixSquircleDrawable(
                        fillColor = colors.background,
                        cornerRadiusPx = radiusPx,
                        strokeColor = colors.outline,
                        strokeWidthPx = strokePx,
                        indicationBaseColor = colors.onSurface,
                        enableIndication = false
                    )
                    MiuixSquircle.applyOutline(content, radiusPx, clipToOutline = true)
                    content.alpha = 0f
                    content.scaleX = 0.88f
                    content.scaleY = 0.88f
                    content.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(260L)
                        .setInterpolator(MiuixSpring.folmeInterpolator(0.9f, 0.3f, 260L))
                        .start()
                }
            }
        }
        dialog.show()
    }

    fun setOnItemChangedListener(listener: OnItemChangedListener): ListSettingsWrapper {
        this.listener = listener
        return this
    }

    fun interface OnItemChangedListener {
        fun onChange(value: String)
    }
}
