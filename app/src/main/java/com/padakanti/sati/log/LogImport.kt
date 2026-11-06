package com.padakanti.sati.log

import com.padakanti.sati.Sadhana
import com.padakanti.sati.SadhanaCheck
import com.padakanti.sati.SadhanaJson
import com.padakanti.sati.TextRules
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

/** Why an import failed. The screen turns each one into a sentence. */
enum class ImportErrorKind {
    Unreadable, TooLarge, Empty, MissingFile, QuoteProblem, WrongColumns, BadId, BadName, BadMeasure,
    BadTarget, BadFlag, BadDate, FutureDay, BadValue, BadFocus, BadPeriods, BadNumber, UnknownPractice,
    MeasureConflict, BadSadhanas, TooMany, BadText, PracticeLimit,
}

/**
 * A failed import. [file] is the file inside a ZIP, empty for a plain CSV.
 * [line] is 0 when no line applies. [limit] is the cap that was passed, for [ImportErrorKind.TooMany].
 */
data class ImportError(val kind: ImportErrorKind, val file: String = "", val line: Int = 0, val limit: Int = 0)

/** Either a value or the first error. */
sealed interface Parsed<out T> {
    data class Ok<T>(val value: T) : Parsed<T>
    data class Failed(val error: ImportError) : Parsed<Nothing>
}

/**
 * One practice row of an exported file. [periods] are the archive periods as days: from, and to or null.
 * [askFocus] is null when the file has no ask_focus column, so the measure's default applies.
 */
data class FilePractice(
    val line: Int,
    val id: String,
    val name: String,
    val measure: Measure,
    val target: Int?,
    val createdDay: Long,
    val archived: Boolean,
    val periods: List<Pair<Long, Long?>>,
    val askFocus: Boolean? = null,
)

/**
 * One entry row. A row with an [id] came from an export and names its practice
 * by [practiceId]. A row without one is hand-made and names it by [practiceName].
 * [focus] is already a score from 1 to 10. [file] is the file inside a ZIP, empty for a hand-made CSV.
 */
data class FileEntry(
    val line: Int,
    val id: String?,
    val practiceId: String?,
    val practiceName: String,
    val day: Long,
    val value: Int,
    val focus: Int?,
    val note: String?,
    val createdAt: Long?,
    val file: String = "",
    /** The hand-made value ended in "%". Only a percent practice allows it. */
    val percentSign: Boolean = false,
) {
    /** A row of a hand-made CSV, where any value of 1 or more means done for a done practice. */
    val handMade: Boolean get() = file.isEmpty()
}

/** An import file after reading and checking its shape. Nothing is written yet. */
data class ImportFile(
    val practices: List<FilePractice>,
    val entries: List<FileEntry>,
    val sadhanas: List<Sadhana>?,
    /** Sadhanas in the file that were left out because they are not valid. */
    val sadhanasBad: Int = 0,
)

/** What the log holds now. */
data class LogState(val practices: List<Practice>, val entries: List<Entry>)

/**
 * What an import would write. [unknownNames] are practice names the log does
 * not know; each became a new practice in [practices]. Until the user picks a
 * measure for it, that practice is a guess.
 */
data class ImportChanges(
    val practices: List<Practice>,
    val periods: List<ArchivePeriod>,
    val entries: List<Entry>,
    val entriesSkipped: Int,
    val unknownNames: List<String>,
    val sadhanas: List<Sadhana>,
    val sadhanasBad: Int = 0,
)

/** Where an import stands. */
sealed interface LogImportState {
    data object Idle : LogImportState

    /** The file is valid. These new practice names need a measure before it runs. */
    data class Asking(val file: ImportFile, val names: List<String>) : LogImportState

    data class Result(
        val added: Int,
        val skipped: Int,
        val practicesAdded: Int,
        val sadhanasAdded: Int = 0,
        val sadhanasSkipped: Int = 0,
    ) : LogImportState
    data class Failed(val error: ImportError) : LogImportState
}

