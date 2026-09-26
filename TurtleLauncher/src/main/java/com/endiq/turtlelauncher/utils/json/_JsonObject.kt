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

package com.endiq.turtlelauncher.utils.json

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

fun JsonObject.merge(other: JsonObject) {
    other.entrySet().forEach { (key, otherValue) ->
        when (val currentValue = this.get(key)) {
            //Same-named object present: merge recursively
            is JsonObject -> if (otherValue is JsonObject) {
                currentValue.merge(otherValue)
            } else {
                // Different types: overwrite directly
                this.add(key, otherValue.deepCopy())
            }

            //Same-named array present: append elements
            is JsonArray -> if (otherValue is JsonArray) {
                otherValue.forEach { element ->
                    currentValue.add(element.deepCopy())
                }
            } else {
                this.add(key, otherValue.deepCopy())
            }

            //Missing or simple type: overwrite directly
            else -> this.add(key, otherValue.deepCopy())
        }
    }
}

fun JsonObject.safeGetMember(memberName: String): String {
    return this.get(memberName)?.takeIf { it.isJsonPrimitive }?.asString ?: ""
}

/**
 * Quickly parses into a JsonObject
 */
fun String.parseToJson(): JsonObject = JsonParser.parseString(this).asJsonObject