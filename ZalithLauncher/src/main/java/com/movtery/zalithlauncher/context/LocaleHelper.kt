package com.movtery.zalithlauncher.context

import android.content.Context
import android.content.ContextWrapper
import com.movtery.zalithlauncher.setting.Settings
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.prefs.LauncherPreferences

class LocaleHelper(context: Context) : ContextWrapper(context) {
    companion object {
        fun setLocale(context: Context): ContextWrapper {
            runCatching { PathManager.initContextConstants(context) }
            runCatching { Settings.refreshSettings() }
            runCatching { LauncherPreferences.loadPreferences() }
            return LocaleHelper(context)
        }
    }
}