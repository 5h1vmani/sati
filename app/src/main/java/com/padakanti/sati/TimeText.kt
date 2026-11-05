package com.padakanti.sati

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Words for times and lengths, shared by the screen and the notification. Pure, so it is unit tested. */
object TimeText {
    /** "15 min", "1 h", "1 h 30". Fits on a chip. */
    fun shortInterval(min: Int) = when {
        min < 60 -> "$min min"
        min % 60 == 0 -> "${min / 60} h"
        else -> "${min / 60} h ${min % 60}"
    }

    /** "15 minutes", "hour", "2 hours", "1 h 30 min". Follows "every". */
    fun longInterval(min: Int) = when {
        min < 60 -> "$min minutes"
        min == 60 -> "hour"
        min % 60 == 0 -> "${min / 60} hours"
        else -> "${min / 60} h ${min % 60} min"
    }

    /** "30 min", "1 h", "1 h 30 min". A length of time on its own. */
    fun length(min: Int) = when {
        min < 60 -> "$min min"
        min % 60 == 0 -> "${min / 60} h"
        else -> "${min / 60} h ${min % 60} min"
    }

    fun rhythmLine(random: Boolean, min: Int) =
        if (random) "Random, about every ${longInterval(min)}" else "Every ${longInterval(min)}, on the clock"

    fun rhythmHint(random: Boolean, min: Int): String =
        if (random) {
            val low = (min * Scheduler.RANDOM_LOW).toInt()
            val high = (min * Scheduler.RANDOM_HIGH).toInt()
            "Bells come at unexpected times, $low to $high minutes apart. The mind does not learn to tune them out."
        } else {
            "Bells come every ${longInterval(min)}, counted from the start of your waking hours."
        }

    /** "in 23 min", "in 1 h 5 min", "tomorrow", or "on Saturday". */
    fun relative(next: Long, now: Long, zone: ZoneId, locale: Locale): String {
        if (next <= 0) return ""
        val days = ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
            Instant.ofEpochMilli(next).atZone(zone).toLocalDate(),
        )
        val mins = ((next - now + 59_999) / 60_000).coerceAtLeast(0)
        return when {
            days == 0L && next - now < 60_000 -> "in under a minute"
            days == 0L && mins < 60 -> "in $mins min"
            days == 0L && mins % 60 == 0L -> "in ${mins / 60} h"
            days == 0L -> "in ${mins / 60} h ${mins % 60} min"
            days == 1L -> "tomorrow"
            else -> "on " + Instant.ofEpochMilli(next).atZone(zone).dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        }
    }

    /** A time of day from minutes after midnight, in the phone's 12- or 24-hour style. */
    fun clock(minute: Int, is24Hour: Boolean, locale: Locale): String {
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        return LocalTime.of(minute / 60, minute % 60).format(DateTimeFormatter.ofPattern(pattern, locale))
    }

    /** "4:05" or "1:02:05". Rounded up, so a countdown shows 0:00 only at the end. */
    fun countdown(ms: Long): String {
        val total = ((ms.coerceAtLeast(0) + 999) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** "20 min" for one stage, "30 + 30 min" for several. */
    fun stages(stages: List<Stage>): String =
        if (stages.size == 1) length(stages[0].minutes) else stages.joinToString(" + ") { "${it.minutes}" } + " min"

    /** "30 seconds", "1 minute", "2 minutes". */
    fun settle(seconds: Int) = when {
        seconds < 60 -> "$seconds seconds"
        seconds == 60 -> "1 minute"
        else -> "${seconds / 60} minutes"
    }

    /**
     * Where a sadhana stands, in a few words: "Settling in", "Stage 1 of 2 · Breathing",
     * or for a single stage its label or length.
     */
    fun stage(s: Sadhana, p: Position): String {
        if (p.settling) return "Settling in"
        val label = s.stages[p.stage].label.trim()
        if (s.stages.size == 1) return label.ifEmpty { length(s.totalMin) }
        val count = "Stage ${p.stage + 1} of ${s.stages.size}"
        return if (label.isEmpty()) count else "$count · $label"
    }
}
