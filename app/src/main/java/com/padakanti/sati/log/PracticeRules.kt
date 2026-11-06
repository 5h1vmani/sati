package com.padakanti.sati.log

import com.padakanti.sati.TextRules

/** What is wrong with a practice. The screen turns each one into a sentence. */
enum class PracticeProblem {
    NameBlank, NameHidden, LimitReached, NameTooLong, NameTaken, TargetInvalid, MeasureLocked, NoPractice, ScheduleInvalid,
}

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

    /** A practice repeats every 1 to 7 days. */
    const val MIN_EVERY_DAYS = 1
    const val MAX_EVERY_DAYS = 7

    /** Only durations and cycles have a target. */
    fun hasTarget(measure: Measure): Boolean = measure == Measure.Duration || measure == Measure.Count

    /** The focus switch of a new practice: on for durations and cycles, off for the others. */
    fun defaultAskFocus(measure: Measure): Boolean = measure == Measure.Duration || measure == Measure.Count

    /** A done, percent or scale practice has no target. */
    fun targetFor(measure: Measure, target: Int?): Int? = if (hasTarget(measure)) target else null

    /**
     * True when a practice that repeats every [everyDays] days from [start] is due on [day].
     * It is never due before [start]. A due day that is missed does not move the schedule.
     */
    fun isDue(everyDays: Int, start: Long, day: Long): Boolean = day >= start && (day - start) % everyDays == 0L

    /** The first due day on or after [from]. It is [from] itself when that day is due. */
    fun nextDue(everyDays: Int, start: Long, from: Long): Long =
        if (from <= start) start else start + Math.floorDiv(from - start + everyDays - 1, everyDays.toLong()) * everyDays

    fun isDue(practice: Practice, day: Long): Boolean = isDue(practice.everyDays, practice.scheduleStart, day)

    fun nextDue(practice: Practice, from: Long): Long = nextDue(practice.everyDays, practice.scheduleStart, from)

    /**
     * Moves the practice with [id] to place [index] in [ordered], which is in position order.
     * Returns the practices whose position changed, with the new positions, or an empty list when
     * nothing moves. Positions become 0, 1, 2 and so on, so gaps or equal positions from earlier
     * edits cannot stop a move.
     */
    fun moveTo(ordered: List<Practice>, id: String, index: Int): List<Practice> {
        val from = ordered.indexOfFirst { it.id == id }
        if (from < 0 || index !in ordered.indices) return emptyList()
        val order = ordered.toMutableList()
        order.add(index, order.removeAt(from))
        return order.mapIndexedNotNull { i, p -> if (p.position != i) p.copy(position = i) else null }
    }

    /** Moves the practice with [id] one place up or down. Nothing changes at the end it would move past. */
    fun moved(ordered: List<Practice>, id: String, up: Boolean): List<Practice> {
        val from = ordered.indexOfFirst { it.id == id }
        return moveTo(ordered, id, if (up) from - 1 else from + 1)
    }

    /** The largest target a practice measured as [measure] may have. */
    fun maxTarget(measure: Measure): Int = if (measure == Measure.Duration) EntryRules.MAX_SECONDS else EntryRules.MAX_VALUE

    /** The first problem with a practice, or null when it can be saved. [otherNames] are the other practices' names. */
    fun problem(name: String, measure: Measure, target: Int?, otherNames: List<String>, everyDays: Int = 1): PracticeProblem? = when {
        name.isBlank() -> PracticeProblem.NameBlank
        TextRules.hasHidden(name.trim()) -> PracticeProblem.NameHidden
        clean(name).length > MAX_NAME -> PracticeProblem.NameTooLong
        otherNames.any { sameName(it, name) } -> PracticeProblem.NameTaken
        everyDays !in MIN_EVERY_DAYS..MAX_EVERY_DAYS -> PracticeProblem.ScheduleInvalid
        targetFor(measure, target)?.let { it !in 1..maxTarget(measure) } == true -> PracticeProblem.TargetInvalid
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
