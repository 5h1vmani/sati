package com.padakanti.sati

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.random.Random

/** Waking hours for one kind of day, in minutes after midnight. End is after start. */
data class Hours(val startMin: Int, val endMin: Int)

/** Everything the scheduler needs. It has no Android types, so it runs in plain unit tests. */
data class Plan(
    val intervalMin: Int,
    val random: Boolean,
    val weekday: Hours,
    val weekend: Hours,
    /** Epoch milliseconds. 0 means not paused. */
    val pausedUntilMillis: Long = 0,
)

object Scheduler {
    /** In random mode, gaps are this share of the interval, low to high. */
    const val RANDOM_LOW = 0.75
    const val RANDOM_HIGH = 1.25

    /** A bell that fires up to this many minutes after waking hours end still rings. */
    const val LATE_GRACE_MIN = 2L

    /** A past bell may push the next search start at most this far ahead of now. */
    const val MAX_CLOCK_STEP_MS = 15 * 60_000L

    /**
     * Where the search for the next bell starts, in epoch milliseconds.
     * [notBefore] is a bell that already fired. When the clock steps back a
     * little, that bell is still ahead of now, and starting after it stops a
     * repeat. A larger step means the clock was set back on purpose, so the
     * old bell is ignored.
     */
    fun base(nowMillis: Long, notBefore: Long): Long =
        if (notBefore > nowMillis && notBefore - nowMillis <= MAX_CLOCK_STEP_MS) notBefore else nowMillis

    /** Returns the next bell time strictly after [now], inside waking hours and after any pause. */
    fun next(now: ZonedDateTime, plan: Plan, rnd: Random = Random.Default): ZonedDateTime {
        var base = now
        if (plan.pausedUntilMillis > 0) {
            val until = Instant.ofEpochMilli(plan.pausedUntilMillis).atZone(now.zone)
            if (until.isAfter(base)) base = until
        }
        return if (plan.random) nextRandom(base, plan, rnd) else nextFixed(base, plan)
    }

    /** True when a bell due now should sound: inside waking hours and not paused. */
    fun shouldRing(now: ZonedDateTime, plan: Plan): Boolean {
        if (plan.pausedUntilMillis > now.toInstant().toEpochMilli()) return false
        val date = now.toLocalDate()
        val start = windowStart(date, now, plan)
        val end = windowEnd(date, now, plan).plusMinutes(LATE_GRACE_MIN)
        return !now.isBefore(start) && !now.isAfter(end)
    }

    fun hoursFor(date: LocalDate, plan: Plan): Hours =
        when (date.dayOfWeek) {
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> plan.weekend
            else -> plan.weekday
        }

    // Fixed mode rings at start, start + interval, start + 2 x interval, and so on.
    // With a 60-minute interval and a start on the hour, that is every hour on the clock.
    private fun nextFixed(base: ZonedDateTime, plan: Plan): ZonedDateTime {
        for (day in 0L..7L) {
            val date = base.toLocalDate().plusDays(day)
            val end = windowEnd(date, base, plan)
            var t = windowStart(date, base, plan)
            while (!t.isAfter(end)) {
                if (t.isAfter(base)) return t
                t = t.plusMinutes(plan.intervalMin.toLong())
            }
        }
        error("Every day has waking hours, so a bell is always found within a week.")
    }

    // Random mode picks a gap of 75% to 125% of the interval. When that gap
    // lands outside waking hours, the bell moves to early in the next window.
    private fun nextRandom(base: ZonedDateTime, plan: Plan, rnd: Random): ZonedDateTime {
        val gapSec = (plan.intervalMin * 60 * rnd.nextDouble(RANDOM_LOW, RANDOM_HIGH)).toLong()
        val candidate = base.plusSeconds(gapSec)
        val date = candidate.toLocalDate()
        if (!candidate.isBefore(windowStart(date, base, plan)) &&
            !candidate.isAfter(windowEnd(date, base, plan))
        ) {
            return candidate
        }
        for (day in 0L..7L) {
            val d = base.toLocalDate().plusDays(day)
            val start = windowStart(d, base, plan)
            if (!start.isAfter(base)) continue
            val length = Duration.between(start, windowEnd(d, base, plan)).seconds
            val spread = minOf(plan.intervalMin * 30L, length)
            return start.plusSeconds(rnd.nextLong(0, spread + 1))
        }
        error("Every day has waking hours, so a bell is always found within a week.")
    }

    private fun windowStart(date: LocalDate, zoneOf: ZonedDateTime, plan: Plan): ZonedDateTime =
        at(date, hoursFor(date, plan).startMin, zoneOf)

    private fun windowEnd(date: LocalDate, zoneOf: ZonedDateTime, plan: Plan): ZonedDateTime =
        at(date, hoursFor(date, plan).endMin, zoneOf)

    private fun at(date: LocalDate, minute: Int, zoneOf: ZonedDateTime): ZonedDateTime =
        date.atTime(LocalTime.of(minute / 60, minute % 60)).atZone(zoneOf.zone)
}
