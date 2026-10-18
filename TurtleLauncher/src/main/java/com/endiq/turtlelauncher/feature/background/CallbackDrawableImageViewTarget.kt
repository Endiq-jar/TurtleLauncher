package com.endiq.turtlelauncher.feature.background

import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.request.target.DrawableImageViewTarget

/**
 * Load callback for the DrawableImageViewTarget class.
 */
class CallbackDrawableImageViewTarget(
    private val imageView: ImageView,
    private val callback: Callback?
) : DrawableImageViewTarget(imageView) {
    override fun setResource(resource: Drawable?) {
        // ImageViewTarget callbacks already run on the main thread. Posting again can
        // apply an old background after a newer request has cleared/replaced this one.
        super.setResource(resource)
        callback?.callback(resource != null)
    }

    override fun onLoadFailed(errorDrawable: Drawable?) {
        super.onLoadFailed(errorDrawable)
        callback?.callback(false)
    }

    fun interface Callback {
        fun callback(loaded: Boolean)
    }
}