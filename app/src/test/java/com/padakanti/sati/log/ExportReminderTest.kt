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

    @Test fun asksWhenThereWasNoExportAndEntriesExist() = assertTrue(ExportReminder.shouldAsk(0, at(11, 5), zone, hasEntries = true))

    @Test fun staysQuietWhenThereIsNothingToLose() = assertFalse(ExportReminder.shouldAsk(0, at(11, 5), zone, hasEntries = false))

    @Test fun asksOnlyAfterThirtyDays() {
        assertFalse(ExportReminder.shouldAsk(at(10, 6), at(11, 5), zone, hasEntries = true))
        assertTrue(ExportReminder.shouldAsk(at(10, 5), at(11, 5), zone, hasEntries = true))
    }
}
