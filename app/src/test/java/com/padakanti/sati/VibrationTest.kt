package com.padakanti.sati

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VibrationTest {
    @Test
    fun oneStrikeIsThreeHalfSecondPulses() {
        assertArrayEquals(longArrayOf(0, 500, 300, 500, 300, 500), Vibration.pattern(1))
    }

    @Test
    fun oneStrikeLastsAboutTwoSeconds() {
        // 0.85 seconds went unnoticed on a real phone. The bell must last long enough to feel.
        assertEquals(2_100L, Vibration.pattern(1).sum())
    }

    @Test
    fun strikesArePausedApart() {
        val three = Vibration.pattern(3)
        assertEquals(1 + 3 * 5 + 2, three.size)
        assertEquals(3 * 2_100L + 2 * 700L, three.sum())
        // Off, then on: even positions are pauses, odd positions are pulses.
        assertEquals(listOf(0L, 300L, 300L, 700L, 300L, 300L, 700L, 300L, 300L), three.filterIndexed { i, _ -> i % 2 == 0 })
    }

    @Test
    fun zeroStrikesStillVibratesOnce() {
        assertArrayEquals(Vibration.pattern(1), Vibration.pattern(0))
    }
}
