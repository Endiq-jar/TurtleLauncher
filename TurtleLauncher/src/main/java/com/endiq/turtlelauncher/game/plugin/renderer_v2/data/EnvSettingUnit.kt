/*
 * Turtle Launcher
 * Copyright (C) 2025 Endiq and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.endiq.turtlelauncher.game.plugin.renderer_v2.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.endiq.turtlelauncher.setting.unit.AbstractSettingUnit

/**
 * Setting units (sealed) for renderer-configurable environment variables
 * @param summary description text of the entry, provided by the plugin
 */
sealed class EnvSettingUnit(
    mmkvKey: String,
    defaultValue: String,
    val summary: String?,
) : AbstractSettingUnit<String>(mmkvKey, defaultValue) {

    override fun getValue(): String {
        return rendererEnvMMKV().getString(key, defaultValue)!!
            .also { state = it }
    }

    override fun saveValue(v: String): String {
        rendererEnvMMKV().putString(key, v).apply()
        return v
    }

    /**
     * Option-style env var: pick one value from the preset list
     * @param rawEnv raw env var config
     * @param values all selectable values (default included)
     */
    class Selectable(
        mmkvKey: String,
        val rawEnv: RendererConfig.Env.SelectableEnv,
        defaultValue: String,
        val values: List<String>,
        summary: String? = null,
    ) : EnvSettingUnit(mmkvKey, defaultValue, summary) {
        private val checkKey = "${mmkvKey}:check"

        /**
         * Whether this env var is currently enabled
         */
        var isEnabled by mutableStateOf(rawEnv.check != false)
            private set

        fun initCheck() {
            val mmkv = rendererEnvMMKV()
            val pluginDefault = rawEnv.check != false

            if (rawEnv.check == null) {
                isEnabled = true
            } else if (mmkv.containsKey(checkKey)) {
                isEnabled = mmkv.getBoolean(checkKey, pluginDefault)
            } else {
                isEnabled = pluginDefault
            }
        }

        fun saveCheck(enabled: Boolean) {
            isEnabled = enabled
            rendererEnvMMKV().putBoolean(checkKey, enabled).apply()
        }
    }

    /**
     * Free-form env var: the user can enter any value
     * @param rawEnv raw env var config
     */
    class Customizable(
        mmkvKey: String,
        val rawEnv: RendererConfig.Env.CustomizableEnv,
        defaultValue: String,
        summary: String? = null,
    ) : EnvSettingUnit(mmkvKey, defaultValue, summary)

    /**
     * Toggle-style env var: enable/disable the variable
     * Enabled sets the value [RendererConfig.Env.ToggleableEnv.value]; disabled sets nothing
     * @param rawEnv raw env var config
     * @param envValue the env var's actual value (used when toggle=true)
     */
    class Toggleable(
        mmkvKey: String,
        val rawEnv: RendererConfig.Env.ToggleableEnv,
        defaultValue: String,
        val envValue: String,
        summary: String? = null,
    ) : EnvSettingUnit(mmkvKey, defaultValue, summary) {
        /** Whether the toggle is currently on */
        val isEnabled: Boolean get() = state.isNotEmpty()
    }
}
