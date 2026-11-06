package com.padakanti.sati.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ExportReminderTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(m: Int, d: Int, h: Int = 12) = LocalDateTime.of(2026, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun noExportMeansNoDays() = assertNull(ExportReminder.daysSince(0, at(11, 5), zone))

    @Test fun countsCalendarDays() {
        assertEquals(0, ExportReminder.daysSince(at(11, 5, 8), at(11, 5, 22), zone))
        assertEquals(1, ExportReminder.daysSince(at(11, 4, 23), at(11, 5, 1), zone))
        assertEquals(31, ExportReminder.daysSince(at(10, 5), at(11, 5), zone))
    }

    @Test fun theFirstAskComesWhenThereWasNoExportAndEntriesExist() = assertTrue(ExportReminder.shouldAskFirst(0, hasEntries = true))

    @Test fun theFirstAskStaysQuietWhenThereIsNothingToLose() = assertFalse(ExportReminder.shouldAskFirst(0, hasEntries = false))

    @Test fun theFirstAskStopsAfterAnExport() = assertFalse(ExportReminder.shouldAskFirst(at(11, 5), hasEntries = true))

    @Test fun theTopReminderNeverComesBeforeTheFirstExport() = assertFalse(ExportReminder.shouldRemind(0, at(11, 5), zone))

    @Test fun theTopReminderComesOnlyAfterThirtyDays() {
        assertFalse(ExportReminder.shouldRemind(at(10, 6), at(11, 5), zone))
        assertTrue(ExportReminder.shouldRemind(at(10, 5), at(11, 5), zone))
    }

    @Test fun theTwoAsksNeverCoincide() {
        val neverExported = listOf(true, false).map { ExportReminder.shouldAskFirst(0, it) to ExportReminder.shouldRemind(0, at(11, 5), zone) }
        assertTrue(neverExported.none { it.first && it.second })
        val old = at(1, 1)
        assertFalse(ExportReminder.shouldAskFirst(old, hasEntries = true))
        assertTrue(ExportReminder.shouldRemind(old, at(11, 5), zone))
    }
}
