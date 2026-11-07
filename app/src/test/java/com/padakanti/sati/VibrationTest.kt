package com.padakanti.sati

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class VibrationTest {
    @Test
    fun oneStrikeIsOneVibrationOfOneSecond() {
        // 0.85 seconds of short pulses went unnoticed on a real phone. 1.5 seconds felt too long.
        assertArrayEquals(longArrayOf(0, 1_000), Vibration.pattern(1))
    }

    @Test
    fun strikesArePausedApart() {
        assertArrayEquals(longArrayOf(0, 1_000, 700, 1_000, 700, 1_000), Vibration.pattern(3))
    }

    @Test
    fun zeroStrikesStillVibratesOnce() {
        assertArrayEquals(Vibration.pattern(1), Vibration.pattern(0))
    }
}
