package com.endiq.turtlelauncher.support.touch_controller

import android.view.MotionEvent
import android.view.View
import top.fifthlight.touchcontroller.proxy.client.LauncherProxyClient
import top.fifthlight.touchcontroller.proxy.data.Offset

/**
 * Touch points are handled here to feed the TouchController control proxy.
 */
object ContactHandler {
    private val pointerIds = TouchPointerMap()

    private fun MotionEvent.getOffset(index: Int, view: View): Offset? =
        normalizeTouchOffset(getX(index), getY(index), view.width, view.height)
            ?.let { Offset(it.x, it.y) }

    fun progressEvent(event: MotionEvent, view: View) {
        val client = ControllerProxy.getProxyClient() ?: return

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // A fresh gesture must not inherit pointer mappings if Android omitted a prior
                // UP/CANCEL (for example when the view loses focus during a rotation).
                client.clearPointer()
                pointerIds.clear()
                handlePointerDown(event, client, 0, view)
            }

            MotionEvent.ACTION_POINTER_DOWN -> handlePointerDown(event, client, event.actionIndex, view)

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pointerId = pointerIds.clientIdFor(event.getPointerId(i)) ?: continue
                    val offset = event.getOffset(i, view) ?: continue
                    client.addPointer(pointerId, offset)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                client.clearPointer()
                pointerIds.clear()
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val androidPointerId = event.getPointerId(event.actionIndex)
                // The map is keyed by Android's pointer ID, not the generated client ID.
                // Deleting with the latter left stale entries when Android reused an ID.
                pointerIds.remove(androidPointerId)?.let(client::removePointer)
            }
        }
    }

    private fun handlePointerDown(event: MotionEvent, client: LauncherProxyClient, index: Int, view: View) {
        val offset = event.getOffset(index, view) ?: return
        val androidPointerId = event.getPointerId(index)
        val pointerId = pointerIds.register(androidPointerId)
        client.addPointer(pointerId, offset)
    }
}
