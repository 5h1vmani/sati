package com.padakanti.sati.log

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Which day an entry belongs to. A day ends at 3 AM local time, so a sitting
 * at 1 AM counts for the day before. The 3 AM rule is fixed, not a setting.
 * A day is a local date as `LocalDate.toEpochDay()`.
 */
object LogDay {
    const val ROLLOVER_HOURS = 3L

    /** The day of [epochMillis] in [zone]. */
    fun of(epochMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()
            .minusHours(ROLLOVER_HOURS).toLocalDate().toEpochDay()

    fun date(day: Long): LocalDate = LocalDate.ofEpochDay(day)
}
