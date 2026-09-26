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

package com.endiq.turtlelauncher.utils.string

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val EN_US_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.MEDIUM)
    .withLocale(Locale.US)
    .withZone(ZoneId.systemDefault())

private val ISO_DATE_TIME: DateTimeFormatter = DateTimeFormatterBuilder()
    .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    .optionalStart().appendOffset("+HH:MM", "+00:00").optionalEnd()
    .optionalStart().appendOffset("+HHMM", "+0000").optionalEnd()
    .optionalStart().appendOffset("+HH", "Z").optionalEnd()
    .optionalStart().appendOffsetId().optionalEnd()
    .toFormatter()

/**
 * Parses a string into an Instant
 * @param string the date-time string
 * @return the parsed Instant
 * @throws IllegalArgumentException when the string matches no supported format
 */
fun parseInstant(string: String): Instant {
    val parsers = listOf<(String) -> Instant>(
        { ZonedDateTime.parse(it, EN_US_FORMAT).toInstant() },
        { ZonedDateTime.parse(it, ISO_DATE_TIME).toInstant() },
        { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .atZone(ZoneId.systemDefault())
            .toInstant() }
    )

    return parsers.firstNotNullOfOrNull { parser ->
        try {
            parser(string)
        } catch (_: DateTimeParseException) {
            null
        }
    } ?: throw IllegalArgumentException("Invalid instant format: $string. Supported formats: EN_US localized, ISO with offset, ISO local date time")
}

/**
 * Serializes an Instant into a string
 * @param instant the Instant to convert
 * @param zone the timezone; defaults to the system default
 * @return the formatted date-time string
 */
fun formatInstant(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
        ZonedDateTime.ofInstant(instant, zone).truncatedTo(ChronoUnit.SECONDS)
    )
}

/**
 * Serializes an Instant using the given timezone ID
 * @param instant the Instant to convert
 * @param zoneId a timezone ID, e.g. "Asia/Shanghai"
 * @return the formatted date-time string
 */
fun formatInstant(instant: Instant, zoneId: String): String {
    return formatInstant(instant, ZoneId.of(zoneId))
}

/**
 * Safely parses an Instant, returning null on failure
 * @param string the date-time string
 * @return the parsed Instant, or null on failure
 */
fun parseInstantOrNull(string: String): Instant? {
    return try {
        parseInstant(string)
    } catch (_: IllegalArgumentException) {
        null
    }
}