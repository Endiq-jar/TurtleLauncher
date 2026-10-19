package com.endiq.turtlelauncher.ui.miuix

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.app.AppCompatViewInflater
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.AppCompatSeekBar
import com.endiq.turtlelauncher.ui.view.AnimButton
import com.endiq.turtlelauncher.ui.view.AnimCheckBox
import com.endiq.turtlelauncher.ui.view.AnimEditText
import com.endiq.turtlelauncher.ui.view.AnimatedIconButton
import com.endiq.turtlelauncher.ui.view.AnimatedIconView

/**
 * Custom [AppCompatViewInflater] registered via `viewInflaterClass` in `AppTheme`
 * so all standard Android widgets (`<Button>`, `<CheckBox>`, `<RadioButton>`,
 * `<EditText>`, `<SeekBar>`, `<Switch>`, `<ProgressBar>`) automatically inflate
 * as Miuix components throughout the application.
 */
class MiuixViewInflater : AppCompatViewInflater() {

    override fun createButton(context: Context, attrs: AttributeSet?): AppCompatButton {
        return AnimButton(context, attrs)
    }

    override fun createCheckBox(context: Context, attrs: AttributeSet?): AppCompatCheckBox {
        return AnimCheckBox(context, attrs)
    }

    override fun createRadioButton(context: Context, attrs: AttributeSet?): AppCompatRadioButton {
        return MiuixRadioButton(context, attrs)
    }

    override fun createEditText(context: Context, attrs: AttributeSet?): AppCompatEditText {
        return AnimEditText(context, attrs)
    }

    /**
     * Blanket coverage for any `<ImageView>` that is still written as the platform tag:
     * it inflates as [AnimatedIconView], so it earns the same hover/press/click motion as
     * the 199 explicit views already converted in `res/layout`. `<ImageButton>` becomes
     * [AnimatedIconButton] for the same reason. Both subclasses keep the exact
     * `ImageView`/`ImageButton` attribute + `ViewBinding` contract.
     */
    override fun createImageView(context: Context, attrs: AttributeSet?): AppCompatImageView {
        return AnimatedIconView(context, attrs)
    }

    override fun createImageButton(context: Context, attrs: AttributeSet?): AppCompatImageButton {
        return AnimatedIconButton(context, attrs)
    }

    override fun createSeekBar(context: Context, attrs: AttributeSet?): AppCompatSeekBar {
        return MiuixSlider(context, attrs)
    }

    override fun createView(context: Context, name: String?, attrs: AttributeSet?): View? {
        return when (name) {
            "Switch" -> MiuixSwitch(context, attrs)
            "ProgressBar" -> MiuixProgressBar(context, attrs)
            else -> super.createView(context, name, attrs)
        }
    }
}
