package com.padakanti.sati.log

import com.padakanti.sati.TextRules

/** What is wrong with a practice. The screen turns each one into a sentence. */
enum class PracticeProblem { NameBlank, NameHidden, LimitReached, NameTooLong, NameTaken, TargetInvalid, MeasureLocked, NoPractice }

object PracticeRules {
    const val MAX_NAME = 60

    /** The most practices the app lets the user keep, and the most an import may leave. */
    const val MAX_PRACTICES = 200

    /** The name as it is saved: trimmed, in NFC form. */
    fun clean(name: String): String = TextRules.nfc(name.trim())

    /** Names match with case ignored and in NFC form, so an import can find a practice by its name. */
    fun sameName(a: String, b: String): Boolean = clean(a).equals(clean(b), ignoreCase = true)

    /** The measure of a practice with [entryCount] entries cannot change, because the entries would no longer match. */
    fun measureProblem(old: Measure, new: Measure, entryCount: Int): PracticeProblem? =
        if (new != old && entryCount > 0) PracticeProblem.MeasureLocked else null

    /** A done practice has no target. */
    fun targetFor(measure: Measure, target: Int?): Int? = if (measure == Measure.Done) null else target

    /** The first problem with a practice, or null when it can be saved. [otherNames] are the other practices' names. */
    fun problem(name: String, measure: Measure, target: Int?, otherNames: List<String>): PracticeProblem? = when {
        name.isBlank() -> PracticeProblem.NameBlank
        TextRules.hasHidden(name.trim()) -> PracticeProblem.NameHidden
        clean(name).length > MAX_NAME -> PracticeProblem.NameTooLong
        otherNames.any { sameName(it, name) } -> PracticeProblem.NameTaken
        targetFor(measure, target)?.let { it !in 1..EntryRules.MAX_VALUE } == true -> PracticeProblem.TargetInvalid
        else -> null
    }
}

/** What archiving or returning a practice writes: the practice, a period to open, a period to close. */
data class ArchiveChange(val practice: Practice, val open: ArchivePeriod?, val close: ArchivePeriod?)

object ArchiveRules {
    /**
     * Archives or returns [practice] on [day]. An archive opens a period that
     * starts on [day]. A return closes the [openPeriod] with [day] as the day
     * it came back, so that day counts as active. Archiving again later opens
     * a new period. Null when nothing changes.
     */
    fun change(practice: Practice, openPeriod: ArchivePeriod?, archive: Boolean, day: Long): ArchiveChange? {
        if (practice.archived == archive) return null
        return if (archive) {
            // A period that is still open, which should not happen, is kept instead of opening a second one.
            ArchiveChange(practice.copy(archived = true), if (openPeriod == null) ArchivePeriod(practiceId = practice.id, fromDay = day, toDay = null) else null, null)
        } else {
            ArchiveChange(practice.copy(archived = false), null, openPeriod?.copy(toDay = day))
        }
    }
}
