package com.endiq.turtlelauncher.ui.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton
import com.endiq.turtlelauncher.utils.anim.AnimatedIcon

/**
 * ImageButton twin of [AnimatedIconView]: the button's icon animates on hover /
 * press / focus / click while the button itself keeps its normal behaviour.
 * Being an ImageButton subclass keeps view-binding field types compatible.
 */
class AnimatedIconButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageButton(context, attrs, defStyleAttr) {

    init {
        val source = AnimatedIcon.readSource(context, attrs)
        if (source != 0) AnimatedIcon.bindAny(this, source)
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        if (resId != 0) AnimatedIcon.bindAny(this, resId) else AnimatedIcon.clear(this)
    }

    override fun performClick(): Boolean {
        AnimatedIcon.playOnceAny(this)
        return super.performClick()
    }
}
