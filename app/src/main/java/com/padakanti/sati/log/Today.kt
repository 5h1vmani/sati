package com.padakanti.sati.log

/**
 * One line on Today. [total] adds up the day's values, which is the amount for
 * durations and cycles. [logged] is true after any entry. [done] is true when
 * the practice was done: an entry with a value above 0. So 80 of a target of 108
 * is done and still shows as 80 of 108. [notDone] is a logged practice with only
 * not-done entries, which have value 0.
 * [rating] is the day's value of a percent or scale practice: the average of its
 * entries, rounded half up, never the sum. A not-done entry counts as 0. It is null
 * for the other measures and when no entry is done. [focusAverage] is the average of the entries
 * that have a score, or null. It is also null when the practice does not ask for focus.
 * [due] is true when the practice is due on the day. [nextDue] is its first due
 * day on or after the day. [latest] is the newest entry of the day, or null.
 */
data class TodayRow(
    val practice: Practice,
    val total: Int,
    val target: Int?,
    val logged: Boolean,
    val done: Boolean,
    val rating: Int?,
    val focusAverage: Double?,
    val due: Boolean,
    val nextDue: Long,
    val latest: Entry?,
) {
    val notDone: Boolean get() = logged && !done
}

object Today {
    /** The average of [values], rounded half up. [values] must not be empty and must not hold negative numbers. */
    fun averageRoundedHalfUp(values: List<Int>): Int = Math.floorDiv(2L * values.sumOf { it.toLong() } + values.size, 2L * values.size).toInt()

    /**
     * One row for each active practice. Practices due on [day] come first, in the
     * order given, and the others follow in the same order. [entries] may hold other days; they are ignored.
     */
    fun rows(practices: List<Practice>, entries: List<Entry>, day: Long): List<TodayRow> {
        val byPractice = entries.filter { it.day == day }.groupBy { it.practiceId }
        val rows = practices.filterNot { it.archived }.map { p ->
            val mine = byPractice[p.id].orEmpty()
            val kept = mine.filter { it.value > EntryRules.NOT_DONE_VALUE }
            val scores = mine.mapNotNull { it.focus }
            val isRating = p.measure == Measure.Percent || p.measure == Measure.Scale
            TodayRow(
                practice = p,
                total = mine.sumOf { it.value },
                target = PracticeRules.targetFor(p.measure, p.target),
                logged = mine.isNotEmpty(),
                done = kept.isNotEmpty(),
                // A not-done entry counts as 0 in the average. A day with only not-done entries has no rating.
                rating = if (isRating && kept.isNotEmpty()) averageRoundedHalfUp(mine.map { it.value }) else null,
                focusAverage = if (scores.isEmpty() || !p.askFocus) null else scores.average(),
                due = PracticeRules.isDue(p, day),
                nextDue = PracticeRules.nextDue(p, day),
                latest = mine.maxByOrNull { it.createdAt },
            )
        }
        return rows.filter { it.due } + rows.filterNot { it.due }
    }
}
