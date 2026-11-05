package com.padakanti.sati.log

import java.util.Locale

/**
 * The text of the two CSV files in an export. Days are written as
 * yyyy-mm-dd, so the files read well in a spreadsheet.
 */
object LogExport {
    val PRACTICE_HEADER = listOf("id", "name", "measure", "target", "created", "archived", "archive_periods")
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
