package com.padakanti.sati

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class VibrationTest {
    @Test
    fun oneStrikeIsOneLongVibration() {
        // 0.85 seconds of short pulses went unnoticed on a real phone.
        assertArrayEquals(longArrayOf(0, 1_500), Vibration.pattern(1))
    }

    @Test
    fun strikesArePausedApart() {
        assertArrayEquals(longArrayOf(0, 1_500, 700, 1_500, 700, 1_500), Vibration.pattern(3))
    }

    @Test
    fun zeroStrikesStillVibratesOnce() {
        assertArrayEquals(Vibration.pattern(1), Vibration.pattern(0))
    }
}
