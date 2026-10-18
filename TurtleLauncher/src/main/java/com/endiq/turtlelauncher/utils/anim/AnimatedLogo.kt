package com.endiq.turtlelauncher.utils.anim

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.widget.ImageView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.task.TaskExecutors

/** Finite animated WebP accents. Original drawables remain the API 26/27/reduced-motion fallback. */
object AnimatedLogo {
    @JvmStatic
    fun bind(view: ImageView, resource: Int) {
        clear(view)
        view.setImageDrawable(androidx.core.content.ContextCompat.getDrawable(view.context, resource))
        if (Build.VERSION.SDK_INT < 28 || !TurtleTransitions.isEnabled()) return
        val raw = when (resource) {
            R.drawable.ic_minecraft -> R.raw.ic_minecraft
            R.drawable.ic_turtle_full -> R.raw.ic_turtle_full_animated
            R.drawable.ic_command_block -> R.raw.ic_command_block
            R.drawable.app_name_title -> R.raw.app_name_title
            R.drawable.ic_old_cobblestone -> R.raw.ic_old_cobblestone
            R.drawable.ic_old_grass_block -> R.raw.ic_old_grass_block
            else -> return
        }
        val request = Request(view)
        view.setTag(R.id.animated_logo_request, request)
        // Decoding is never on the scroll/UI thread. Small frames cap decoded memory.
        TaskExecutors.getDefault().execute {
            if (TaskExecutors.isGameSessionActive) return@execute
            val drawable = runCatching {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(view.resources, raw))
            }.getOrNull() ?: return@execute
            view.post {
                if (view.getTag(R.id.animated_logo_request) !== request) return@post
                request.install(drawable)
            }
        }
    }

    @JvmStatic
    fun clear(view: ImageView) {
        (view.getTag(R.id.animated_logo_request) as? Request)?.close()
        view.setTag(R.id.animated_logo_request, null)
    }

    private class Request(private val view: ImageView) : View.OnAttachStateChangeListener,
        DefaultLifecycleObserver {
        private var owner: LifecycleOwner? = null
        private var drawable: Drawable? = null
        private var played = false

        init {
            view.addOnAttachStateChangeListener(this)
            if (view.isAttachedToWindow) onViewAttachedToWindow(view)
        }

        fun install(value: Drawable) {
            drawable = value
            view.setImageDrawable(value)
            playIfVisible()
        }

        private fun playIfVisible() {
            if (Build.VERSION.SDK_INT < 28 || played || !view.isShown ||
                !view.isAttachedToWindow || !TurtleTransitions.isEnabled() ||
                owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return
            (drawable as? AnimatedImageDrawable)?.let {
                it.repeatCount = 0 // one sweep, never an infinite heat-producing loop
                played = true
                it.start()
            }
        }

        private fun stop() {
            if (Build.VERSION.SDK_INT >= 28) (drawable as? AnimatedImageDrawable)?.stop()
        }

        override fun onViewAttachedToWindow(v: View) {
            owner = view.findViewTreeLifecycleOwner()
            owner?.lifecycle?.addObserver(this)
            playIfVisible()
        }

        override fun onViewDetachedFromWindow(v: View) {
            stop()
            owner?.lifecycle?.removeObserver(this)
            owner = null
        }

        override fun onResume(owner: LifecycleOwner) = playIfVisible()
        override fun onPause(owner: LifecycleOwner) = stop()
        override fun onDestroy(owner: LifecycleOwner) = close()

        fun close() {
            stop()
            owner?.lifecycle?.removeObserver(this)
            owner = null
            view.removeOnAttachStateChangeListener(this)
            drawable = null
        }
    }
}
