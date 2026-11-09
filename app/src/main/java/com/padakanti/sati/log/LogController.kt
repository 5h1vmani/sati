package com.padakanti.sati.log

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.padakanti.sati.SadhanaJson
import com.padakanti.sati.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.UUID

/**
 * The one place that writes the practice log. The screen calls it for every
 * change: practices, entries, undo, export and import. Every function that
 * touches the database is a suspend function and runs off the main thread.
 *
 * Days are local dates as `LocalDate.toEpochDay()`, worked out with [LogDay].
 */
object LogController {
    /**
     * Runs when work in [scope] fails with something unexpected, such as a full
     * disk. The screen sets it to clear its busy state and show a message.
     */
    @Volatile var onFailure: (Throwable) -> Unit = {}

    /** Work that must finish even if the screen leaves, such as a write after a tap. */
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e ->
            if (e !is CancellationException) onFailure(e)
        },
    )

    private fun dao(context: Context) = LogDb.get(context).dao()

    private fun today(now: Long) = LogDay.of(now, ZoneId.systemDefault())

    private fun newId() = UUID.randomUUID().toString()

    // ---------- reading, for the screen ----------

    fun practices(context: Context): Flow<List<Practice>> = dao(context).practices()

    fun entriesOn(context: Context, day: Long): Flow<List<Entry>> = dao(context).entriesBetween(day, day)

    fun entriesOf(context: Context, practiceId: String): Flow<List<Entry>> = dao(context).entriesOf(practiceId)

    /** The entries from [from] to [to], both included. The analytics load only the days they show. */
    fun entriesBetween(context: Context, from: Long, to: Long): Flow<List<Entry>> = dao(context).entriesBetween(from, to)

    fun archivePeriods(context: Context): Flow<List<ArchivePeriod>> = dao(context).archivePeriods()

    fun firstEntries(context: Context): Flow<List<FirstEntry>> = dao(context).firstEntries()

    fun entryTotal(context: Context): Flow<Int> = dao(context).entryTotal()

    /** The value of the newest entry of a practice that is not a not-done mark, for [Prefill]. */
    suspend fun lastValue(context: Context, practiceId: String): Int? = dao(context).lastEntryOf(practiceId)?.value

    /** How many entries a delete of the practice would remove. */
    suspend fun entryCount(context: Context, practiceId: String): Int = dao(context).entryCount(practiceId)

    // ---------- practices ----------

    /** Adds a practice at the end of the list. Returns what is wrong with it, or null when it was added. */
    suspend fun addPractice(
        context: Context,
        name: String,
        measure: Measure,
        target: Int?,
        askFocus: Boolean,
        everyDays: Int,
        scheduleStart: Long,
        now: Long = System.currentTimeMillis(),
    ): PracticeProblem? {
        val db = LogDb.get(context)
        return db.inTransaction {
            val dao = db.dao()
            val all = dao.allPractices()
            if (all.size >= PracticeRules.MAX_PRACTICES) return@inTransaction PracticeProblem.LimitReached
            val problem = PracticeRules.problem(name, measure, target, all.map { it.name }, everyDays)
            if (problem != null) return@inTransaction problem
            dao.upsertPractice(
                Practice(
                    id = newId(),
                    name = PracticeRules.clean(name),
                    measure = measure,
                    target = PracticeRules.targetFor(measure, target),
                    createdDay = today(now),
                    archived = false,
                    position = dao.nextPosition(),
                    askFocus = askFocus,
                    everyDays = everyDays,
                    scheduleStart = scheduleStart,
                ),
            )
            null
        }
    }

    /**
     * Changes a practice's name, target, measure, focus switch or repeat schedule. The measure changes only
     * while the practice has no entries, because old entries would no longer
     * match it. Returns what is wrong, or null when it was saved.
     */
    suspend fun editPractice(
        context: Context,
        id: String,
        name: String,
        measure: Measure,
        target: Int?,
        askFocus: Boolean,
        everyDays: Int,
        scheduleStart: Long,
    ): PracticeProblem? {
        val db = LogDb.get(context)
        return db.inTransaction {
            val dao = db.dao()
            val old = dao.practiceById(id) ?: return@inTransaction PracticeProblem.NoPractice
            val others = dao.allPractices().filterNot { it.id == id }.map { it.name }
            val problem = PracticeRules.problem(name, measure, target, others, everyDays)
            if (problem != null) return@inTransaction problem
            PracticeRules.measureProblem(old.measure, measure, dao.entryCount(id))?.let { return@inTransaction it }
            dao.upsertPractice(
                old.copy(
                    name = PracticeRules.clean(name),
                    measure = measure,
                    target = PracticeRules.targetFor(measure, target),
                    askFocus = askFocus,
                    everyDays = everyDays,
                    scheduleStart = scheduleStart,
                ),
            )
            null
        }
    }

    /** Moves a practice to place [index], after a drag. All new positions are written in one transaction. */
    suspend fun moveTo(context: Context, id: String, index: Int) {
        val db = LogDb.get(context)
        db.inTransaction {
            val dao = db.dao()
            PracticeRules.moveTo(dao.allPractices(), id, index).forEach { dao.upsertPractice(it) }
        }
    }

    /** Moves a practice one place up or down. Today follows the order. The change is one transaction. */
    suspend fun movePractice(context: Context, id: String, up: Boolean) {
        val db = LogDb.get(context)
        db.inTransaction {
            val dao = db.dao()
            PracticeRules.moved(dao.allPractices(), id, up).forEach { dao.upsertPractice(it) }
        }
    }

    /**
     * Archives a practice or brings it back. An archive opens an archive
     * period that starts today. A return closes the open period with today as
     * the day it came back, so that day counts as active.
     */
    suspend fun setArchived(context: Context, id: String, archived: Boolean, now: Long = System.currentTimeMillis()) {
        val db = LogDb.get(context)
        db.inTransaction {
            val dao = db.dao()
            val practice = dao.practiceById(id) ?: return@inTransaction
            val change = ArchiveRules.change(practice, dao.openArchivePeriod(id), archived, today(now)) ?: return@inTransaction
            dao.upsertPractice(change.practice)
            change.open?.let { dao.insertArchivePeriod(it) }
            change.close?.let { dao.updateArchivePeriod(it) }
        }
    }

    /** Deletes a practice, its entries and its archive periods. The screen shows [entryCount] and asks first. */
    suspend fun deletePractice(context: Context, id: String) {
        dao(context).deletePractice(id)
    }

    // ---------- entries ----------

    /** What a save of an entry returns: the saved entry, or the problem. */
    data class Saved(val entry: Entry?, val problem: EntryProblem?)

    /**
     * Adds an entry on [day], which is today unless the user chose a past day.
     * [value] must fit the measure. 0 means not done, for every measure, and a
     * not-done entry has no focus.
     */
    suspend fun addEntry(
        context: Context,
        practiceId: String,
        day: Long,
        value: Int?,
        focus: Int?,
        note: String?,
        now: Long = System.currentTimeMillis(),
    ): Saved {
        val practice = dao(context).practiceById(practiceId) ?: return Saved(null, EntryProblem.NoPractice)
        val n = note?.trim()?.ifEmpty { null }
        EntryRules.problem(practice.measure, value, focus, n, day, today(now))?.let { return Saved(null, it) }
        val entry = Entry(newId(), practiceId, day, value!!, focus, n, now)
        dao(context).upsertEntry(entry)
        return Saved(entry, null)
    }

    /**
     * The one-tap log of a done practice, for today. Returns the entry, so the
     * screen can offer Undo. Returns null, and adds nothing, when the practice
     * already has an entry today, so a double tap logs once.
     */
    suspend fun logDone(context: Context, practiceId: String, now: Long = System.currentTimeMillis()): Entry? =
        logOnce(context, practiceId, EntryRules.DONE_VALUE, now)

    /** Like [logDone], but marks any practice as not done today, with value 0. Adds nothing when it already has an entry today. */
    suspend fun markNotDone(context: Context, practiceId: String, now: Long = System.currentTimeMillis()): Entry? =
        logOnce(context, practiceId, EntryRules.NOT_DONE_VALUE, now)

    private suspend fun logOnce(context: Context, practiceId: String, value: Int, now: Long): Entry? {
        val db = LogDb.get(context)
        return db.inTransaction {
            val day = today(now)
            if (db.dao().entryCountOn(practiceId, day) > 0) return@inTransaction null
            addEntry(context, practiceId, day, value, null, null, now).entry
        }
    }

    /** Changes an entry. Its id and the time it was written stay the same. */
    suspend fun editEntry(
        context: Context,
        entry: Entry,
        day: Long,
        value: Int?,
        focus: Int?,
        note: String?,
        now: Long = System.currentTimeMillis(),
    ): EntryProblem? {
        val practice = dao(context).practiceById(entry.practiceId) ?: return EntryProblem.NoPractice
        val n = note?.trim()?.ifEmpty { null }
        EntryRules.problem(practice.measure, value, focus, n, day, today(now))?.let { return it }
        dao(context).upsertEntry(entry.copy(day = day, value = value!!, focus = focus, note = n))
        return null
    }

    suspend fun deleteEntry(context: Context, id: String) {
        dao(context).deleteEntry(id)
    }

    /**
     * Puts a deleted entry back. False when its practice was deleted meanwhile,
     * or its measure changed so the entry no longer fits.
     */
    suspend fun undoDelete(context: Context, entry: Entry): Boolean {
        val dao = dao(context)
        val practice = dao.practiceById(entry.practiceId) ?: return false
        if (EntryRules.valueProblem(practice.measure, entry.value, entry.focus) != null) return false
        dao.upsertEntry(entry)
        return true
    }

    // ---------- export ----------

    /**
     * Writes the ZIP to [uri]. Returns the number of entries in it, or null
     * when writing failed. A failed export deletes the file only if it was
     * already opened for writing.
     */
    suspend fun export(context: Context, uri: Uri, now: Long = System.currentTimeMillis()): Int? = withContext(Dispatchers.IO) {
        // Opening the file for writing empties it. Before that point, a failure leaves the file as it was.
        var opened = false
        try {
            val db = LogDb.get(context)
            val (practices, periods, entries) = db.inTransaction {
                val dao = db.dao()
                Triple(dao.allPractices(), dao.allArchivePeriods(), dao.allEntries())
            }
            val sadhanas = SadhanaJson.encodeList(Store(context).sadhanas)
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("No output stream")
            opened = true
            out.use {
                LogZip.write(
                    it,
                    LogExport.practicesCsv(practices, periods),
                    LogExport.entriesCsv(entries, practices),
                    sadhanas,
                )
            }
            Store(context).lastLogExport = now
            entries.size
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // A failure after the file was opened leaves it broken or empty. Remove it. A failure before that
            // keeps the old file, which may be an older backup the user chose to replace.
            if (opened) {
                try {
                    DocumentsContract.deleteDocument(context.contentResolver, uri)
                } catch (ignored: Exception) {
                }
            }
            null
        }
    }

    // ---------- import ----------

    /**
     * Reads and checks the file at [uri], a ZIP from [export] or a hand-made
     * CSV. Nothing is written yet. When the file names practices the log does
     * not know, the answer is [LogImportState.Asking], so the screen can ask
     * for their measures. Otherwise the import runs at once.
     */
    suspend fun startImport(context: Context, uri: Uri, now: Long = System.currentTimeMillis()): LogImportState {
        val bytes = when (val read = withContext(Dispatchers.IO) { readBytes(context, uri) }) {
            is Parsed.Failed -> return LogImportState.Failed(read.error)
            is Parsed.Ok -> read.value
        }
        val db = LogDb.get(context)
        val state = db.inTransaction { LogState(db.dao().allPractices(), db.dao().allEntries()) }
        // Parsing and merging a big file is slow, so it runs off the main thread.
        val checked = withContext(Dispatchers.Default) {
            when (val file = parse(bytes, today(now))) {
                is Parsed.Failed -> Parsed.Failed(file.error)
                is Parsed.Ok -> when (val merged = LogImport.merge(file.value, state, emptyMap(), now)) {
                    is Parsed.Failed -> Parsed.Failed(merged.error)
                    is Parsed.Ok -> Parsed.Ok(file.value to merged.value.unknownNames)
                }
            }
        }
        val (file, unknownNames) = when (checked) {
            is Parsed.Failed -> return LogImportState.Failed(checked.error)
            is Parsed.Ok -> checked.value
        }
        return if (unknownNames.isEmpty()) finishImport(context, file, emptyMap(), now) else LogImportState.Asking(file, unknownNames)
    }

    /**
     * Runs a checked import in one transaction. [measures] gives the measure
     * for each name [startImport] asked about. A file with an error changes
     * nothing. Sadhanas in the file merge into the saved ones by id.
     */
    suspend fun finishImport(
        context: Context,
        file: ImportFile,
        measures: Map<String, Measure>,
        now: Long = System.currentTimeMillis(),
    ): LogImportState {
        val db = LogDb.get(context)
        val outcome = db.inTransaction {
            val dao = db.dao()
            // Read inside the transaction, so nothing can change between the check and the write.
            val state = LogState(dao.allPractices(), dao.allEntries())
            val merged = LogImport.merge(file, state, measures, now, final = true)
            if (merged is Parsed.Ok) {
                val c = merged.value
                c.practices.forEach { dao.upsertPractice(it) }
                c.periods.forEach { dao.insertArchivePeriod(it) }
                dao.insertEntriesIgnoringIds(c.entries)
            }
            merged
        }
        return when (outcome) {
            is Parsed.Failed -> LogImportState.Failed(outcome.error)
            is Parsed.Ok -> {
                val c = outcome.value
                var sadhanasAdded = 0
                var sadhanasSkipped = c.sadhanasBad
                if (c.sadhanas.isNotEmpty()) {
                    val store = Store(context)
                    val merge = LogImport.mergeSadhanas(store.sadhanas, c.sadhanas)
                    sadhanasAdded = merge.added
                    sadhanasSkipped += merge.skipped
                    // The sadhanas sit outside the database transaction. commit() waits for the disk.
                    if (merge.added > 0) withContext(Dispatchers.IO) { store.saveSadhanasNow(merge.list) }
                }
                LogImportState.Result(c.entries.size, c.entriesSkipped, c.practices.size, sadhanasAdded, sadhanasSkipped)
            }
        }
    }

    /** Reads the file at [uri], at most [LogZip.MAX_BYTES]. Blocks, so call it off the main thread. */
    private fun readBytes(context: Context, uri: Uri): Parsed<ByteArray> {
        val unreadable = Parsed.Failed(ImportError(ImportErrorKind.Unreadable))
        val stream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: SecurityException) {
            null
        } catch (e: java.io.IOException) {
            null
        } ?: return unreadable
        val bytes = try {
            stream.use(LogZip::readLimited)
        } catch (e: java.io.IOException) {
            return unreadable
        } ?: return Parsed.Failed(ImportError(ImportErrorKind.TooLarge))
        return Parsed.Ok(bytes)
    }

    private fun parse(bytes: ByteArray, today: Long): Parsed<ImportFile> =
        if (LogZip.looksLikeZip(bytes)) {
            when (val z = LogZip.read(bytes)) {
                ZipRead.TooLarge -> Parsed.Failed(ImportError(ImportErrorKind.TooLarge))
                ZipRead.Unreadable -> Parsed.Failed(ImportError(ImportErrorKind.Unreadable))
                is ZipRead.Ok -> LogImport.parseZip(z.contents, today)
            }
        } else {
            LogImport.parseHandMade(String(bytes, Charsets.UTF_8), today)
        }
}
