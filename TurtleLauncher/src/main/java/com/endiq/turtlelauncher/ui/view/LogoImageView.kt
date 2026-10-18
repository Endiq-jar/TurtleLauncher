package com.endiq.turtlelauncher.ui.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.endiq.turtlelauncher.utils.anim.AnimatedLogo

/** XML logo with the same static source and layout contract as an ImageView. */
class LogoImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {
    init {
        val values = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.src))
        val source = values.getResourceId(0, 0)
        values.recycle()
        if (source != 0) AnimatedLogo.bind(this, source)
    }
}
