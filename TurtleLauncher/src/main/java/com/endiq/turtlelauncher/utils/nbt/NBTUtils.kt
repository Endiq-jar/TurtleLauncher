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

package com.endiq.turtlelauncher.utils.nbt

import com.github.steveice10.opennbt.tag.builtin.ByteTag
import com.github.steveice10.opennbt.tag.builtin.CompoundTag
import com.github.steveice10.opennbt.tag.builtin.DoubleTag
import com.github.steveice10.opennbt.tag.builtin.FloatTag
import com.github.steveice10.opennbt.tag.builtin.IntTag
import com.github.steveice10.opennbt.tag.builtin.ListTag
import com.github.steveice10.opennbt.tag.builtin.LongTag
import com.github.steveice10.opennbt.tag.builtin.ShortTag
import com.github.steveice10.opennbt.tag.builtin.StringTag
import com.github.steveice10.opennbt.tag.builtin.Tag

private inline fun <reified T : Tag, R> CompoundTag.getAs(key: String, crossinline mapper: (T) -> R, defaultValue: R): R {
    return (this.get(key) as? T)?.let(mapper) ?: defaultValue
}

/** Gets the CompoundTag of the given key */
fun CompoundTag.asCompoundTag(key: String): CompoundTag? {
    return this.get(key) as? CompoundTag
}

/**
 * Gets the Boolean of the given key, manually judged via ByteTag
 */
fun CompoundTag.asBoolean(key: String, defaultValue: Boolean?): Boolean? {
    return asByte(key, defaultValue?.let { if (it) 1 else 0 })?.let { it == 1.toByte() }
}

/**
 * Gets the Boolean of the given key, manually judged via ByteTag
 */
fun CompoundTag.asBooleanNotNull(key: String, defaultValue: Boolean): Boolean {
    return asByteNotNull(key, if (defaultValue) 1 else 0) == 1.toByte()
}

/** Gets the Byte of the given key */
fun CompoundTag.asByte(key: String, defaultValue: Byte?): Byte? {
    return getAs<ByteTag, Byte?>(key, { it.value }, defaultValue)
}

/** Gets the Byte of the given key */
fun CompoundTag.asByteNotNull(key: String, defaultValue: Byte): Byte {
    return getAs<ByteTag, Byte>(key, { it.value }, defaultValue)
}

/** Gets the Short of the given key */
fun CompoundTag.asShort(key: String, defaultValue: Short?): Short? {
    return getAs<ShortTag, Short?>(key, { it.value }, defaultValue)
}

/** Gets the Short of the given key */
fun CompoundTag.asShortNotNull(key: String, defaultValue: Short): Short {
    return getAs<ShortTag, Short>(key, { it.value }, defaultValue)
}

/** Gets the Int of the given key */
fun CompoundTag.asInt(key: String, defaultValue: Int?): Int? {
    return getAs<IntTag, Int?>(key, { it.value }, defaultValue)
}

/** Gets the Int of the given key */
fun CompoundTag.asIntNotNull(key: String, defaultValue: Int): Int {
    return getAs<IntTag, Int>(key, { it.value }, defaultValue)
}

/** Gets the Long of the given key */
fun CompoundTag.asLong(key: String, defaultValue: Long?): Long? {
    return getAs<LongTag, Long?>(key, { it.value }, defaultValue)
}

/** Gets the Long of the given key */
fun CompoundTag.asLongNotNull(key: String, defaultValue: Long): Long {
    return getAs<LongTag, Long>(key, { it.value }, defaultValue)
}

/** Gets the Float of the given key */
fun CompoundTag.asFloat(key: String, defaultValue: Float?): Float? {
    return getAs<FloatTag, Float?>(key, { it.value }, defaultValue)
}

/** Gets the Float of the given key */
fun CompoundTag.asFloatNotNull(key: String, defaultValue: Float): Float {
    return getAs<FloatTag, Float>(key, { it.value }, defaultValue)
}

/** Gets the Double of the given key */
fun CompoundTag.asDouble(key: String, defaultValue: Double?): Double? {
    return getAs<DoubleTag, Double?>(key, { it.value }, defaultValue)
}

/** Gets the Double of the given key */
fun CompoundTag.asDoubleNotNull(key: String, defaultValue: Double): Double {
    return getAs<DoubleTag, Double>(key, { it.value }, defaultValue)
}

/** Gets the String of the given key */
fun CompoundTag.asString(key: String, defaultValue: String?): String? {
    return getAs<StringTag, String?>(key, { it.value }, defaultValue)
}

/** Gets the String of the given key */
fun CompoundTag.asStringNotNull(key: String, defaultValue: String): String {
    return getAs<StringTag, String>(key, { it.value }, defaultValue)
}

/** Gets the tag list of the given key */
fun CompoundTag.asList(key: String, defaultValue: ListTag?): ListTag? {
    return getAs<ListTag, ListTag?>(key, { it }, defaultValue)
}
