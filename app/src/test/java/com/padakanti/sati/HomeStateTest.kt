package com.padakanti.sati

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class HomeStateTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun t(h: Int, m: Int) = ZonedDateTime.of(2026, 11, 2, h, m, 0, 0, zone)
    private fun ms(z: ZonedDateTime) = z.toInstant().toEpochMilli()
    private val plan = Plan(60, true, Hours(7 * 60, 22 * 60), Hours(8 * 60, 22 * 60))

    @Test fun offWinsOverEverything() =
        assertEquals(HomeState.Off, HomeState.of(false, ms(t(12, 0)), ms(t(11, 0)), t(10, 0), plan))

    @Test fun activePauseShowsPaused() =
        assertEquals(HomeState.Paused(ms(t(12, 0))), HomeState.of(true, ms(t(12, 0)), ms(t(12, 30)), t(10, 0), plan))

    @Test fun endedPauseShowsOn() =
        assertEquals(HomeState.On(ms(t(10, 40)), false), HomeState.of(true, ms(t(9, 0)), ms(t(10, 40)), t(10, 0), plan))

    @Test fun nightShowsResting() =
        assertEquals(HomeState.On(ms(t(7, 10)), true), HomeState.of(true, 0, ms(t(7, 10)), t(3, 0), plan))
}
