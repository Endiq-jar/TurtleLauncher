package com.endiq.turtlelauncher.feature.engine

import android.content.Context
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.feature.version.Version
import com.endiq.turtlelauncher.setting.AllSettings

enum class HorizonLaunchOutcome {
    /** The on-device engine is selected; nothing was handed off. */
    NOT_SELECTED,

    /** Horizon is selected and supported, but no engine app is installed yet. */
    NOT_INSTALLED,

    /** The Horizon engine app was started; the Java launch path must not run. */
    LAUNCHED,

    /** Horizon is selected but cannot start; [HorizonLaunchResult.message] explains why. */
    FAILED,
}

data class HorizonLaunchResult(
    val outcome: HorizonLaunchOutcome,
    val message: String? = null,
)

/**
 * Reads/writes the engine choice and performs the hand-off for the Play button.
 *
 * Everything here is defensive: a launch never throws, and a broken/missing Horizon install only
 * ever produces a fallback to the on-device engine plus an explanation.
 */
object GameEngineManager {

    /** Current state of the Horizon engine, as shown in Phone settings. */
    data class HorizonStatus(
        val deviceSupported: Boolean,
        val installation: HorizonEngine.Installation?,
        val summary: String,
    )

    @JvmStatic
    fun selected(): GameEngine = GameEngine.fromId(AllSettings.gameEngine.getValue())

    @JvmStatic
    fun isHorizonSelected(): Boolean = selected() == GameEngine.HORIZON

    @JvmStatic
    fun select(engine: GameEngine) {
        AllSettings.gameEngine.put(engine.id).save()
    }

    /** Remembers which installed app is the Horizon engine (blank = auto-detect). */
    @JvmStatic
    fun selectHorizonPackage(packageName: String) {
        AllSettings.horizonEnginePackage.put(packageName.trim()).save()
    }

    @JvmStatic
    fun horizonStatus(context: Context): HorizonStatus {
        val supported = HorizonEngine.isDeviceSupported()
        val installation = if (supported) HorizonEngine.resolve(context) else null
        val summary = when {
            !supported -> context.getString(R.string.engine_status_unsupported_abi)
            installation == null -> context.getString(R.string.engine_status_missing)
            installation.versionName.isNullOrBlank() ->
                context.getString(R.string.engine_status_ready, installation.label)
            else -> context.getString(
                R.string.engine_status_ready_version,
                installation.label,
                installation.versionName,
            )
        }
        return HorizonStatus(supported, installation, summary)
    }

    /**
     * Called by the launcher before it starts the Java engine.
     *
     * [HorizonLaunchOutcome.LAUNCHED] means the Play press was fully handled; anything else means
     * the caller should continue with the on-device engine, showing [HorizonLaunchResult.message]
     * when it is not null.
     */
    @JvmStatic
    fun attemptHorizonLaunch(context: Context, version: Version?): HorizonLaunchResult {
        if (!isHorizonSelected()) return HorizonLaunchResult(HorizonLaunchOutcome.NOT_SELECTED)

        if (!HorizonEngine.isDeviceSupported()) {
            return HorizonLaunchResult(
                HorizonLaunchOutcome.FAILED,
                context.getString(R.string.engine_fallback_unsupported),
            )
        }

        val installation = HorizonEngine.resolve(context)
            ?: return HorizonLaunchResult(
                // Callers can offer the built-in download instead of silently bouncing to the JVM.
                HorizonLaunchOutcome.NOT_INSTALLED,
                context.getString(R.string.engine_fallback_not_installed),
            )

        val result = HorizonEngine.launch(
            context = context,
            installation = installation,
            versionName = version?.getVersionName(),
            gameDir = version?.getGameDir()?.absolutePath,
        )
        return result.fold(
            onSuccess = { HorizonLaunchResult(HorizonLaunchOutcome.LAUNCHED) },
            onFailure = { error ->
                HorizonLaunchResult(
                    HorizonLaunchOutcome.FAILED,
                    context.getString(
                        R.string.engine_fallback_failed,
                        error.message ?: installation.label,
                    ),
                )
            },
        )
    }
}
