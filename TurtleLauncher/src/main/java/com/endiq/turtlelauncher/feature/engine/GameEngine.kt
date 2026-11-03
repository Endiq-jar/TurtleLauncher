package com.endiq.turtlelauncher.feature.engine

import androidx.annotation.StringRes
import com.endiq.turtlelauncher.R

/**
 * Which runtime the launcher's Play button starts.
 *
 * The launcher ships the Java engine ([DEVICE]) - a bundled JVM plus a renderer plugin, which is
 * what runs worlds, mods and servers today. [HORIZON] hands the launch to a separately installed
 * Horizon engine app instead of starting the JVM.
 *
 * Horizon is a native (Rust, wgpu/Vulkan) Minecraft runtime with its own Android builds; it is
 * not a library this APK can embed, and it prepares its own game resources. So "switching
 * engines" here means *which app gets the launch*, never something silently emulated: if the
 * Horizon app is missing or unsupported the launcher falls back to [DEVICE] and says so.
 */
enum class GameEngine(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
) {
    DEVICE("device", R.string.engine_device_title, R.string.engine_device_summary),
    HORIZON("horizon", R.string.engine_horizon_title, R.string.engine_horizon_summary);

    companion object {
        const val DEFAULT_ID: String = "device"

        /** Maps a stored id (including unknown/legacy values) to an engine, defaulting to DEVICE. */
        @JvmStatic
        fun fromId(value: String?): GameEngine =
            values().firstOrNull { it.id == value?.trim() } ?: DEVICE
    }
}
