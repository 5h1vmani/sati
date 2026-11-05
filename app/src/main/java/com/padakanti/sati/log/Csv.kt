package com.padakanti.sati.log

/** One row of a CSV file. [line] is the line where the row starts, counting from 1. */
data class CsvRecord(val line: Int, val fields: List<String>)

/** The result of reading CSV text. [line] is where the text broke. */
sealed interface CsvParse {
    data class Ok(val records: List<CsvRecord>) : CsvParse
    data class Broken(val line: Int) : CsvParse

    /** There are more rows than the caller allows. [line] is where the first extra row starts. */
    data class TooMany(val line: Int) : CsvParse
}

/**
 * CSV as spreadsheets write it. A field with a comma, a quote or a line break
 * is wrapped in quotes, and a quote inside it is doubled.
 */
object Csv {
    /** The byte order mark some programs put at the start of a file, as a character. */
    private val BOM = 0xFEFF.toChar()

    fun write(rows: List<List<String>>): String = rows.joinToString("") { row ->
        row.joinToString(",") { quote(it) } + "\r\n"
    }

    private fun quote(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    /**
     * Reads CSV text. Blank lines, and rows with only blank fields, are skipped, a leading byte order mark is
     * ignored, [maxRecords] limits the rows, and a line ends with LF, CRLF or CR. Text with a quote that is
     * never closed, or with characters after a closing quote, is [CsvParse.Broken].
     */
    fun parse(text: String, maxRecords: Int = Int.MAX_VALUE): CsvParse {
        val s = text.removePrefix(BOM.toString())
        val records = mutableListOf<CsvRecord>()
        var fields = mutableListOf<String>()
        val field = StringBuilder()
        var line = 1
        var recordLine = 1
        var quoted = false
        var closedQuote = false
        var started = false
        var i = 0

        fun endField() {
            fields.add(field.toString())
            field.clear()
            quoted = false
            closedQuote = false
        }

        fun endRecord() {
            if (started) {
                endField()
                // A row of only commas or spaces, such as ",,,", holds no data.
                if (fields.any { it.isNotBlank() }) records.add(CsvRecord(recordLine, fields))
            }
            fields = mutableListOf()
            started = false
        }

        while (i < s.length) {
            val c = s[i]
            if (quoted) {
                when {
                    c == '"' && i + 1 < s.length && s[i + 1] == '"' -> {
                        field.append('"')
                        i++
                    }
                    c == '"' -> {
                        quoted = false
                        closedQuote = true
                    }
                    else -> {
                        if (c == '\n') line++
                        field.append(c)
                    }
                }
            } else {
                when {
                    c == '\r' || c == '\n' -> {
                        if (c == '\r' && i + 1 < s.length && s[i + 1] == '\n') i++
                        endRecord()
                        if (records.size > maxRecords) return CsvParse.TooMany(records.last().line)
                        line++
                        recordLine = line
                    }
                    closedQuote && c != ',' -> return CsvParse.Broken(line)
                    c == ',' -> {
                        if (!started) started = true
                        endField()
                    }
                    c == '"' && field.isEmpty() && !closedQuote -> {
                        if (!started) started = true
                        quoted = true
                    }
                    else -> {
                        if (!started) started = true
                        field.append(c)
                    }
                }
            }
            i++
        }
        if (quoted) return CsvParse.Broken(recordLine)
        endRecord()
        if (records.size > maxRecords) return CsvParse.TooMany(records.last().line)
        return CsvParse.Ok(records)
    }
}

/**
 * A spreadsheet runs a cell that starts with = + - @ as a formula. On export,
 * a text field that starts with one of those, a tab, a carriage return or an
 * apostrophe gets one apostrophe in front. On import, exactly one leading
 * apostrophe is removed, so a round trip gives back the same text.
 */
object CsvGuard {
    private val TRIGGERS = setOf('=', '+', '-', '@', '\t', '\r', '\'')

    fun guard(text: String): String = if (text.isNotEmpty() && text[0] in TRIGGERS) "'" + text else text

    fun unguard(text: String): String = text.removePrefix("'")
}