/**
 * Reads, checks and merges an import. The file is checked completely before
 * anything is written, so an error changes nothing. A row that is already in
 * the log is skipped, so the same file can be imported twice.
 */
object LogImport {
    /** The first column of a hand-made file may be a header with this word. */
    private const val HAND_MADE_HEADER = "date"
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

    /** At most this many practices in a file, entries in a file, and new practice names in a hand-made file. */
    const val MAX_PRACTICES = PracticeRules.MAX_PRACTICES
    const val MAX_ENTRIES = 100_000
    const val MAX_NEW_NAMES = 50

    private fun fail(kind: ImportErrorKind, file: String = "", line: Int = 0, limit: Int = 0) =
        Parsed.Failed(ImportError(kind, file, line, limit))

    private fun key(name: String) = PracticeRules.clean(name).lowercase(Locale.ROOT)

    /** A name from a file in NFC form, or null when it is empty, too long or has hidden characters. */
    private fun cleanName(raw: String): Parsed<String> {
        val name = PracticeRules.clean(raw)
        return when {
            name.isEmpty() || name.length > PracticeRules.MAX_NAME -> Parsed.Failed(ImportError(ImportErrorKind.BadName))
            TextRules.hasHidden(name) -> Parsed.Failed(ImportError(ImportErrorKind.BadText))
            else -> Parsed.Ok(name)
        }
    }

    /** The rows of [text]. At most [dataRows] data rows are allowed, and one more for a header row. */
    private fun records(text: String, file: String, dataRows: Int): Parsed<List<CsvRecord>> =
        when (val csv = Csv.parse(text, dataRows + 1)) {
            is CsvParse.Broken -> fail(ImportErrorKind.QuoteProblem, file, csv.line)
            is CsvParse.TooMany -> fail(ImportErrorKind.TooMany, file, csv.line, dataRows)
            is CsvParse.Ok -> Parsed.Ok(csv.records)
        }

    private fun day(text: String): Long? =
        if (DATE.matches(text)) runCatching { LocalDate.parse(text).toEpochDay() }.getOrNull() else null

    // ---------- reading ----------

    /** A hand-made CSV with four columns: date, practice, value, focus in percent. Days after [today] are refused. */
    fun parseHandMade(text: String, today: Long): Parsed<ImportFile> {
        val records = when (val r = records(text, "", MAX_ENTRIES)) {
            is Parsed.Failed -> return r
            is Parsed.Ok -> r.value
        }
        val rows = if (records.firstOrNull()?.fields?.firstOrNull()?.trim()?.lowercase(Locale.ROOT) == HAND_MADE_HEADER) {
            records.drop(1)
        } else {
            records
        }
        if (rows.isEmpty()) return fail(ImportErrorKind.Empty)
        // The row limit counted a possible header row, so a file without one needs this check too.
        if (rows.size > MAX_ENTRIES) return fail(ImportErrorKind.TooMany, "", rows[MAX_ENTRIES].line, MAX_ENTRIES)
        val entries = rows.map { r ->
            if (r.fields.size != 4) return fail(ImportErrorKind.WrongColumns, line = r.line)
            val (dateText, rawName, valueText, focusText) = r.fields.map { it.trim() }
            val d = day(dateText) ?: return fail(ImportErrorKind.BadDate, line = r.line)
            if (d > today) return fail(ImportErrorKind.FutureDay, line = r.line)
            val name = when (val n = cleanName(rawName)) {
                is Parsed.Failed -> return fail(n.error.kind, line = r.line)
                is Parsed.Ok -> n.value
            }
            // The practice and its measure are not known yet, so the value is only checked for the widest range.
            val value = valueText.removeSuffix("%").trim().toIntOrNull()?.takeIf { it in 0..EntryRules.MAX_VALUE }
                ?: return fail(ImportErrorKind.BadValue, line = r.line)
            val focus = if (focusText.isEmpty()) {
                null
            } else {
                val percent = focusText.removeSuffix("%").trim().toIntOrNull()?.takeIf { it in 0..100 }
                    ?: return fail(ImportErrorKind.BadFocus, line = r.line)
                Focus.fromPercent(percent)
            }
            FileEntry(r.line, null, null, name, d, value, focus, null, null, percentSign = valueText.endsWith("%"))
        }
        return Parsed.Ok(ImportFile(emptyList(), entries, null))
    }

