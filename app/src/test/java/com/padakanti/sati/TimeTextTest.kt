package com.padakanti.sati

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class TimeTextTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun ms(day: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone).toInstant().toEpochMilli()
    private fun rel(next: Long, now: Long) = TimeText.relative(next, now, zone, Locale.ENGLISH)

    @Test fun intervals() {
        assertEquals("15 min", TimeText.shortInterval(15))
        assertEquals("2 h", TimeText.shortInterval(120))
        assertEquals("1 h 30", TimeText.shortInterval(90))
        assertEquals("hour", TimeText.longInterval(60))
        assertEquals("2 hours", TimeText.longInterval(120))
        assertEquals("1 h 30 min", TimeText.longInterval(90))
        assertEquals("45 minutes", TimeText.longInterval(45))
    }

    @Test fun lengths() {
        assertEquals("20 min", TimeText.length(20))
        assertEquals("1 h", TimeText.length(60))
        assertEquals("1 h 15 min", TimeText.length(75))
    }

    @Test fun relativeToday() {
        val now = ms(2, 10, 0)
        assertEquals("in under a minute", rel(now + 20_000, now))
        assertEquals("in 1 min", rel(now + 30_000 + 30_000, now))
        assertEquals("in 23 min", rel(ms(2, 10, 23), now))
        assertEquals("in 2 h", rel(ms(2, 12, 0), now))
        assertEquals("in 1 h 5 min", rel(ms(2, 11, 5), now))
    }

    @Test fun relativeLaterDays() {
        val now = ms(2, 23, 30)
        // 1 November 2026 is a Sunday, so the 7th is a Saturday.
        assertEquals("tomorrow", rel(ms(3, 0, 10), now))
        assertEquals("on Saturday", rel(ms(7, 8, 0), now))
        assertEquals("", rel(0, now))
    }

    @Test fun clock() {
        assertEquals("07:05", TimeText.clock(7 * 60 + 5, is24Hour = true, Locale.ENGLISH))
        assertEquals("7:05 PM", TimeText.clock(19 * 60 + 5, is24Hour = false, Locale.ENGLISH))
    }

    @Test fun countdownRoundsUp() {
        assertEquals("0:00", TimeText.countdown(0))
        assertEquals("0:01", TimeText.countdown(1))
        assertEquals("4:05", TimeText.countdown(245_000))
        assertEquals("1:02:05", TimeText.countdown(3_725_000))
        assertEquals("0:00", TimeText.countdown(-5))
    }

    @Test fun stagesAndSettle() {
        assertEquals("20 min", TimeText.stages(listOf(Stage(20))))
        assertEquals("30 + 30 min", TimeText.stages(listOf(Stage(30), Stage(30))))
        assertEquals("30 seconds", TimeText.settle(30))
        assertEquals("1 minute", TimeText.settle(60))
        assertEquals("2 minutes", TimeText.settle(120))
    }

    @Test fun rhythmHintUsesSchedulerLimits() =
        assertEquals(
            "Bells come at unexpected times, 45 to 75 minutes apart. The mind does not learn to tune them out.",
            TimeText.rhythmHint(random = true, min = 60),
        )
}
