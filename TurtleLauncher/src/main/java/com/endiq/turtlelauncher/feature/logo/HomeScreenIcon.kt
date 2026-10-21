package com.endiq.turtlelauncher.feature.logo

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.endiq.turtlelauncher.feature.log.Logging

/**
 * Switches the launcher's home-screen icon to match the picked logo.
 *
 * Android does not allow an app to set its icon to an arbitrary image at runtime. The
 * only supported route is a set of pre-declared `activity-alias` launcher entries (see
 * AndroidManifest.xml), with exactly one enabled at a time. So only the [PRESETS] below
 * can become the home-screen icon. Any other logo restores the default turtle icon; the
 * in-app logo still changes for every logo.
 */
object HomeScreenIcon {
    private const val TAG = "HomeScreenIcon"
    private const val CLASS_PREFIX = "com.endiq.turtlelauncher."
    private const val DEFAULT_ALIAS = "HomeIconDefault"

    /** Pack file name (as extracted into DIR_CUSTOM_LOGO) -> alias class name. */
    val PRESETS: Map<String, String> = linkedMapOf(
        "0579-lavender-orbit-sparkles.png" to "HomeIconPreset1",
        "0308-ocean-forest-bubbles.png" to "HomeIconPreset2",
        "1963-gold-dunes-sparkles.png" to "HomeIconPreset3",
        "0194-emerald-orbit-stars.png" to "HomeIconPreset4",
        "1547-violet-sunset-sparkles.png" to "HomeIconPreset5",
    )

    fun isPreset(logoFileName: String?): Boolean =
        logoFileName != null && PRESETS.containsKey(logoFileName)

    /**
     * Enables the alias for [logoFileName], or the default icon when it isn't a preset.
     * Returns true when the switch succeeded.
     */
    fun apply(context: Context, logoFileName: String?): Boolean {
        val pm = context.packageManager
        val targetName = PRESETS[logoFileName] ?: DEFAULT_ALIAS
        return try {
            // Enable the target first, then disable the rest, so there is never a moment
            // when the app has no launcher entry.
            pm.setComponentEnabledSetting(
                componentFor(context, targetName),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            (PRESETS.values + DEFAULT_ALIAS).filter { it != targetName }.forEach { name ->
                pm.setComponentEnabledSetting(
                    componentFor(context, name),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            true
        } catch (e: Exception) {
            Logging.e(TAG, "Could not switch the home-screen icon", e)
            false
        }
    }

    private fun componentFor(context: Context, alias: String) =
        ComponentName(context.packageName, CLASS_PREFIX + alias)
}
