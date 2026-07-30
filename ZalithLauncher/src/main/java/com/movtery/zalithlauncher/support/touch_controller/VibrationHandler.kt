package com.movtery.zalithlauncher.support.touch_controller

import android.os.VibrationEffect
import android.os.Vibrator
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.setting.AllSettings
import top.fifthlight.touchcontroller.proxy.client.LauncherProxyClient
import top.fifthlight.touchcontroller.proxy.message.VibrateMessage

class VibrationHandler(private val vibrator: Vibrator) : LauncherProxyClient.VibrationHandler {
    override fun viberate(kind: VibrateMessage.Kind) {
        runCatching {
            val effect = VibrationEffect.createOneShot(
                AllSettings.tcVibrateDuration.getValue().coerceAtMost(500).coerceAtLeast(80).toLong(),
                VibrationEffect.DEFAULT_AMPLITUDE
            )
            vibrator.vibrate(effect)
        }.getOrElse {
            Logging.e("TouchController_VibrationHandler", "Failed to attempt vibrating the device!", it)
        }
    }
}