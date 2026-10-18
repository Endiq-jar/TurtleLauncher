package com.endiq.turtlelauncher.ui.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import com.endiq.turtlelauncher.utils.anim.AnimatedIcon

/**
 * Drop-in ImageView for launcher icons: same attributes/layout contract, but the
 * icon animates on hover / press / focus / click (see [AnimatedIcon], and
 * [com.endiq.turtlelauncher.utils.anim.AnimatedLogo] for the WebP logos this
 * also routes to). Re-binding follows [setImageResource], which is how most
 * adapters swap icons.
 */
class AnimatedIconView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

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
