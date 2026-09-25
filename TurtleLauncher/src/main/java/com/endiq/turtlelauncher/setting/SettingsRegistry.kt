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

package com.endiq.turtlelauncher.setting

import android.os.Parcelable
import androidx.compose.ui.geometry.Offset
import com.endiq.turtlelauncher.setting.unit.AbstractSettingUnit
import com.endiq.turtlelauncher.setting.unit.BooleanSettingUnit
import com.endiq.turtlelauncher.setting.unit.DEFAULT_FLOAT_RANGE
import com.endiq.turtlelauncher.setting.unit.DEFAULT_INT_RANGE
import com.endiq.turtlelauncher.setting.unit.DEFAULT_LONG_RANGE
import com.endiq.turtlelauncher.setting.unit.EnumSettingUnit
import com.endiq.turtlelauncher.setting.unit.FloatSettingUnit
import com.endiq.turtlelauncher.setting.unit.IntSettingUnit
import com.endiq.turtlelauncher.setting.unit.LongSettingUnit
import com.endiq.turtlelauncher.setting.unit.NullableIntSettingUnit
import com.endiq.turtlelauncher.setting.unit.OffsetSettingUnit
import com.endiq.turtlelauncher.setting.unit.StringListSettingUnit
import com.endiq.turtlelauncher.setting.unit.StringSettingUnit
import com.endiq.turtlelauncher.setting.unit.parcelableSettingUnit

abstract class SettingsRegistry {
    protected val refreshableList = mutableListOf<AbstractSettingUnit<*>>()

    fun reloadAll() = refreshableList.forEach { it.init() }

    protected fun boolSetting(key: String, def: Boolean) =
        BooleanSettingUnit(key, def).also { refreshableList.add(it) }

    protected fun intSetting(key: String, def: Int, valueRange: IntRange = DEFAULT_INT_RANGE) =
        IntSettingUnit(key, def, valueRange).also { refreshableList.add(it) }

    protected fun intSetting(key: String, def: Int, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE) =
        IntSettingUnit(key, def, min..max).also { refreshableList.add(it) }

    protected fun intSetting(key: String, def: Int?, valueRange: IntRange = DEFAULT_INT_RANGE) =
        NullableIntSettingUnit(key, def, valueRange).also { refreshableList.add(it) }

    protected fun intSetting(key: String, def: Int?, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE) =
        NullableIntSettingUnit(key, def, min..max).also { refreshableList.add(it) }

    protected fun floatSetting(key: String, def: Float, valueRange: ClosedFloatingPointRange<Float> = DEFAULT_FLOAT_RANGE) =
        FloatSettingUnit(key, def, valueRange).also { refreshableList.add(it) }

    protected fun floatSetting(key: String, def: Float, min: Float = Float.MIN_VALUE, max: Float = Float.MAX_VALUE) =
        FloatSettingUnit(key, def, min..max).also { refreshableList.add(it) }

    protected fun longSetting(key: String, def: Long, valueRange: LongRange = DEFAULT_LONG_RANGE) =
        LongSettingUnit(key, def, valueRange).also { refreshableList.add(it) }

    protected fun longSetting(key: String, def: Long, min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE) =
        LongSettingUnit(key, def, min..max).also { refreshableList.add(it) }

    protected fun offsetSetting(key: String, def: Offset) =
        OffsetSettingUnit(key, def).also { refreshableList.add(it) }

    protected fun stringSetting(key: String, def: String) =
        StringSettingUnit(key, def).also { refreshableList.add(it) }

    protected fun stringListSetting(key: String, def: List<String>) =
        StringListSettingUnit(key, def).also { refreshableList.add(it) }

    protected inline fun <reified E : Enum<E>> enumSetting(key: String, def: E, legacyNames: Map<String, E> = emptyMap()) =
        EnumSettingUnit(key, def) { raw ->
            enumValues<E>().firstOrNull { it.name == raw } ?: legacyNames[raw]
        }.also { refreshableList.add(it) }

    protected inline fun <reified E: Parcelable> parcelableSetting(key: String, def: E) =
        parcelableSettingUnit(key, def).also { refreshableList.add(it) }
}