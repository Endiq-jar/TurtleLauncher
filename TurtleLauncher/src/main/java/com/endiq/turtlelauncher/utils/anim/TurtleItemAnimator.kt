package com.endiq.turtlelauncher.utils.anim

import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator

/**
 * RecyclerView item animator used by every list in the launcher.
 *
 * The stock [androidx.recyclerview.widget.DefaultItemAnimator] fades everything; this
 * gives each list operation its own, recognisably different motion:
 *
 *  * add - drops in from above with a small overshoot and squash/stretch,
 *  * remove - tips sideways and fades, alternating direction per position,
 *  * move - glides to its new slot with decelerate easing,
 *  * change - cross-fades the old and new content with a tiny scale pop.
 *
 * All work is view-property animation (alpha/translation/scale/rotation), so it is
 * GPU-composited. It obeys [TurtleTransitions.isEnabled] because
 * [TurtleTransitions.installMotion] simply removes the animator when motion is off.
 */
class TurtleItemAnimator : SimpleItemAnimator() {

    private data class MoveInfo(
        val holder: RecyclerView.ViewHolder,
        val fromX: Int,
        val fromY: Int,
        val toX: Int,
        val toY: Int
    )

    private data class ChangeInfo(
        val oldHolder: RecyclerView.ViewHolder,
        val newHolder: RecyclerView.ViewHolder,
        val fromX: Int,
        val fromY: Int,
        val toX: Int,
        val toY: Int
    )

    private val pendingAdds = mutableListOf<RecyclerView.ViewHolder>()
    private val pendingRemoves = mutableListOf<RecyclerView.ViewHolder>()
    private val pendingMoves = mutableListOf<MoveInfo>()
    private val pendingChanges = mutableListOf<ChangeInfo>()

    /** Holder -> the dispatch call that must run when its animation ends/cancels. */
    private val running = LinkedHashMap<RecyclerView.ViewHolder, () -> Unit>()

    init {
        addDuration = 280L
        removeDuration = 230L
        moveDuration = 260L
        changeDuration = 240L
        setSupportsChangeAnimations(true)
    }

