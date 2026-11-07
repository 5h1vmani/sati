package com.padakanti.sati

/**
 * The vibration of a bell. Pure logic, no Android code, so it is unit tested.
 *
 * Each strike is one long vibration, so it can be felt in a pocket. Until
 * 1.3.1 a strike was two pulses of 0.3 seconds, which went unnoticed even
 * though Android ran it in full. Three pulses of 0.5 seconds were tried next,
 * and felt too long and unusual.
 */
object Vibration {
    const val STRIKE_MS = 1_500L

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
            timings += STRIKE_MS
        }
        return timings.toLongArray()
    }
}