    /** The files of an export ZIP. sadhanas.json may be missing; the two CSV files may not. */
    fun parseZip(zip: ZipContents, today: Long): Parsed<ImportFile> {
        val practiceText = zip.practices ?: return fail(ImportErrorKind.MissingFile, LogZip.PRACTICES)
        val entryText = zip.entries ?: return fail(ImportErrorKind.MissingFile, LogZip.ENTRIES)
        val practices = when (val p = parsePractices(practiceText)) {
            is Parsed.Failed -> return p
            is Parsed.Ok -> p.value
        }
        val entries = when (val e = parseEntries(entryText, today)) {
            is Parsed.Failed -> return e
            is Parsed.Ok -> e.value
        }
        // A sadhana that cannot be used is skipped. Only text that is not a JSON list fails the file.
        val sadhanas = zip.sadhanas?.let { SadhanaJson.decodeImport(it) ?: return fail(ImportErrorKind.BadSadhanas, LogZip.SADHANAS) }
        return Parsed.Ok(ImportFile(practices, entries, sadhanas?.sadhanas, sadhanas?.skipped ?: 0))
    }

    /**
     * The data rows of one CSV file of an export, after checking its header row.
     * The header may also be [oldHeader], which is the same without the last column.
     */
    private fun rowsOf(text: String, file: String, header: List<String>, dataRows: Int, oldHeader: List<String> = header): Parsed<List<CsvRecord>> {
        val records = when (val r = records(text, file, dataRows)) {
            is Parsed.Failed -> return r
            is Parsed.Ok -> r.value
        }
        val first = records.firstOrNull() ?: return fail(ImportErrorKind.Empty, file)
        val names = first.fields.map { it.trim().lowercase(Locale.ROOT) }
        if (names != header && names != oldHeader) return fail(ImportErrorKind.WrongColumns, file, first.line)
        records.drop(1).firstOrNull { it.fields.size != names.size }?.let {
            return fail(ImportErrorKind.WrongColumns, file, it.line)
        }
        return Parsed.Ok(records.drop(1))
    }

