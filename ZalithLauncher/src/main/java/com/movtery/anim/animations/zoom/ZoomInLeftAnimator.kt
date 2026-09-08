package com.movtery.anim.animations.zoom

import android.animation.Animator
import android.animation.ObjectAnimator
import android.view.View
import com.movtery.anim.animations.BaseAnimator

class ZoomInLeftAnimator : BaseAnimator() {
    override fun getAnimators(target: View): Array<Animator> {
        return arrayOf(
            ObjectAnimator.ofFloat(target, "alpha", 0f, 1f),
            ObjectAnimator.ofFloat(target, "scaleX", 0.6f, 1f),
            ObjectAnimator.ofFloat(target, "scaleY", 0.6f, 1f),
            ObjectAnimator.ofFloat(target, "translationX", -120f, 0f)
        )
    }
}
