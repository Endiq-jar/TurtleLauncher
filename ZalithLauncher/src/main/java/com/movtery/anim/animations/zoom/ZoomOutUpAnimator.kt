package com.movtery.anim.animations.zoom

import android.animation.Animator
import android.animation.ObjectAnimator
import android.view.View
import com.movtery.anim.animations.BaseAnimator

class ZoomOutUpAnimator : BaseAnimator() {
    override fun getAnimators(target: View): Array<Animator> {
        return arrayOf(
            ObjectAnimator.ofFloat(target, "alpha", 1f, 0f),
            ObjectAnimator.ofFloat(target, "scaleX", 1f, 0.6f),
            ObjectAnimator.ofFloat(target, "scaleY", 1f, 0.6f),
            ObjectAnimator.ofFloat(target, "translationY", 0f, -120f)
        )
    }
}