    private fun parsePractices(text: String): Parsed<List<FilePractice>> {
        val file = LogZip.PRACTICES
        val rows = when (val r = rowsOf(text, file, LogExport.PRACTICE_HEADER, MAX_PRACTICES, LogExport.PRACTICE_HEADER_OLD)) {
            is Parsed.Failed -> return r
            is Parsed.Ok -> r.value
        }
        val ids = mutableSetOf<String>()
        val names = mutableSetOf<String>()
        val out = rows.map { r ->
            val f = r.fields.map { it.trim() }
            val (id, rawName, measureText) = f
            val (targetText, createdText, archivedText) = f.drop(3)
            val periodsText = f[6]
            // A file from before the focus switch has no eighth column.
            val askText = f.getOrNull(7)
            if (!IdRules.valid(id) || !ids.add(id)) return fail(ImportErrorKind.BadId, file, r.line)
            val name = when (val n = cleanName(CsvGuard.unguard(rawName))) {
                is Parsed.Failed -> return fail(n.error.kind, file, r.line)
                is Parsed.Ok -> n.value
            }
            if (!names.add(key(name))) return fail(ImportErrorKind.BadName, file, r.line)
            val measure = LogExport.measureOf(measureText) ?: return fail(ImportErrorKind.BadMeasure, file, r.line)
            val target = if (targetText.isEmpty()) {
                null
            } else {
                targetText.toIntOrNull()?.takeIf { it in 1..EntryRules.MAX_VALUE } ?: return fail(ImportErrorKind.BadTarget, file, r.line)
            }
            val created = day(createdText) ?: return fail(ImportErrorKind.BadDate, file, r.line)
            val archived = when (archivedText.lowercase(Locale.ROOT)) {
                "true" -> true
                "false" -> false
                else -> return fail(ImportErrorKind.BadFlag, file, r.line)
            }
            val askFocus = when (askText?.lowercase(Locale.ROOT)) {
                null -> null
                "true" -> true
                "false" -> false
                else -> return fail(ImportErrorKind.BadFlag, file, r.line)
            }
            val periods = if (periodsText.isEmpty()) {
                emptyList()
            } else {
                periodsText.split(';').map { part ->
                    val bits = part.split('/')
                    val from = bits.getOrNull(0)?.trim()?.let(::day)
                    val toText = bits.getOrNull(1)?.trim()
                    val to = if (toText.isNullOrEmpty()) null else day(toText) ?: return fail(ImportErrorKind.BadPeriods, file, r.line)
                    if (bits.size != 2 || from == null) return fail(ImportErrorKind.BadPeriods, file, r.line)
                    from to to
                }
            }
            FilePractice(r.line, id, name, measure, PracticeRules.targetFor(measure, target), created, archived, periods, askFocus)
        }
        return Parsed.Ok(out)
    }

    private fun parseEntries(text: String, today: Long): Parsed<List<FileEntry>> {
        val file = LogZip.ENTRIES
        val rows = when (val r = rowsOf(text, file, LogExport.ENTRY_HEADER, MAX_ENTRIES)) {
            is Parsed.Failed -> return r
            is Parsed.Ok -> r.value
        }
        val out = rows.map { r ->
            val f = r.fields
            val id = f[0].trim().ifEmpty { null }
            val practiceId = f[1].trim().ifEmpty { null }
            if (id != null && !IdRules.valid(id)) return fail(ImportErrorKind.BadId, file, r.line)
            if (practiceId != null && !IdRules.valid(practiceId)) return fail(ImportErrorKind.BadId, file, r.line)
            if (id != null && practiceId == null) return fail(ImportErrorKind.UnknownPractice, file, r.line)
            // A row with an id names its practice by id, so its name column is not used.
            val name = if (id != null) "" else when (val n = cleanName(CsvGuard.unguard(f[2]))) {
                is Parsed.Failed -> return fail(n.error.kind, file, r.line)
                is Parsed.Ok -> n.value
            }
            val d = day(f[3].trim()) ?: return fail(ImportErrorKind.BadDate, file, r.line)
            if (d > today) return fail(ImportErrorKind.FutureDay, file, r.line)
            val value = f[4].trim().toIntOrNull()?.takeIf { it in 0..EntryRules.MAX_VALUE }
                ?: return fail(ImportErrorKind.BadValue, file, r.line)
            val focusText = f[5].trim()
            val focus = if (focusText.isEmpty()) {
                null
            } else {
                focusText.toIntOrNull()?.takeIf { it in Focus.MIN..Focus.MAX } ?: return fail(ImportErrorKind.BadFocus, file, r.line)
            }
            val note = CsvGuard.unguard(f[6]).ifEmpty { null }
            if (note != null && note.length > EntryRules.MAX_NOTE) return fail(ImportErrorKind.BadValue, file, r.line)
            if (note != null && TextRules.hasHidden(note, allowNewlines = true)) return fail(ImportErrorKind.BadText, file, r.line)
            val createdText = f[7].trim()
            val createdAt = if (createdText.isEmpty()) {
                null
            } else {
                createdText.toLongOrNull()?.takeIf { it >= 0 } ?: return fail(ImportErrorKind.BadNumber, file, r.line)
            }
            FileEntry(r.line, id, practiceId, name, d, value, focus, note, createdAt, file)
        }
        return Parsed.Ok(out)
    }

