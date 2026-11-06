package com.padakanti.sati.log

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LogExportTest {
    private val day = LocalDate.of(2026, 11, 5).toEpochDay()
    private val practice = Practice("p1", "Sukha, Kriya", Measure.Count, 108, day - 30, archived = false, position = 0)
    private val closed = Practice("p2", "Walk", Measure.Done, null, day - 30, archived = false, position = 1)

    @Test fun practicesFileHasAHeaderAndQuotedNames() {
        val csv = LogExport.practicesCsv(listOf(practice), emptyList())
        assertEquals(
            "id,name,measure,target,created,archived,archive_periods,ask_focus,every_days,schedule_start\r\n" +
                "p1,\"Sukha, Kriya\",count,108,2026-10-06,false,,true,1,1970-01-01\r\n",
            csv,
        )
    }

    @Test fun archivePeriodsAreWrittenAsDateRanges() {
        val periods = listOf(
            ArchivePeriod(2, "p2", day - 5, null),
            ArchivePeriod(1, "p2", day - 20, day - 10),
        )
        val csv = LogExport.practicesCsv(listOf(closed), periods)
        assertTrue(csv.contains("2026-10-16/2026-10-26;2026-10-31/,true,1,1970-01-01\r\n"))
    }

    @Test fun entriesFileHasAHeaderNamesAndNotes() {
        val e = Entry("e1", "p1", day, 108, 9, "He said \"om\"\nfor an hour", 1234)
        val csv = LogExport.entriesCsv(listOf(e), listOf(practice))
        assertEquals(
            "id,practice_id,practice,date,value,focus,note,created_at\r\n" +
                "e1,p1,\"Sukha, Kriya\",2026-11-05,108,9,\"He said \"\"om\"\"\nfor an hour\",1234\r\n",
            csv,
        )
    }

    @Test fun missingFocusAndNoteAreEmpty() {
        val csv = LogExport.entriesCsv(listOf(Entry("e1", "p2", day, 1, null, null, 7)), listOf(closed))
        assertTrue(csv.endsWith("e1,p2,Walk,2026-11-05,1,,,7\r\n"))
    }

    @Test fun fileNameHasTheDate() = assertEquals("sati-log-2026-11-05.zip", LogExport.fileName(day))

    @Test fun measuresReadBackFromText() {
        Measure.entries.forEach { assertEquals(it, LogExport.measureOf(LogExport.measureText(it))) }
        assertEquals(Measure.Count, LogExport.measureOf(" COUNT "))
        assertEquals(null, LogExport.measureOf("cycles"))
    }

    @Test fun zipHoldsTheThreeFilesAndReadsBack() {
        val out = ByteArrayOutputStream()
        LogZip.write(out, "p-text", "e-text", "[]")
        val bytes = out.toByteArray()
        assertTrue(LogZip.looksLikeZip(bytes))
        val read = (LogZip.read(bytes) as ZipRead.Ok).contents
        assertEquals(ZipContents("p-text", "e-text", "[]"), read)
    }

    @Test fun zipKeepsNonAsciiText() {
        val out = ByteArrayOutputStream()
        LogZip.write(out, "ॐ", "ünï", "{}")
        assertEquals("ॐ", (LogZip.read(out.toByteArray()) as ZipRead.Ok).contents.practices)
    }

    @Test fun plainCsvIsNotAZip() {
        assertFalse(LogZip.looksLikeZip("2026-11-01,A,5,".toByteArray()))
        assertFalse(LogZip.looksLikeZip(ByteArray(0)))
    }

    @Test fun aBrokenZipHasNoFilesToRead() {
        val bytes = byteArrayOf(0x50, 0x4B, 3, 4, 1, 2, 3)
        val read = LogZip.read(bytes)
        assertEquals(ZipRead.Ok(ZipContents(null, null, null)), read)
    }

    @Test fun aFileUnpackingToTooMuchIsRefused() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("entries.csv"))
            val chunk = ByteArray(1024 * 1024)
            repeat(LogZip.MAX_BYTES / chunk.size + 1) { z.write(chunk) }
            z.closeEntry()
        }
        assertEquals(ZipRead.TooLarge, LogZip.read(out.toByteArray()))
    }

    @Test fun filesInsideAFolderAreFoundByName() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("sati/practices.csv"))
            z.write("x".toByteArray())
            z.closeEntry()
        }
        assertEquals("x", (LogZip.read(out.toByteArray()) as ZipRead.Ok).contents.practices)
    }

    @Test fun readLimitedReturnsAFileThatFitsExactly() {
        val exact = ByteArray(LogZip.MAX_BYTES)
        assertEquals(LogZip.MAX_BYTES, LogZip.readLimited(exact.inputStream())!!.size)
    }

    @Test fun readLimitedRefusesOneByteMore() {
        assertEquals(null, LogZip.readLimited(ByteArray(LogZip.MAX_BYTES + 1).inputStream()))
    }

    @Test fun smallInputIsReturnedWhole() {
        val small = ByteArray(100) { 1 }
        assertArrayEquals(small, LogZip.readLimited(small.inputStream()))
    }

    @Test fun manyEntriesAreRefused() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            repeat(LogZip.MAX_ENTRIES + 1) {
                z.putNextEntry(ZipEntry("junk$it.txt"))
                z.closeEntry()
            }
        }
        assertEquals(ZipRead.TooLarge, LogZip.read(out.toByteArray()))
    }

    @Test fun tenEntriesAreAllowed() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            repeat(LogZip.MAX_ENTRIES) {
                z.putNextEntry(ZipEntry("junk$it.txt"))
                z.closeEntry()
            }
        }
        assertEquals(ZipRead.Ok(ZipContents(null, null, null)), LogZip.read(out.toByteArray()))
    }

    @Test fun theUnpackedLimitCoversAllEntriesTogether() {
        // Each file is under the limit. Together they are over it, and the unwanted one counts too.
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            val chunk = ByteArray(1024 * 1024)
            listOf("junk.bin", "entries.csv").forEach { name ->
                z.putNextEntry(ZipEntry(name))
                repeat(10) { z.write(chunk) }
                z.closeEntry()
            }
        }
        assertEquals(ZipRead.TooLarge, LogZip.read(out.toByteArray()))
    }
}
