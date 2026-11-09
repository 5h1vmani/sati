package com.padakanti.sati.log

import java.time.DayOfWeek
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * How one day of one practice looks in the analytics.
 * Inactive is a day before the practice started, a day while it was archived, or a future day.
 * NotDue is an active day that was not due and has nothing done.
 * Done is a day with an entry above 0, due or not.
 * Missed is a due day before today with nothing done.
 * Open is today, due, with nothing done yet. Today never counts as missed.
 */
enum class DayMark { Inactive, NotDue, Done, Missed, Open }

/** Done due days out of counted due days. A due day counts when it is before today, or it is today and done. */
data class Tally(val done: Int, val due: Int) {
    /** The share of due days done, from 0 to 1, or null when nothing was due. */
    val share: Double? get() = if (due == 0) null else done.toDouble() / due

    operator fun plus(other: Tally) = Tally(done + other.done, due + other.due)

    companion object {
        val ZERO = Tally(0, 0)
    }
}

/** The first entry day of a practice, read from the database for all its entries. */
data class FirstEntry(val practiceId: String, val day: Long)

/**
 * One practice with what the analytics need: when it started, its archive periods,
 * and its entries. [entries] may cover only part of the history. Days outside them
 * look like days with nothing logged, so the caller loads the days it asks about.
 */
class Track(val practice: Practice, firstEntryDay: Long?, periods: List<ArchivePeriod>, entries: List<Entry>) {
    /** The first active day: the earlier of the creation day and the first entry. */
    val start: Long = minOf(practice.createdDay, firstEntryDay ?: practice.createdDay)

    private val periods = periods.filter { it.practiceId == practice.id }
    val entries: List<Entry> = entries.filter { it.practiceId == practice.id }
    private val doneDays: Set<Long> = this.entries.filter { it.value > EntryRules.NOT_DONE_VALUE }.mapTo(HashSet()) { it.day }

    /** An archive period covers its first day. The day the practice came back is active again. */
    fun active(day: Long): Boolean =
        day >= start && periods.none { day >= it.fromDay && (it.toDay == null || day < it.toDay) }

    fun due(day: Long): Boolean = active(day) && PracticeRules.isDue(practice, day)

    fun done(day: Long): Boolean = day in doneDays

    fun mark(day: Long, today: Long): DayMark = when {
        day > today || !active(day) -> DayMark.Inactive
        done(day) -> DayMark.Done
        !PracticeRules.isDue(practice, day) -> DayMark.NotDue
        day == today -> DayMark.Open
        else -> DayMark.Missed
    }

    /** The tally of the days from [from] to [to], both included. Days after [today] are not counted. */
    fun tally(from: Long, to: Long, today: Long): Tally {
        var done = 0
        var due = 0
        for (day in from..minOf(to, today)) {
            if (!due(day)) continue
            if (done(day)) {
                done++
                due++
            } else if (day < today) {
                due++
            }
        }
        return Tally(done, due)
    }
}

/** The practice with the most missed due days in a stretch of days. */
data class MostMissed(val practice: Practice, val missed: Int, val due: Int)

/** The 7 days that end with today. [allDone] days had every due practice done, out of [days] days that had something due. */
data class WeekSummary(val allDone: Int, val days: Int, val mostMissed: MostMissed?)

/** Which way a practice changed in the last 4 weeks, against the 8 weeks before. */
data class Change(val practice: Practice, val rose: Boolean)

/** The 12 weeks. [steady] practices out of [counted] practices that had a due day. */
data class TrendSummary(val steady: Int, val counted: Int, val change: Change?)

object Analytics {
    /** The dots on a Today row and the week sentences cover this many days, today included. */
    const val WEEK_DAYS = 7

    /** The 12-week view has this many weeks, the current one included. */
    const val TREND_WEEKS = 12

    /** The weeks compared with the earlier ones in the 12-week view. */
    const val RECENT_WEEKS = 4

    /** The bars of a practice's amount cover this many weeks. */
    const val AMOUNT_WEEKS = 8

    /** A practice is steady when it was done on at least this share of its due days. */
    const val STEADY_SHARE = 0.8

