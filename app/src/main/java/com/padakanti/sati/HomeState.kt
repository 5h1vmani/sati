package com.padakanti.sati

import java.time.ZonedDateTime

/** What the top of the screen and the tile say. Every case has its own look. */
sealed interface HomeState {
    /** The bell is turned off. */
    data object Off : HomeState

    /** The user paused the bell. It resumes by itself at [until]. */
    data class Paused(val until: Long) : HomeState

    /**
     * The bell is on. [next] is the next bell in epoch milliseconds.
     * [restingNow] is true outside waking hours, when the next bell is the
     * first one of the next window.
     */
    data class On(val next: Long, val restingNow: Boolean) : HomeState

    companion object {
        fun of(enabled: Boolean, pausedUntil: Long, nextRing: Long, now: ZonedDateTime, plan: Plan): HomeState =
            when {
                !enabled -> Off
                pausedUntil > now.toInstant().toEpochMilli() -> Paused(pausedUntil)
                else -> On(nextRing, restingNow = !Scheduler.shouldRing(now, plan.copy(pausedUntilMillis = 0)))
            }
    }
}
