package com.movtery.zalithlauncher.setting.unit

import com.movtery.zalithlauncher.setting.Settings.Manager

class LongSettingUnit(key: String, defaultValue: Long) : AbstractSettingUnit<Long>(key, defaultValue) {
    override fun getValue() = Manager.getValue(key, defaultValue) { it.toLongOrNull() }
}