    /** The smallest change of share, in the last 4 weeks against the 8 before, that gets a sentence. */
    const val CHANGE_SHARE = 0.2

    /** Each part of the comparison needs this many counted due days. */
    const val CHANGE_MIN_DUE = 7

    /** The weekday sentence needs this many active days in the 12 weeks. */
    const val WEEKDAY_MIN_ACTIVE = 28

    /** The weakest weekday must be this far below the practice's average share. */
    const val WEEKDAY_GAP = 0.15

    /** A practice is named as missed most only when it missed at least this many days. */
    const val MOST_MISSED_MIN = 2

    /** The 30-day count on a practice's screen. */
    const val LAST_DAYS = 30

    /** The first day of the [WEEK_DAYS] days that end with [today]. */
    fun weekFrom(today: Long): Long = today - WEEK_DAYS + 1

    /** The marks of the [WEEK_DAYS] days that end with [today], oldest first. */
    fun weekMarks(track: Track, today: Long): List<DayMark> = (weekFrom(today)..today).map { track.mark(it, today) }

    /**
     * The 7 days that end with [today]. A day counts when any practice was due on it. It is all done when every
     * due practice was done. Today counts only once it is all done, because today is never missed.
     * [tracks] include archived practices, because they were active on some of the days.
     * Only practices that are not archived now can be named as missed most. A tie goes to the earlier position.
     */
    fun week(tracks: List<Track>, today: Long): WeekSummary {
        var allDone = 0
        var days = 0
        for (day in weekFrom(today)..today) {
            val due = tracks.filter { it.due(day) }
            if (due.isEmpty()) continue
            val done = due.all { it.done(day) }
            if (day == today && !done) continue
            days++
            if (done) allDone++
        }
        val most = tracks
            .filterNot { it.practice.archived }
            .map { it to it.tally(weekFrom(today), today, today) }
            .filter { (_, t) -> t.due - t.done >= MOST_MISSED_MIN }
            .sortedWith(compareByDescending<Pair<Track, Tally>> { (_, t) -> t.due - t.done }.thenBy { (track, _) -> track.practice.position })
            .firstOrNull()
            ?.let { (track, t) -> MostMissed(track.practice, t.due - t.done, t.due) }
        return WeekSummary(allDone, days, most)
    }

    /** The first day of the week that holds [day]. */
    fun weekStart(day: Long, firstDay: DayOfWeek): Long =
        LogDay.date(day).with(TemporalAdjusters.previousOrSame(firstDay)).toEpochDay()

    /** The first days of [count] weeks, oldest first. The last one is the week that holds [today]. */
    fun weekStarts(today: Long, firstDay: DayOfWeek, count: Int): List<Long> {
        val current = weekStart(today, firstDay)
        return (count - 1 downTo 0).map { current - it * 7L }
    }

    /** The tally of each week that starts on a day in [starts]. */
    fun weekTallies(track: Track, starts: List<Long>, today: Long): List<Tally> =
        starts.map { track.tally(it, it + 6, today) }

    /**
     * How full a week's square is. Null when nothing was due. 0 when nothing was done, 1 for less than half,
     * 2 for half or more, 3 for every due day.
     */
    fun level(tally: Tally): Int? {
        val share = tally.share ?: return null
        return when {
            tally.done == 0 -> 0
            tally.done == tally.due -> 3
            share >= 0.5 -> 2
            else -> 1
        }
    }

