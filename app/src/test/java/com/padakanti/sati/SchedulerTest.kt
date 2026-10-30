package com.padakanti.sati

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

class SchedulerTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    // 2026-11-02 is a Monday, 2026-11-06 a Friday, 2026-11-07 a Saturday.
    private fun t(day: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone)

    private val fixed = Plan(
        intervalMin = 60,
        random = false,
        weekday = Hours(7 * 60, 22 * 60),
        weekend = Hours(8 * 60, 22 * 60),
    )
    private val random = fixed.copy(random = true)

    @Test fun fixedRingsOnTheNextHour() =
        assertEquals(t(2, 8, 0), Scheduler.next(t(2, 7, 30), fixed))

    @Test fun fixedNeverRepeatsTheCurrentBell() =
        assertEquals(t(2, 9, 0), Scheduler.next(t(2, 8, 0), fixed))

    @Test fun fixedRingsAtTheEndOfWakingHours() =
        assertEquals(t(2, 22, 0), Scheduler.next(t(2, 21, 10), fixed))

    @Test fun fixedWaitsForTheMorning() =
        assertEquals(t(2, 7, 0), Scheduler.next(t(2, 5, 0), fixed))

    @Test fun fixedUsesWeekendHoursOnSaturday() =
        assertEquals(t(7, 8, 0), Scheduler.next(t(6, 22, 30), fixed))

    @Test fun fixedSkipsThePause() {
        val paused = fixed.copy(pausedUntilMillis = t(2, 10, 15).toInstant().toEpochMilli())
        assertEquals(t(2, 11, 0), Scheduler.next(t(2, 9, 10), paused))
    }

    @Test fun randomGapIsBetween45And75Minutes() {
        val now = t(2, 10, 0)
        repeat(2000) { seed ->
            val gap = Duration.between(now, Scheduler.next(now, random, Random(seed))).toMinutes()
            assertTrue("gap $gap", gap in 45..75)
        }
    }

    @Test fun randomNearBedtimeMovesToEarlyNextMorning() {
        repeat(500) { seed ->
            val next = Scheduler.next(t(2, 21, 50), random, Random(seed))
            assertTrue("$next", !next.isBefore(t(3, 7, 0)) && !next.isAfter(t(3, 7, 30)))
        }
    }

    @Test fun randomStartsFromTheEndOfThePause() {
        val paused = random.copy(pausedUntilMillis = t(2, 10, 15).toInstant().toEpochMilli())
        repeat(500) { seed ->
            val next = Scheduler.next(t(2, 9, 10), paused, Random(seed))
            assertTrue("$next", !next.isBefore(t(2, 11, 0)) && !next.isAfter(t(2, 11, 30)))
        }
    }

    // Property check over many start times and seeds, in both modes: the
    // next bell is always in the future and always inside waking hours.
    @Test fun nextIsAlwaysFutureAndInsideWakingHours() {
        val rnd = Random(7)
        for (plan in listOf(fixed, random, fixed.copy(intervalMin = 45), random.copy(intervalMin = 15))) {
            repeat(3000) {
                val now = t(2, 0, 0).plusMinutes(rnd.nextLong(0, 7 * 24 * 60))
                val next = Scheduler.next(now, plan, rnd)
                assertTrue("$now -> $next", next.isAfter(now))
                assertTrue("$now -> $next", Scheduler.shouldRing(next, plan))
            }
        }
    }

    @Test fun lateBellStillRingsWithinGrace() {
        assertTrue(Scheduler.shouldRing(t(2, 22, 1), fixed))
        assertFalse(Scheduler.shouldRing(t(2, 22, 3), fixed))
        assertFalse(Scheduler.shouldRing(t(2, 6, 59), fixed))
    }

    // Strict check with its own window maths and no grace: every next bell is
    // in the future, inside that day's hours, and after any pause.
    @Test fun nextIsInsideHoursByIndependentCheck() {
        val rnd = Random(11)
        for (plan in listOf(fixed, random, fixed.copy(intervalMin = 90), random.copy(intervalMin = 20))) {
            repeat(3000) {
                val now = t(2, 0, 0).plusMinutes(rnd.nextLong(0, 14 * 24 * 60))
                val next = Scheduler.next(now, plan, rnd)
                val weekend = next.dayOfWeek.value >= 6
                val hours = if (weekend) plan.weekend else plan.weekday
                val minute = next.hour * 60 + next.minute
                assertTrue("$now -> $next", next.isAfter(now))
                assertTrue("$now -> $next", minute >= hours.startMin && minute <= hours.endMin)
            }
        }
    }

    @Test fun pauseAcrossMidnightResumesNextMorning() {
        val paused = fixed.copy(pausedUntilMillis = t(3, 1, 0).toInstant().toEpochMilli())
        assertEquals(t(3, 7, 0), Scheduler.next(t(2, 21, 30), paused))
    }

    @Test fun pauseIntoTheWeekendUsesWeekendHours() {
        val paused = random.copy(pausedUntilMillis = t(7, 6, 0).toInstant().toEpochMilli())
        repeat(300) { seed ->
            val next = Scheduler.next(t(6, 21, 30), paused, Random(seed))
            assertTrue("$next", !next.isBefore(t(7, 8, 0)) && !next.isAfter(t(7, 8, 30)))
        }
    }

    @Test fun randomOnWeekendStaysInWeekendHours() {
        repeat(300) { seed ->
            val next = Scheduler.next(t(7, 7, 0), random, Random(seed))
            assertTrue("$next", !next.isBefore(t(7, 8, 0)) && !next.isAfter(t(7, 8, 30)))
        }
    }

    @Test fun intervalLongerThanWindowGivesOneBellAtStart() {
        val short = fixed.copy(intervalMin = 120, weekday = Hours(9 * 60, 10 * 60))
        assertEquals(t(2, 9, 0), Scheduler.next(t(2, 8, 0), short))
        assertEquals(t(3, 9, 0), Scheduler.next(t(2, 9, 0), short))
    }

    @Test fun daylightSavingDayStillMovesForward() {
        // 2026-03-08 is the US spring-forward day: 02:00 jumps to 03:00.
        val ny = ZoneId.of("America/New_York")
        val early = fixed.copy(weekend = Hours(1 * 60, 5 * 60))
        var now = ZonedDateTime.of(2026, 3, 8, 0, 30, 0, 0, ny)
        val seen = mutableListOf<ZonedDateTime>()
        repeat(4) {
            now = Scheduler.next(now, early)
            seen += now
        }
        for (i in 1 until seen.size) assertTrue("$seen", seen[i].isAfter(seen[i - 1]))
    }

    @Test fun smallClockStepBackDoesNotRepeatTheBell() {
        val rang = t(2, 10, 0).toInstant().toEpochMilli()
        val now = rang - 20_000
        val base = Scheduler.base(now, rang)
        assertEquals(rang, base)
        val next = Scheduler.next(java.time.Instant.ofEpochMilli(base).atZone(zone), fixed)
        assertEquals(t(2, 11, 0), next)
    }

    @Test fun largeClockStepBackIgnoresTheOldBell() {
        val rang = t(2, 10, 0).toInstant().toEpochMilli()
        val now = rang - 3 * 3_600_000L
        assertEquals(now, Scheduler.base(now, rang))
    }

    @Test fun baseIsNowWhenNoBellIsAhead() {
        val now = t(2, 10, 0).toInstant().toEpochMilli()
        assertEquals(now, Scheduler.base(now, 0))
        assertEquals(now, Scheduler.base(now, now - 60_000))
    }

    @Test fun pausedBellDoesNotRing() {
        val paused = fixed.copy(pausedUntilMillis = t(2, 12, 0).toInstant().toEpochMilli())
        assertFalse(Scheduler.shouldRing(t(2, 11, 0), paused))
        assertTrue(Scheduler.shouldRing(t(2, 12, 0), paused))
    }
}
