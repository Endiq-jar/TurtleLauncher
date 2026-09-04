package com.movtery.zalithlauncher.startup

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.startup.Initializer
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.log.NativeCrashCapture
import com.movtery.zalithlauncher.feature.turtle.AnrWatchdog
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.Settings

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
        // Turtle Launcher theme engine: Settings > Launcher > Launcher Theme (System / Light /
        // Dark, stored as AllSettings.launcherTheme). This initializer runs at the end of
        // PojavApplication.onCreate(), after PathManager has located the settings file, so the
        // persisted choice can be applied before any activity inflates its resources. The
        // default is dark — the launcher's native appearance. Light/System only take effect
        // after the launcher process restarts (the Settings row is marked "requires reboot"),
        // so no activity recreation dance is needed here.
        val launcherTheme = try {
            Settings.refreshSettings()
            AllSettings.launcherTheme.getValue()
        } catch (t: Throwable) {
            Logging.w("TurtleStartupInitializer", "Failed to read launcher theme, falling back to dark", t)
            "dark"
        }
        val nightMode = when (launcherTheme) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            else -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)

        // Note: no DynamicColors — the launcher ships one fixed brand palette (Turtle green on
        // solid dark/light surfaces). Material You wallpaper-derived colors would silently
        // replace it on Android 12+, which clashes with the launcher's visual identity.

        AnrWatchdog.start()

        // TurtleLauncher: surfaces the *previous* run's death if it was a native crash the OS
        // killed the whole process for (no Java code could run at that instant to log it
        // itself - see NativeCrashCapture's own doc comment). Same PathManager.DIR_DATA
        // ordering requirement as everything else in this initializer.
        NativeCrashCapture.checkAndReport(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