    /**
     * The 12 weeks of the practices that are not archived. [starts] are the first days of the weeks, oldest first.
     * A practice counts when it had a due day. The change compares the last [RECENT_WEEKS] weeks with the weeks
     * before. The biggest fall is named first. Without a fall, the biggest rise is named. A tie goes to the earlier position.
     */
    fun trend(tracks: List<Track>, starts: List<Long>, today: Long): TrendSummary {
        val live = tracks.filterNot { it.practice.archived }.sortedBy { it.practice.position }
        val tallies = live.map { weekTallies(it, starts, today) }
        val totals = tallies.map { weeks -> weeks.fold(Tally.ZERO, Tally::plus) }
        val counted = totals.count { it.due > 0 }
        val steady = totals.count { (it.share ?: 0.0) >= STEADY_SHARE && it.due > 0 }

        val split = (starts.size - RECENT_WEEKS).coerceAtLeast(0)
        val changes = live.indices.mapNotNull { i ->
            val earlier = tallies[i].take(split).fold(Tally.ZERO, Tally::plus)
            val recent = tallies[i].drop(split).fold(Tally.ZERO, Tally::plus)
            if (earlier.due < CHANGE_MIN_DUE || recent.due < CHANGE_MIN_DUE) return@mapNotNull null
            live[i].practice to (recent.share!! - earlier.share!!)
        }
        // The small allowance keeps a change of exactly 20 points from failing on rounding.
        val fall = changes.filter { it.second <= -CHANGE_SHARE + 1e-9 }.minByOrNull { it.second }
        val rise = changes.filter { it.second >= CHANGE_SHARE - 1e-9 }.maxByOrNull { it.second }
        val change = when {
            fall != null -> Change(fall.first, rose = false)
            rise != null -> Change(rise.first, rose = true)
            else -> null
        }
        return TrendSummary(steady, counted, change)
    }

    /** The last [LAST_DAYS] days, today included. */
    fun lastDays(track: Track, today: Long): Tally = track.tally(today - LAST_DAYS + 1, today, today)

    /**
     * The weekday with the lowest share of due days done, over the days from [from] to [today].
     * Null until the practice has [WEEKDAY_MIN_ACTIVE] active days in that stretch, when two weekdays
     * share the lowest value, or when the lowest is less than [WEEKDAY_GAP] below the average.
     */
    fun weakestWeekday(track: Track, from: Long, today: Long): DayOfWeek? {
        val activeDays = (from..today).count { track.active(it) }
        if (activeDays < WEEKDAY_MIN_ACTIVE) return null
        val byWeekday = HashMap<DayOfWeek, Tally>()
        for (day in from..today) {
            val t = track.tally(day, day, today)
            if (t.due == 0) continue
            val weekday = LogDay.date(day).dayOfWeek
            byWeekday[weekday] = (byWeekday[weekday] ?: Tally.ZERO) + t
        }
        val average = byWeekday.values.fold(Tally.ZERO, Tally::plus).share ?: return null
        val shares = byWeekday.mapValues { it.value.share!! }
        val lowest = shares.values.minOrNull() ?: return null
        val atLowest = shares.filterValues { it == lowest }.keys
        if (atLowest.size != 1 || average - lowest < WEEKDAY_GAP - 1e-9) return null
        return atLowest.single()
    }

    /** The sum of entry values in each week that starts on a day in [starts]. Not-done entries add 0. */
    fun weeklyTotals(entries: List<Entry>, starts: List<Long>): List<Long> =
        starts.map { start -> entries.filter { it.day in start..start + 6 }.sumOf { it.value.toLong() } }

    /** The average focus of the scored entries in each week, or null for a week with no score. Each scored entry counts once. */
    fun weeklyFocus(entries: List<Entry>, starts: List<Long>): List<Double?> =
        starts.map { start -> focusAverage(entries, start, start + 6) }

    /** The average focus of the scored entries from [from] to [to], or null when none has a score. */
    fun focusAverage(entries: List<Entry>, from: Long, to: Long): Double? =
        entries.filter { it.day in from..to }.mapNotNull { it.focus }.takeIf { it.isNotEmpty() }?.average()

    /** The days of [month] as a grid that starts on [firstDay]. Null cells are the days of the next or previous month. */
    fun monthGrid(month: YearMonth, firstDay: DayOfWeek): List<Long?> {
        val first = month.atDay(1)
        val lead = Math.floorMod(first.dayOfWeek.value - firstDay.value, 7)
        val days = (0 until month.lengthOfMonth()).map { first.toEpochDay() + it }
        val cells = List<Long?>(lead) { null } + days
        return cells + List<Long?>(Math.floorMod(-cells.size, 7)) { null }
    }

    /** The weekdays in order from [firstDay]. */
    fun weekdays(firstDay: DayOfWeek): List<DayOfWeek> = (0L until 7L).map { firstDay.plus(it) }
}
