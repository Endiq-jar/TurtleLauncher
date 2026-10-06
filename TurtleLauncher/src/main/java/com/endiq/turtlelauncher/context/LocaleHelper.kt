package com.endiq.turtlelauncher.context

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.setting.Settings
import com.endiq.turtlelauncher.utils.path.PathManager
import net.endiq.launcher.prefs.LauncherPreferences
import java.util.Locale

class LocaleHelper(context: Context) : ContextWrapper(context) {
    companion object {
        fun setLocale(context: Context): ContextWrapper {
            // Initialise the paths.
            PathManager.initContextConstants(context)
            // Refresh the launcher settings.
            Settings.refreshSettings()

            LauncherPreferences.loadPreferences()

            // Apply the launcher language chosen in Settings -> Launcher -> Language.
            val locale = selectedLocale()
            if (locale != null) {
                runCatching {
                    Locale.setDefault(locale)
                    val config = Configuration(context.resources.configuration)
                    config.setLocale(locale)
                    return LocaleHelper(context.createConfigurationContext(config))
                }
            }
            return LocaleHelper(context)
        }

        /** The locale picked in the Language setting, or null to follow the system. */
        @JvmStatic
        fun selectedLocale(): Locale? = runCatching {
            localeFor(AllSettings.launcherLanguage.getValue())
        }.getOrNull()

        /**
         * Turns a Minecraft-style language code from R.array.all_game_language_value
         * ("system", "pt_br", "zh_cn", "be_latn", "bar", ...) into a [Locale].
         * "system" (or blank) gives null. Odd codes the JDK doesn't know still produce a Locale,
         * so every entry in the list is selectable.
         */
        @JvmStatic
        fun localeFor(value: String?): Locale? {
            val code = value?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (code.isEmpty() || code == "system") return null
            val parts = code.split('_', '-').filter { it.isNotEmpty() }
            val language = parts.firstOrNull() ?: return null
            var region = ""
            var script = ""
            for (part in parts.drop(1)) {
                when (part.length) {
                    2 -> region = part.uppercase(Locale.ROOT)
                    4 -> script = part.replaceFirstChar { it.uppercase(Locale.ROOT) }
                }
            }
            return runCatching {
                Locale.Builder().setLanguage(language).setScript(script).setRegion(region).build()
            }.getOrElse {
                @Suppress("DEPRECATION")
                Locale(language, region)
            }
        }
    }
}
