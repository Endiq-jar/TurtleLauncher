package com.endiq.turtlelauncher.feature

import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.feature.version.Version
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.MCVersionRegex
import com.endiq.turtlelauncher.utils.ZHTools
import org.jackhuang.hmcl.util.versioning.VersionNumber
import org.jackhuang.hmcl.util.versioning.VersionRange


class ProfileLanguageSelector {
    companion object {
        private fun getOlderLanguage(lang: String): String {
            val underscoreIndex = lang.indexOf('_')
            return if (underscoreIndex != -1) {
                // Only uppercase the part after the underscore.
                val builder = StringBuilder(lang.substring(0, underscoreIndex + 1))
                builder.append(lang.substring(underscoreIndex + 1).uppercase())
                builder.toString()
            } else lang
        }

        private fun getLanguage(minecraftVersion: Version, rawLang: String): String {
            val lang = if (rawLang == "system") ZHTools.getSystemLanguage() else rawLang

            val version: String = minecraftVersion.getVersionInfo()?.minecraftVersion ?: "1.11"

            val versionId = VersionNumber.asVersion(version).canonical
            Logging.i("ProfileLanguageSelector", "Version Id : $versionId")

            return when {
                versionId.contains('.') -> {
                    if (isOlderVersionRelease(versionId)) getOlderLanguage(lang) // 1.10 -
                    else lang
                }
                MCVersionRegex.SNAPSHOT_REGEX.matcher(versionId).matches() -> { // snapshot ids like "24w09a" / "16w20a"
                    if (isOlderVersionSnapshot(versionId)) getOlderLanguage(lang)
                    else lang
                }
                else -> lang
            }
        }

        private fun isOlderVersionRelease(versionName: String): Boolean {
            return VersionRange.atMost(VersionNumber.asVersion("1.10.2")).contains(VersionNumber.asVersion(versionName))
        }

        private fun isOlderVersionSnapshot(versionName: String): Boolean {
            return VersionRange.atMost(VersionNumber.asVersion("16w32a")).contains(VersionNumber.asVersion(versionName))
        }

        /**
         * The value a freshly installed profile starts with (see assets/options.txt).
         * It is not a language Minecraft actually ships, so the game falls back to
         * English - which is exactly what "no language chosen yet" means here.
         */
        private const val BUNDLED_DEFAULT_LANG = "en_mm"

        @JvmStatic
        fun setGameLanguage(version: Version, overridden: Boolean) {
            val existing = MCOptions.get("lang")
            // Respect a language the player picked inside Minecraft's own Options
            // screen - unless they asked Settings to override it. The old check was
            // just `containsKey("lang")`, and since the bundled options.txt ships
            // `lang:en_mm`, that made Settings -> Game -> Language a no-op on every
            // install: the key was always present, so the chosen language was never
            // written. Treating only a non-default value as a real choice fixes it,
            // including on devices whose options.txt was copied before this change.
            val chosenInGame = !existing.isNullOrEmpty() && existing != BUNDLED_DEFAULT_LANG
            if (chosenInGame && !overridden) {
                Logging.i(
                    "ProfileLanguageSelector",
                    "Keeping the language chosen in-game: $existing (enable gameLanguageOverridden to override it)"
                )
                return
            }
            val language = getLanguage(version, AllSettings.setGameLanguage.getValue())
            Logging.i("ProfileLanguageSelector", "The game language has been set to: $language")
            MCOptions.set("lang", language)
        }
    }
}
