package com.padakanti.sati.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {
    private fun rows(text: String): List<List<String>> =
        (Csv.parse(text) as CsvParse.Ok).records.map { it.fields }

    @Test fun plainFieldsAreNotQuoted() = assertEquals("a,b,c\r\n", Csv.write(listOf(listOf("a", "b", "c"))))

    @Test fun commasQuotesAndLineBreaksAreQuoted() {
        assertEquals("\"a,b\"\r\n", Csv.write(listOf(listOf("a,b"))))
        assertEquals("\"say \"\"om\"\"\"\r\n", Csv.write(listOf(listOf("say \"om\""))))
        assertEquals("\"one\ntwo\"\r\n", Csv.write(listOf(listOf("one\ntwo"))))
    }

    @Test fun writeThenParseGivesTheSameRows() {
        val original = listOf(
            listOf("id", "name", "note"),
            listOf("1", "Sukha, Kriya", "He said \"calm\"\nthen sat\r\nagain"),
            listOf("2", "", ""),
            listOf("3", "  spaced  ", "ünï©ode ॐ"),
        )
        assertEquals(original, rows(Csv.write(original)))
    }

    @Test fun readsLfCrlfAndCr() {
        val expected = listOf(listOf("a", "b"), listOf("c", "d"), listOf("e", "f"))
        assertEquals(expected, rows("a,b\nc,d\ne,f"))
        assertEquals(expected, rows("a,b\r\nc,d\r\ne,f\r\n"))
        assertEquals(expected, rows("a,b\rc,d\re,f"))
    }

    @Test fun skipsBlankLines() = assertEquals(listOf(listOf("a"), listOf("b")), rows("a\n\n\nb\n"))

    @Test fun skipsRowsOfOnlyBlankFields() = assertEquals(listOf(listOf("a", "b")), rows(",,,\na,b\n , ,\n\"\",\"\"\n"))

    @Test fun keepsEmptyFields() = assertEquals(listOf(listOf("a", "", "")), rows("a,,\n"))

    @Test fun ignoresAByteOrderMark() = assertEquals(listOf(listOf("date", "x")), rows("${0xFEFF.toChar()}date,x"))

    @Test fun recordsKnowTheirStartLine() {
        val records = (Csv.parse("a\n\"x\ny\"\nb") as CsvParse.Ok).records
        assertEquals(listOf(1, 2, 4), records.map { it.line })
    }

    @Test fun anUnclosedQuoteIsBrokenAtItsStart() {
        val result = Csv.parse("a,b\nc,\"oops\nmore")
        assertEquals(CsvParse.Broken(2), result)
    }

    @Test fun textAfterAClosingQuoteIsBroken() = assertTrue(Csv.parse("\"a\"b,c") is CsvParse.Broken)

    @Test fun aQuoteInsideAnUnquotedFieldIsKept() = assertEquals(listOf(listOf("ab\"c")), rows("ab\"c"))

    @Test fun emptyTextHasNoRecords() = assertEquals(emptyList<List<String>>(), rows(""))

    @Test fun maxRecordsAllowsExactlyThatMany() {
        assertTrue(Csv.parse("a\nb\nc", maxRecords = 3) is CsvParse.Ok)
        assertEquals(CsvParse.TooMany(4), Csv.parse("a\nb\nc\nd", maxRecords = 3))
        assertEquals(CsvParse.TooMany(4), Csv.parse("a\nb\nc\nd\n", maxRecords = 3))
    }

    @Test fun blankRowsDoNotCountTowardTheLimit() = assertTrue(Csv.parse("a\n,,\n\nb", maxRecords = 2) is CsvParse.Ok)
}
