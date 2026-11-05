package com.padakanti.sati.log

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class LogDayTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int, z: ZoneId = zone) =
        LocalDateTime.of(y, m, d, h, min).atZone(z).toInstant().toEpochMilli()

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    @Test fun afternoonIsTheSameDay() = assertEquals(day(2026, 11, 5), LogDay.of(millis(2026, 11, 5, 15, 0), zone))

    @Test fun oneAmBelongsToTheDayBefore() = assertEquals(day(2026, 11, 4), LogDay.of(millis(2026, 11, 5, 1, 0), zone))

    @Test fun twoFiftyNineIsStillTheDayBefore() = assertEquals(day(2026, 11, 4), LogDay.of(millis(2026, 11, 5, 2, 59), zone))

    @Test fun threeAmStartsTheNewDay() = assertEquals(day(2026, 11, 5), LogDay.of(millis(2026, 11, 5, 3, 0), zone))

    @Test fun midnightAtTheEndOfAMonthBelongsToTheMonthBefore() =
        assertEquals(day(2026, 11, 30), LogDay.of(millis(2026, 12, 1, 0, 30), zone))

    @Test fun usesTheZoneGiven() {
        val instant = millis(2026, 11, 5, 1, 0, ZoneId.of("UTC"))
        assertEquals(day(2026, 11, 4), LogDay.of(instant, ZoneId.of("UTC")))
        // The same moment is 06:30 in India.
        assertEquals(day(2026, 11, 5), LogDay.of(instant, zone))
    }

    @Test fun roundTripsWithDate() = assertEquals(LocalDate.of(2026, 11, 5), LogDay.date(day(2026, 11, 5)))
}
