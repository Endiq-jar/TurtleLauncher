package com.endiq.turtlelauncher.ui.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.endiq.turtlelauncher.utils.anim.AnimatedLogo

/**
 * XML logo with the same static source and layout contract as an ImageView.
 *
 * Animated logos only play while hovered, pressed/focused or clicked (see
 * [AnimatedLogo]); a standalone logo has no clickable parent to borrow those
 * interactions from, so it opts into receiving them itself.
 */
class LogoImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {
    init {
        val values = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.src))
        val source = values.getResourceId(0, 0)
        values.recycle()
        if (source != 0) {
            if (AnimatedLogo.isAnimatedLogo(source)) {
                isClickable = true
                isFocusable = true
            }
            AnimatedLogo.bind(this, source)
        }
    }

    override fun performClick(): Boolean {
        // Keyboard/gamepad/accessibility clicks may not produce touch events on
        // this view - make sure they still earn one play-through.
        AnimatedLogo.playOnce(this)
        return super.performClick()
    }
}