    // ---------- merging ----------

    /** What merging imported sadhanas into the saved ones gives: the new list, and how many were added and skipped. */
    data class SadhanaMerge(val list: List<Sadhana>, val added: Int, val skipped: Int)

    /**
     * Sadhanas merge by id. A sadhana whose id exists stays as it is. A new one
     * is added only while the list has fewer than [SadhanaCheck.MAX_SADHANAS],
     * and only if it is [SadhanaCheck.isSafe]. The rest are skipped.
     */
    fun mergeSadhanas(existing: List<Sadhana>, incoming: List<Sadhana>): SadhanaMerge {
        val have = existing.mapTo(mutableSetOf()) { it.id }
        val out = existing.toMutableList()
        var added = 0
        var skipped = 0
        for (s in incoming) {
            if (s.id in have || out.size >= SadhanaCheck.MAX_SADHANAS || !SadhanaCheck.isSafe(s)) {
                skipped++
            } else {
                out += s
                have += s.id
                added++
            }
        }
        return SadhanaMerge(out, added, skipped)
    }

    /** A practice that an unknown name will create, with the earliest day of its entries. */
    private class NewPractice(val id: String, val name: String, val measure: Measure) {
        var firstDay = Long.MAX_VALUE
        var anyFocus = false
    }

    /**
     * Works out what importing [file] into [state] would write. [measures] holds
     * the measure for each unknown practice name; a name with no measure is
     * created as Minutes, and the screen shows it to the user first through
     * [ImportChanges.unknownNames]. Every entry is checked against its
     * practice's measure, except those of a practice whose measure is still a
     * guess, which the second run checks. With [final], nothing is a guess,
     * so every entry is checked. A new practice from a hand-made file
     * asks for focus when any of its rows has one. The id of every new row
     * comes from [newId].
     */
    fun merge(
        file: ImportFile,
        state: LogState,
        measures: Map<String, Measure>,
        now: Long,
        newId: () -> String = { UUID.randomUUID().toString() },
        final: Boolean = false,
    ): Parsed<ImportChanges> {
        val chosen = measures.mapKeys { key(it.key) }
        val existingById = state.practices.associateBy { it.id }
        // Every practice the file can name, as its final id in the log.
        val idOf = mutableMapOf<String, String>()
        val idByName = state.practices.associate { key(it.name) to it.id }.toMutableMap()
        val measureById = state.practices.associate { it.id to it.measure }.toMutableMap()
        // New practices whose measure the user has not chosen yet. Their entries are checked on the second run.
        val guessed = mutableSetOf<String>()
        var position = (state.practices.maxOfOrNull { it.position } ?: -1) + 1

        val newPractices = mutableListOf<Practice>()
        val newPeriods = mutableListOf<ArchivePeriod>()
        for (fp in file.practices) {
            val sameId = existingById[fp.id]
            val sameName = idByName[key(fp.name)]
            when {
                sameId != null -> {
                    if (sameId.measure != fp.measure) {
                        return Parsed.Failed(ImportError(ImportErrorKind.MeasureConflict, LogZip.PRACTICES, fp.line))
                    }
                    idOf[fp.id] = sameId.id
                }
                sameName != null -> {
                    if (measureById[sameName] != fp.measure) {
                        return Parsed.Failed(ImportError(ImportErrorKind.MeasureConflict, LogZip.PRACTICES, fp.line))
                    }
                    idOf[fp.id] = sameName
                }
                else -> {
                    idOf[fp.id] = fp.id
                    idByName[key(fp.name)] = fp.id
                    measureById[fp.id] = fp.measure
                    newPractices += Practice(
                        fp.id, fp.name, fp.measure, fp.target, fp.createdDay, fp.archived, position++,
                        fp.askFocus ?: PracticeRules.defaultAskFocus(fp.measure),
                    )
                    fp.periods.forEach { (from, to) -> newPeriods += ArchivePeriod(practiceId = fp.id, fromDay = from, toDay = to) }
                }
            }
        }

        val unknown = linkedMapOf<String, NewPractice>()
        val knownIds = existingById.keys
        val existingEntryIds = state.entries.mapTo(mutableSetOf()) { it.id }
        val existingKeys = state.entries.mapTo(mutableSetOf()) { Triple(it.practiceId, it.day, it.value) }
        val seenIds = mutableSetOf<String>()
        val newEntries = mutableListOf<Entry>()
        var skipped = 0

        for (fe in file.entries) {
            val practiceId: String
            if (fe.id != null) {
                val wanted = fe.practiceId!!
                practiceId = idOf[wanted] ?: wanted.takeIf { it in knownIds }
                    ?: return Parsed.Failed(ImportError(ImportErrorKind.UnknownPractice, LogZip.ENTRIES, fe.line))
            } else {
                val k = key(fe.practiceName)
                practiceId = idByName[k] ?: unknown.getOrPut(k) {
                    NewPractice(newId(), PracticeRules.clean(fe.practiceName), chosen[k] ?: Measure.Minutes)
                        .also {
                            idByName[k] = it.id
                            measureById[it.id] = it.measure
                            if (k !in chosen) guessed += it.id
                        }
                }.id
                if (unknown.size > MAX_NEW_NAMES) {
                    return Parsed.Failed(ImportError(ImportErrorKind.TooMany, "", fe.line, MAX_NEW_NAMES))
                }
            }
            unknown[key(fe.practiceName)]?.takeIf { it.id == practiceId }?.let {
                it.firstDay = minOf(it.firstDay, fe.day)
                if (fe.focus != null) it.anyFocus = true
            }
            val measure = measureById.getValue(practiceId)
            // A hand-made row of a done practice says done with any value of 1 or more.
            val value = if (fe.handMade && measure == Measure.Done && fe.value >= EntryRules.DONE_VALUE) EntryRules.DONE_VALUE else fe.value
            if (final || practiceId !in guessed) {
                if (fe.percentSign && measure != Measure.Percent) {
                    return Parsed.Failed(ImportError(ImportErrorKind.BadValue, fe.file, fe.line))
                }
                when (EntryRules.valueProblem(measure, value, fe.focus)) {
                    EntryProblem.BadValue -> return Parsed.Failed(ImportError(ImportErrorKind.BadValue, fe.file, fe.line))
                    EntryProblem.BadFocus -> return Parsed.Failed(ImportError(ImportErrorKind.BadFocus, fe.file, fe.line))
                    else -> Unit
                }
            }
            if (fe.id != null) {
                if (fe.id in existingEntryIds || !seenIds.add(fe.id)) {
                    skipped++
                    continue
                }
            } else if (Triple(practiceId, fe.day, value) in existingKeys) {
                skipped++
                continue
            }
            newEntries += Entry(fe.id ?: newId(), practiceId, fe.day, value, fe.focus, fe.note, fe.createdAt ?: now)
        }

        for (np in unknown.values) {
            newPractices += Practice(
                np.id, np.name, np.measure, null, np.firstDay, false, position++,
                np.anyFocus || PracticeRules.defaultAskFocus(np.measure),
            )
        }
        if (state.practices.size + newPractices.size > PracticeRules.MAX_PRACTICES) {
            return Parsed.Failed(ImportError(ImportErrorKind.PracticeLimit, limit = PracticeRules.MAX_PRACTICES))
        }
        return Parsed.Ok(
            ImportChanges(
                practices = newPractices,
                periods = newPeriods,
                entries = newEntries,
                entriesSkipped = skipped,
                unknownNames = unknown.values.map { it.name },
                sadhanas = file.sadhanas.orEmpty(),
                sadhanasBad = file.sadhanasBad,
            ),
        )
    }
}
