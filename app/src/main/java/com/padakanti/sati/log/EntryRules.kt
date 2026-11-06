package com.padakanti.sati.log

import com.padakanti.sati.TextRules

/** What is wrong with an entry. The screen turns each one into a sentence. */
enum class EntryProblem { BadValue, BadFocus, NoteTooLong, NoteBadChars, FutureDay, NoPractice }

/** Focus is a score from 1 to 10. Paper notebooks used percent. */
object Focus {
    const val MIN = 1
    const val MAX = 10

    /** A paper percent as a score: divide by 10, round half up, keep it from 1 to 10. */
    fun fromPercent(percent: Int): Int = Math.floorDiv(percent + 5, 10).coerceIn(MIN, MAX)
}

/** What the entry form shows in the value field before the user types. */
object Prefill {
    /** The target, else the last logged value, else null for an empty field. */
    fun value(target: Int?, lastValue: Int?): Int? = target ?: lastValue
}

object EntryRules {
    const val MAX_VALUE = 99_999
    const val MAX_NOTE = 1_000

    /** The most seconds a Duration entry or target may have: one day. */
    const val MAX_SECONDS = 86_400

    /** An entry has value 0 when it is marked not done, for every measure. A done entry has value 1. */
    const val NOT_DONE_VALUE = 0
    const val DONE_VALUE = 1

    /** A percent entry is from 1 to 100, and a scale entry is a score from 1 to 10. */
    const val PERCENT_MAX = 100
    const val SCALE_MIN = 1
    const val SCALE_MAX = 10

    /** The values an entry of a practice measured as [measure] may have. 0 means not done. */
    fun valueRange(measure: Measure): IntRange = NOT_DONE_VALUE..maxValue(measure)

    /** The most an entry of a practice measured as [measure] may have. */
    fun maxValue(measure: Measure): Int = when (measure) {
        Measure.Duration -> MAX_SECONDS
        Measure.Count -> MAX_VALUE
        Measure.Done -> DONE_VALUE
        Measure.Percent -> PERCENT_MAX
        Measure.Scale -> SCALE_MAX
    }

    /**
     * Checks the value and the focus of an entry against its [measure]. Returns
     * BadValue, BadFocus, or null when both are fine. The entry form and the
     * import both use it, so they accept the same entries. A not-done entry
     * has no focus.
     */
    fun valueProblem(measure: Measure, value: Int?, focus: Int?): EntryProblem? = when {
        value == null || value !in valueRange(measure) -> EntryProblem.BadValue
        focus != null && focus !in Focus.MIN..Focus.MAX -> EntryProblem.BadFocus
        focus != null && value == NOT_DONE_VALUE -> EntryProblem.BadFocus
        else -> null
    }

    /** The first problem with an entry of a practice measured as [measure], or null when it can be saved. */
    fun problem(measure: Measure, value: Int?, focus: Int?, note: String?, day: Long, today: Long): EntryProblem? = when {
        day > today -> EntryProblem.FutureDay
        else -> valueProblem(measure, value, focus)
            ?: when {
                note != null && note.length > MAX_NOTE -> EntryProblem.NoteTooLong
                note != null && TextRules.hasHidden(note, allowNewlines = true) -> EntryProblem.NoteBadChars
                else -> null
            }
    }
}
