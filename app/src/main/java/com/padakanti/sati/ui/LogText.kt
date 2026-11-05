package com.padakanti.sati.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.padakanti.sati.R
import com.padakanti.sati.log.EntryProblem
import com.padakanti.sati.log.EntryRules
import com.padakanti.sati.log.IdRules
import com.padakanti.sati.log.ImportError
import com.padakanti.sati.log.ImportErrorKind
import com.padakanti.sati.log.LogDay
import com.padakanti.sati.log.LogZip
import com.padakanti.sati.log.Measure
import com.padakanti.sati.log.PracticeProblem
import com.padakanti.sati.log.PracticeRules
import com.padakanti.sati.log.TodayRow
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The words for the pure results of the log. The pure code returns data; these turn it into sentences. */

@Composable
internal fun measureName(measure: Measure): String = stringResource(
    when (measure) {
        Measure.Minutes -> R.string.log_measure_minutes
        Measure.Count -> R.string.log_measure_count
        Measure.Done -> R.string.log_measure_done
    },
)

/** An amount such as "20 minutes", "108 cycles" or "Done". */
@Composable
internal fun amountText(measure: Measure, value: Int): String = when (measure) {
    Measure.Minutes -> pluralStringResource(R.plurals.log_minutes, value, value)
    Measure.Count -> pluralStringResource(R.plurals.log_cycles, value, value)
    Measure.Done -> stringResource(R.string.log_done)
}

/** The progress of one Today row, such as "80 of 108 cycles". */
@Composable
internal fun progressText(row: TodayRow): String {
    val target = row.target
    return when {
        row.practice.measure == Measure.Done -> stringResource(if (row.done) R.string.log_done else R.string.log_not_done_yet)
        target != null && row.practice.measure == Measure.Minutes ->
            pluralStringResource(R.plurals.log_minutes_of_target, target, row.total, target)
        target != null -> pluralStringResource(R.plurals.log_cycles_of_target, target, row.total, target)
        !row.done -> stringResource(R.string.log_not_done_yet)
        else -> amountText(row.practice.measure, row.total)
    }
}

@Composable
internal fun dayLabel(day: Long, today: Long): String = when (day) {
    today -> stringResource(R.string.log_today)
    today - 1 -> stringResource(R.string.log_yesterday)
    else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(LogDay.date(day))
}

@Composable
internal fun entryProblemText(problem: EntryProblem): String = when (problem) {
    EntryProblem.BadValue -> stringResource(R.string.log_problem_value, EntryRules.MAX_VALUE)
    EntryProblem.BadFocus -> stringResource(R.string.log_problem_focus)
    EntryProblem.NoteTooLong -> pluralStringResource(R.plurals.log_problem_note, EntryRules.MAX_NOTE, EntryRules.MAX_NOTE)
    EntryProblem.NoteBadChars -> stringResource(R.string.log_problem_note_chars)
    EntryProblem.FutureDay -> stringResource(R.string.log_problem_future)
    EntryProblem.NoPractice -> stringResource(R.string.log_problem_no_practice)
}

@Composable
internal fun practiceProblemText(problem: PracticeProblem): String = when (problem) {
    PracticeProblem.NameBlank -> stringResource(R.string.log_problem_name_blank)
    PracticeProblem.LimitReached -> pluralStringResource(R.plurals.log_practice_limit, PracticeRules.MAX_PRACTICES, PracticeRules.MAX_PRACTICES)
    PracticeProblem.NameHidden -> stringResource(R.string.log_problem_name_hidden)
    PracticeProblem.NameTooLong -> pluralStringResource(R.plurals.log_problem_name_long, PracticeRules.MAX_NAME, PracticeRules.MAX_NAME)
    PracticeProblem.NameTaken -> stringResource(R.string.log_problem_name_taken)
    PracticeProblem.TargetInvalid -> stringResource(R.string.log_problem_target, EntryRules.MAX_VALUE)
    PracticeProblem.MeasureLocked -> stringResource(R.string.log_measure_locked)
    PracticeProblem.NoPractice -> stringResource(R.string.log_problem_no_practice)
}

/** The whole error of a failed import: that nothing changed, where, and why. */
@Composable
internal fun importErrorText(e: ImportError): String {
    val reason = when (e.kind) {
        ImportErrorKind.BadId -> pluralStringResource(R.plurals.log_error_id, IdRules.MAX_LENGTH, IdRules.MAX_LENGTH)
        ImportErrorKind.PracticeLimit -> pluralStringResource(R.plurals.log_error_practice_limit, e.limit, e.limit)
        else -> stringResource(
        when (e.kind) {
            ImportErrorKind.Unreadable -> R.string.log_error_unreadable
            ImportErrorKind.TooLarge -> R.string.log_error_too_large
            ImportErrorKind.Empty -> R.string.log_error_empty
            ImportErrorKind.MissingFile -> R.string.log_error_missing_file
            ImportErrorKind.QuoteProblem -> R.string.log_error_quote
            ImportErrorKind.WrongColumns -> R.string.log_error_columns
            ImportErrorKind.BadId, ImportErrorKind.PracticeLimit -> R.string.log_error_unreadable
            ImportErrorKind.BadSadhanas -> R.string.log_error_sadhanas
            ImportErrorKind.BadName -> R.string.log_error_name
            ImportErrorKind.BadMeasure -> R.string.log_error_measure
            ImportErrorKind.BadTarget -> R.string.log_error_target
            ImportErrorKind.BadFlag -> R.string.log_error_flag
            ImportErrorKind.BadDate -> R.string.log_error_date
            ImportErrorKind.FutureDay -> R.string.log_error_future
            ImportErrorKind.BadValue -> R.string.log_error_value
            ImportErrorKind.BadFocus -> R.string.log_error_focus
            ImportErrorKind.BadPeriods -> R.string.log_error_periods
            ImportErrorKind.BadNumber -> R.string.log_error_number
            ImportErrorKind.UnknownPractice -> R.string.log_error_practice
            ImportErrorKind.MeasureConflict -> R.string.log_error_conflict
            ImportErrorKind.TooMany -> R.string.log_error_too_many
            ImportErrorKind.BadText -> R.string.log_error_text
        },
        // Only the messages with a number in them use these.
        when (e.kind) {
            ImportErrorKind.TooLarge -> LogZip.MAX_BYTES / (1024 * 1024)
            ImportErrorKind.TooMany -> e.limit
            else -> EntryRules.MAX_VALUE
        },
        )
    }
    val where = when {
        e.file.isNotEmpty() && e.line > 0 -> stringResource(R.string.log_where_file_line, e.line, e.file)
        e.file.isNotEmpty() -> stringResource(R.string.log_where_file, e.file)
        e.line > 0 -> stringResource(R.string.log_where_line, e.line)
        else -> null
    }
    return if (where == null) stringResource(R.string.log_import_error_plain, reason)
    else stringResource(R.string.log_import_error, where, reason)
}
