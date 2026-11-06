package com.padakanti.sati.log

/**
 * One line on Today. [total] adds up the day's values, which is the amount for
 * minutes and cycles. [logged] is true after any entry. [done] is true when the
 * practice was done: any entry, except that a done practice needs an entry of
 * value 1. So 80 of a target of 108 is done and still shows as 80 of 108, and a
 * done practice with only a not-done mark is not done. [notDone] is a logged
 * done practice that was marked not done.
 * [rating] is the day's value of a percent or scale practice: the average of its
 * entries, rounded half up, never the sum. It is null for the other measures
 * and when nothing is logged. [focusAverage] is the average of the entries that
 * have a score, or null. It is also null when the practice does not ask for focus.
 */
data class TodayRow(
    val practice: Practice,
    val total: Int,
    val target: Int?,
    val logged: Boolean,
    val done: Boolean,
    val rating: Int?,
    val focusAverage: Double?,
) {
    val notDone: Boolean get() = logged && !done
}

object Today {
    /** The average of [values], rounded half up. [values] must not be empty and must not hold negative numbers. */
    fun averageRoundedHalfUp(values: List<Int>): Int = Math.floorDiv(2L * values.sumOf { it.toLong() } + values.size, 2L * values.size).toInt()

    /** One row for each active practice, in the order given. [entries] may hold other days; they are ignored. */
    fun rows(practices: List<Practice>, entries: List<Entry>, day: Long): List<TodayRow> {
        val byPractice = entries.filter { it.day == day }.groupBy { it.practiceId }
        return practices.filterNot { it.archived }.map { p ->
            val mine = byPractice[p.id].orEmpty()
            val scores = mine.mapNotNull { it.focus }
            val isRating = p.measure == Measure.Percent || p.measure == Measure.Scale
            TodayRow(
                practice = p,
                total = mine.sumOf { it.value },
                target = PracticeRules.targetFor(p.measure, p.target),
                logged = mine.isNotEmpty(),
                done = if (p.measure == Measure.Done) mine.any { it.value == EntryRules.DONE_VALUE } else mine.isNotEmpty(),
                rating = if (isRating && mine.isNotEmpty()) averageRoundedHalfUp(mine.map { it.value }) else null,
                focusAverage = if (scores.isEmpty() || !p.askFocus) null else scores.average(),
            )
        }
    }
}
