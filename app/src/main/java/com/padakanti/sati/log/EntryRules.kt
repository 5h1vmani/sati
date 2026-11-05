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

    /** A done entry always has value 1. */
    const val DONE_VALUE = 1

    /** The first problem with an entry of a practice measured as [measure], or null when it can be saved. */
    fun problem(measure: Measure, value: Int?, focus: Int?, note: String?, day: Long, today: Long): EntryProblem? = when {
        day > today -> EntryProblem.FutureDay
        measure != Measure.Done && (value == null || value !in 1..MAX_VALUE) -> EntryProblem.BadValue
        measure == Measure.Done && value != DONE_VALUE -> EntryProblem.BadValue
        focus != null && focus !in Focus.MIN..Focus.MAX -> EntryProblem.BadFocus
        note != null && note.length > MAX_NOTE -> EntryProblem.NoteTooLong
        note != null && TextRules.hasHidden(note, allowNewlines = true) -> EntryProblem.NoteBadChars
        else -> null
    }
}
