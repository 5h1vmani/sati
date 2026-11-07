package com.padakanti.sati

/**
 * The vibration of a bell. Pure logic, no Android code, so it is unit tested.
 *
 * Each strike is three firm pulses, about 2 seconds in all, so it can be felt
 * in a pocket. Until 1.3.1 a strike was two pulses of 0.3 seconds. That was
 * too short: Android ran it in full, but it went unnoticed.
 */
object Vibration {
    const val PULSE_MS = 500L
    const val PULSE_GAP_MS = 300L
    const val PULSES = 3

    /** The pause between strikes of a sadhana bell. */
    const val STRIKE_GAP_MS = 700L

    /**
     * Timings for [android.os.VibrationEffect.createWaveform]: off, on, off,
     * on, and so on, starting with no delay. One strike per [times].
     */
    fun pattern(times: Int): LongArray {
        val timings = mutableListOf(0L)
        repeat(times.coerceAtLeast(1)) { strike ->
            if (strike > 0) timings += STRIKE_GAP_MS
            repeat(PULSES) { pulse ->
                if (pulse > 0) timings += PULSE_GAP_MS
                timings += PULSE_MS
            }
        }
        return timings.toLongArray()
    }
}