    override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
        reset(holder)
        val v = holder.itemView
        v.alpha = 0f
        v.translationY = -dp(v, 22f)
        v.scaleX = 0.9f
        v.scaleY = 0.9f
        pendingAdds.add(holder)
        return true
    }

    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        reset(holder)
        pendingRemoves.add(holder)
        return true
    }

    override fun animateMove(
        holder: RecyclerView.ViewHolder,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int
    ): Boolean {
        reset(holder)
        val v = holder.itemView
        // Pre-translate the view back to where it was, then glide to zero.
        v.translationX = (fromX - toX + v.translationX).toFloat()
        v.translationY = (fromY - toY + v.translationY).toFloat()
        pendingMoves.add(MoveInfo(holder, fromX, fromY, toX, toY))
        return true
    }

    override fun animateChange(
        oldHolder: RecyclerView.ViewHolder,
        newHolder: RecyclerView.ViewHolder,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int
    ): Boolean {
        if (oldHolder === newHolder) {
            return animateMove(oldHolder, fromX, fromY, toX, toY)
        }
        reset(oldHolder)
        reset(newHolder)
        oldHolder.itemView.alpha = 1f
        oldHolder.itemView.translationX = (fromX - toX).toFloat()
        oldHolder.itemView.translationY = (fromY - toY).toFloat()
        newHolder.itemView.alpha = 0f
        newHolder.itemView.scaleX = 0.96f
        newHolder.itemView.scaleY = 0.96f
        pendingChanges.add(ChangeInfo(oldHolder, newHolder, fromX, fromY, toX, toY))
        return true
    }

    override fun runPendingAnimations() {
        if (pendingAdds.isEmpty() && pendingRemoves.isEmpty() &&
            pendingMoves.isEmpty() && pendingChanges.isEmpty()
        ) return

        val moves = pendingMoves.toList().also { pendingMoves.clear() }
        val removes = pendingRemoves.toList().also { pendingRemoves.clear() }
        val adds = pendingAdds.toList().also { pendingAdds.clear() }
        val changes = pendingChanges.toList().also { pendingChanges.clear() }

        moves.forEachIndexed { index, info -> startMove(info, index * 12L) }
        removes.forEachIndexed { index, holder -> startRemove(holder, 20L + index * 18L) }
        adds.forEachIndexed { index, holder -> startAdd(holder, 60L + index * 22L) }
        changes.forEachIndexed { index, info -> startChange(info, 80L + index * 20L) }

        if (running.isEmpty()) dispatchAnimationsFinished()
    }

    private fun startAdd(holder: RecyclerView.ViewHolder, delay: Long) {
        val v = holder.itemView
        track(holder) { dispatchAddFinished(holder) }
        v.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(delay)
            .setDuration(addDuration)
            .setInterpolator(OvershootInterpolator(1.15f))
            .withEndAction { finish(holder) { dispatchAddFinished(holder) } }
            .start()
    }

    private fun startRemove(holder: RecyclerView.ViewHolder, delay: Long) {
        val v = holder.itemView
        val direction = if (holder.bindingAdapterPosition % 2 == 0) 1f else -1f
        track(holder) { dispatchRemoveFinished(holder) }
        v.animate()
            .alpha(0f)
            .translationX(direction * dp(v, 26f))
            .rotation(direction * 5f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setStartDelay(delay)
            .setDuration(removeDuration)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { finish(holder) { dispatchRemoveFinished(holder) } }
            .start()
    }

    private fun startMove(info: MoveInfo, delay: Long) {
        val holder = info.holder
        val v = holder.itemView
        track(holder) { dispatchMoveFinished(holder) }
        v.animate()
            .translationX(0f)
            .translationY(0f)
            .setStartDelay(delay)
            .setDuration(moveDuration)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .withEndAction { finish(holder) { dispatchMoveFinished(holder) } }
            .start()
    }

    private fun startChange(info: ChangeInfo, delay: Long) {
        val oldHolder = info.oldHolder
        val newHolder = info.newHolder
        val oldView = oldHolder.itemView
        val newView = newHolder.itemView
        track(oldHolder) { dispatchChangeFinished(oldHolder, true) }
        track(newHolder) { dispatchChangeFinished(newHolder, false) }
        oldView.animate()
            .alpha(0f)
            .translationX(oldView.translationX + dp(oldView, 12f))
            .translationY(oldView.translationY - dp(oldView, 8f))
            .scaleX(0.94f)
            .scaleY(0.94f)
            .setStartDelay(delay)
            .setDuration(changeDuration)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { finish(oldHolder) { dispatchChangeFinished(oldHolder, true) } }
            .start()
        newView.animate()
            .alpha(1f)
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(delay + 30L)
            .setDuration(changeDuration)
            .setInterpolator(OvershootInterpolator(1.08f))
            .withEndAction { finish(newHolder) { dispatchChangeFinished(newHolder, false) } }
            .start()
    }

    private fun track(holder: RecyclerView.ViewHolder, dispatch: () -> Unit) {
        running[holder] = dispatch
    }

    private fun finish(holder: RecyclerView.ViewHolder, dispatch: () -> Unit) {
        if (running.remove(holder) != null) {
            reset(holder)
            dispatch()
            if (running.isEmpty()) dispatchAnimationsFinished()
        }
    }

    override fun endAnimation(holder: RecyclerView.ViewHolder) {
        holder.itemView.animate().cancel()
        val dispatch = running.remove(holder)
        reset(holder)
        if (dispatch != null) dispatch()
        if (running.isEmpty()) dispatchAnimationsFinished()
    }

    override fun endAnimations() {
        val pendingAddHolders = pendingAdds.toList()
        val pendingRemoveHolders = pendingRemoves.toList()
        val pendingMoveHolders = pendingMoves.map { it.holder }
        val pendingChangesSnapshot = pendingChanges.toList()
        pendingAdds.clear()
        pendingRemoves.clear()
        pendingMoves.clear()
        pendingChanges.clear()

        for (holder in pendingAddHolders) {
            reset(holder)
            dispatchAddFinished(holder)
        }
        for (holder in pendingRemoveHolders) {
            reset(holder)
            dispatchRemoveFinished(holder)
        }
        for (holder in pendingMoveHolders) {
            reset(holder)
            dispatchMoveFinished(holder)
        }
        for (info in pendingChangesSnapshot) {
            reset(info.oldHolder)
            dispatchChangeFinished(info.oldHolder, true)
            reset(info.newHolder)
            dispatchChangeFinished(info.newHolder, false)
        }

        // Snapshot: dispatch callbacks can re-enter.
        val active = running.entries.toList()
        running.clear()
        for ((holder, dispatch) in active) {
            holder.itemView.animate().cancel()
            reset(holder)
            dispatch()
        }
        dispatchAnimationsFinished()
    }

    override fun isRunning(): Boolean = running.isNotEmpty() ||
        pendingAdds.isNotEmpty() || pendingRemoves.isNotEmpty() ||
        pendingMoves.isNotEmpty() || pendingChanges.isNotEmpty()

    private fun reset(holder: RecyclerView.ViewHolder) {
        val v = holder.itemView
        v.alpha = 1f
        v.translationX = 0f
        v.translationY = 0f
        v.scaleX = 1f
        v.scaleY = 1f
        v.rotation = 0f
    }

    private fun dp(view: View, value: Float): Float =
        value * view.resources.displayMetrics.density
}
