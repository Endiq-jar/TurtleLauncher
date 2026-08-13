package com.movtery.zalithlauncher.ui.view

import android.animation.AnimatorInflater
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.movtery.zalithlauncher.R
import net.kdt.pojavlaunch.Tools

open class AnimButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.buttonStyle
) : AppCompatButton(context, attrs, defStyleAttr) {
    init {
        isAllCaps = false
        setRipple()
        stateListAnimator = AnimatorInflater.loadStateListAnimator(context, R.xml.anim_scale)
        translationZ = Tools.dpToPx(4f)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        post {
            pivotX = width / 2f
            pivotY = height / 2f
        }
    }

    private fun setRipple() {
        // TurtleLauncher: this used to always use R.drawable.button_background here,
        // silently discarding whatever android:background an individual AnimButton
        // instance set in its own layout XML - e.g. play_button's background never
        // actually showed, no matter what drawable it named. By the time this runs,
        // the View constructor chain has already applied any XML android:background,
        // so use that if the instance set one, and only fall back to the shared
        // default when it didn't.
        val instanceBackground = background
        val contentDrawable = instanceBackground
            ?: ResourcesCompat.getDrawable(resources, R.drawable.button_background, context.theme)

        val rippleDrawable = RippleDrawable(
            ColorStateList.valueOf(ContextCompat.getColor(context, R.color.background_ripple_effect)),
            contentDrawable,
            null
        )

        background = rippleDrawable
    }
}