package com.movtery.zalithlauncher.startup

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.startup.Initializer
import com.google.android.material.color.DynamicColors
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.turtle.AnrWatchdog

/**
 * AndroidX Startup entry point for the parts of app startup that don't need to run before
 * anything else and can be expressed as a plain, declarative, dependency-ordered component
 * instead of inline statements in PojavApplication.onCreate().
 *
 * Deliberately NOT auto-run via the androidx.startup.InitializationProvider ContentProvider
 * (that fires before Application.onCreate() even starts) - Logging is a Kotlin `object`
 * whose init block resolves PathManager.DIR_LAUNCHER_LOG the first time anything touches it,
 * and PathManager.DIR_DATA isn't set until partway through PojavApplication.onCreate(). Auto-
 * discovery would run this before that assignment and silently break where logs get written.
 * Instead this is triggered on demand, at the exact point in onCreate() the old inline code
 * used to run, via:
 *
 *   AppInitializer.getInstance(context).initializeComponent(TurtleStartupInitializer::class.java)
 *
 * Still real AndroidX Startup - dependency-ordered (see [dependencies]) and deduplicated by
 * AppInitializer if ever triggered more than once - just not wired to the automatic timing
 * that doesn't fit this app's own initialization order.
 */
class TurtleStartupInitializer : Initializer<Unit> {
    companion object {
        private const val TAG = "TurtleStartupInitializer"
    }

    override fun create(context: Context) {
        // TurtleLauncher: always force AMOLED dark mode - no light theme override
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        // Material You dynamic color: on supported devices (Android 12+), derives the theme
        // from the system wallpaper; a no-op safe fallback to the static theme elsewhere.
        try {
            DynamicColors.applyToActivitiesIfAvailable(context.applicationContext as Application)
        } catch (t: Throwable) {
            Logging.e(TAG, "Failed to apply dynamic colors", t)
        }

        AnrWatchdog.start()
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
