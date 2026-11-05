package com.padakanti.sati.log

/**
 * One line on Today. [total] adds up the day's entries. [done] is true after
 * any entry, so 80 of a target of 108 is done and still shows as 80 of 108.
 * [focusAverage] is the average of the entries that have a score, or null.
 */
data class TodayRow(
    val practice: Practice,
    val total: Int,
    val target: Int?,
    val done: Boolean,
    val focusAverage: Double?,
)

object Today {
    /** One row for each active practice, in the order given. [entries] may hold other days; they are ignored. */
    fun rows(practices: List<Practice>, entries: List<Entry>, day: Long): List<TodayRow> {
        val byPractice = entries.filter { it.day == day }.groupBy { it.practiceId }
        return practices.filterNot { it.archived }.map { p ->
            val mine = byPractice[p.id].orEmpty()
            val scores = mine.mapNotNull { it.focus }
            TodayRow(
                practice = p,
                total = mine.sumOf { it.value },
                target = p.target,
                done = mine.isNotEmpty(),
                focusAverage = if (scores.isEmpty()) null else scores.average(),
            )
        }
    }
}
