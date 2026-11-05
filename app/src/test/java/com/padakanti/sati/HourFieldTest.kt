package com.padakanti.sati

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HourFieldTest {
    private val day = Hours(7 * 60, 22 * 60)
    private val end = Hours(8 * 60, 21 * 60)

    @Test fun readsTheRightValue() {
        assertEquals(7 * 60, HourField.WeekdayStart.valueIn(day, end))
        assertEquals(22 * 60, HourField.WeekdayEnd.valueIn(day, end))
        assertEquals(8 * 60, HourField.WeekendStart.valueIn(day, end))
        assertEquals(21 * 60, HourField.WeekendEnd.valueIn(day, end))
    }

    @Test fun earlierStartIsAccepted() =
        assertEquals(Hours(6 * 60, 22 * 60), HourField.WeekdayStart.apply(day, 6 * 60))

    @Test fun startAtEndIsRefused() = assertNull(HourField.WeekdayStart.apply(day, 22 * 60))

    @Test fun startAfterEndIsRefused() = assertNull(HourField.WeekendStart.apply(day, 23 * 60))

    @Test fun laterEndIsAccepted() =
        assertEquals(Hours(7 * 60, 23 * 60), HourField.WeekdayEnd.apply(day, 23 * 60))

    @Test fun endAtStartIsRefused() = assertNull(HourField.WeekdayEnd.apply(day, 7 * 60))

    @Test fun refusalNamesTheField() {
        assertEquals("The start must be before the end.", HourField.WeekendStart.refusal)
        assertEquals("The end must be after the start.", HourField.WeekdayEnd.refusal)
    }
}
