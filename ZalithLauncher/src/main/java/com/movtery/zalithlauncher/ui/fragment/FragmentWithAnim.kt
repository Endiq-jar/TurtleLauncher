package com.movtery.zalithlauncher.ui.fragment

import com.movtery.anim.AnimPlayer
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.task.TaskExecutors
import com.movtery.zalithlauncher.utils.anim.SlideAnimation

abstract class FragmentWithAnim : BaseFragment, SlideAnimation {
    private var animPlayer: AnimPlayer = AnimPlayer()

    constructor() : super()

    constructor(contentLayoutId: Int) : super(contentLayoutId)

    override fun onStart() {
        super.onStart()
        slideIn()
    }

    fun slideIn() {
        playAnimation { slideIn(it) }
    }

    fun slideOut() {
        playAnimation { slideOut(it) }
    }

    private fun playAnimation(animationAction: (AnimPlayer) -> Unit) {
        // TurtleLauncher: Background Services (item 20) - a fragment transition can still
        // fire here while the launcher Activity is stopped behind an active Minecraft
        // session (e.g. an EventBus-driven page swap queued just before the game started).
        // Treat an active game session the same as the user's own "disable animations"
        // toggle: skip straight past AnimatorSet entirely rather than spending CPU/GC on
        // ticking a transition nobody is looking at. No new setting needed - reuses the
        // same on/off semantics AllSettings.animation already has here.
        if (AllSettings.animation.getValue() && !TaskExecutors.isGameSessionActive) {
            animPlayer.clearEntries()
            animPlayer.apply {
                animationAction(this)
                start()
            }
        }
    }
}
