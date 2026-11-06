package com.padakanti.sati.log

import java.util.Locale

/**
 * The text of the two CSV files in an export. Days are written as
 * yyyy-mm-dd, so the files read well in a spreadsheet.
 */
object LogExport {
    /**
     * The practice columns grew twice: ask_focus, then every_days and schedule_start.
     * Import reads files with any of the three headers. A Duration is counted in
     * seconds and written as "duration". Older files wrote "minutes", counted in minutes.
     */
    val PRACTICE_HEADER_OLD = listOf("id", "name", "measure", "target", "created", "archived", "archive_periods")
    val PRACTICE_HEADER_ASK_FOCUS = PRACTICE_HEADER_OLD + "ask_focus"
    val PRACTICE_HEADER = PRACTICE_HEADER_ASK_FOCUS + listOf("every_days", "schedule_start")
    val ENTRY_HEADER = listOf("id", "practice_id", "practice", "date", "value", "focus", "note", "created_at")

    fun fileName(day: Long): String = "sati-log-${LogDay.date(day)}.zip"

    fun measureText(m: Measure): String = m.name.lowercase(Locale.ROOT)

    fun measureOf(text: String): Measure? = Measure.entries.firstOrNull { measureText(it) == text.trim().lowercase(Locale.ROOT) }

    /** Archive periods as `from/to` days joined by `;`. An open period has an empty `to`. */
    private fun periodsText(periods: List<ArchivePeriod>): String =
        periods.joinToString(";") { "${LogDay.date(it.fromDay)}/${it.toDay?.let(LogDay::date) ?: ""}" }

    fun practicesCsv(practices: List<Practice>, periods: List<ArchivePeriod>): String {
        val byPractice = periods.groupBy { it.practiceId }
        return Csv.write(
            listOf(PRACTICE_HEADER) + practices.map {
                listOf(
                    it.id,
                    CsvGuard.guard(it.name),
                    measureText(it.measure),
                    it.target?.toString() ?: "",
                    LogDay.date(it.createdDay).toString(),
                    it.archived.toString(),
                    periodsText(byPractice[it.id].orEmpty().sortedBy { p -> p.fromDay }),
                    it.askFocus.toString(),
                    it.everyDays.toString(),
                    LogDay.date(it.scheduleStart).toString(),
                )
            },
        )
    }

    fun entriesCsv(entries: List<Entry>, practices: List<Practice>): String {
        val names = practices.associate { it.id to it.name }
        return Csv.write(
            listOf(ENTRY_HEADER) + entries.map {
                listOf(
                    it.id,
                    it.practiceId,
                    CsvGuard.guard(names[it.practiceId] ?: ""),
                    LogDay.date(it.day).toString(),
                    it.value.toString(),
                    it.focus?.toString() ?: "",
                    CsvGuard.guard(it.note ?: ""),
                    it.createdAt.toString(),
                )
            },
        )
    }
}
