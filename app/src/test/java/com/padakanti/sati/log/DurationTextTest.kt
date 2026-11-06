package com.padakanti.sati.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DurationTextTest {
    @Test fun minutesAloneWork() = assertEquals(1800, DurationText.fromFields("30", ""))

    @Test fun minutesAndSecondsAdd() = assertEquals(90, DurationText.fromFields("1", "30"))

    @Test fun secondsAloneWork() = assertEquals(45, DurationText.fromFields("", "45"))

    @Test fun bothEmptyIsNothing() = assertNull(DurationText.fromFields("", ""))

    @Test fun secondsOverFiftyNineAreRefused() {
        assertNull(DurationText.fromFields("1", "60"))
        assertEquals(59, DurationText.fromFields("0", "59"))
    }

    @Test fun theTotalMayBeOneDayButNoMore() {
        assertEquals(86_400, DurationText.fromFields("1440", ""))
        assertNull(DurationText.fromFields("1440", "1"))
        assertNull(DurationText.fromFields("1500", ""))
    }

    @Test fun textThatIsNotANumberIsRefused() {
        assertNull(DurationText.fromFields("a", ""))
        assertNull(DurationText.fromFields("", "-1"))
    }

    @Test fun aHandMadePlainNumberIsMinutes() {
        assertEquals(1800, DurationText.parseHandMade("30"))
        assertEquals(0, DurationText.parseHandMade("0"))
    }

    @Test fun aHandMadeColonValueIsMinutesAndSeconds() {
        assertEquals(90, DurationText.parseHandMade("1:30"))
        assertEquals(5, DurationText.parseHandMade("0:05"))
        assertNull(DurationText.parseHandMade("1:60"))
    }

    @Test fun aHandMadeValueWithSIsSeconds() {
        assertEquals(45, DurationText.parseHandMade("45s"))
        assertEquals(45, DurationText.parseHandMade(" 45 S "))
        assertEquals(86_400, DurationText.parseHandMade("86400s"))
    }

    @Test fun aHandMadeValueOverTheMaximumIsRefused() {
        assertEquals(86_400, DurationText.parseHandMade("1440"))
        assertNull(DurationText.parseHandMade("1441"))
        assertNull(DurationText.parseHandMade("86401s"))
        assertNull(DurationText.parseHandMade("1440:01"))
    }

    @Test fun otherHandMadeTextIsRefused() {
        assertNull(DurationText.parseHandMade("abc"))
        assertNull(DurationText.parseHandMade("72%"))
        assertNull(DurationText.parseHandMade(""))
        assertNull(DurationText.parseHandMade("-5"))
    }

    @Test fun splitGivesMinutesAndSecondsLeft() {
        assertEquals(1 to 30, DurationText.split(90))
        assertEquals(0 to 45, DurationText.split(45))
        assertEquals(30 to 0, DurationText.split(1800))
        assertEquals(1440 to 0, DurationText.split(86_400))
    }

    @Test fun theFormFieldsAreFilledFromSeconds() {
        assertEquals("1" to "30", DurationText.fieldsOf(90))
        assertEquals("" to "45", DurationText.fieldsOf(45))
        assertEquals("30" to "", DurationText.fieldsOf(1800))
        assertEquals("0" to "", DurationText.fieldsOf(0))
    }

    @Test fun fieldsAndSecondsRoundTrip() {
        listOf(1, 59, 60, 61, 1800, 3599, 86_400).forEach {
            val (m, s) = DurationText.fieldsOf(it)
            assertEquals(it, DurationText.fromFields(m, s))
        }
    }
}
