package com.padakanti.sati.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntryRulesTest {
    @Test fun focusFromPercentRoundsHalfUp() {
        assertEquals(9, Focus.fromPercent(85))
        assertEquals(8, Focus.fromPercent(84))
        assertEquals(8, Focus.fromPercent(75))
        assertEquals(10, Focus.fromPercent(95))
        assertEquals(6, Focus.fromPercent(55))
        assertEquals(5, Focus.fromPercent(54))
    }

    @Test fun focusFromPercentIsAtLeastOne() {
        assertEquals(1, Focus.fromPercent(0))
        assertEquals(1, Focus.fromPercent(4))
        assertEquals(1, Focus.fromPercent(5))
        assertEquals(1, Focus.fromPercent(14))
        assertEquals(2, Focus.fromPercent(15))
    }

    @Test fun focusFromPercentIsAtMostTen() {
        assertEquals(10, Focus.fromPercent(100))
        assertEquals(10, Focus.fromPercent(250))
    }

    @Test fun prefillPrefersTheTarget() = assertEquals(108, Prefill.value(target = 108, lastValue = 40))

    @Test fun prefillFallsBackToTheLastValue() = assertEquals(40, Prefill.value(target = null, lastValue = 40))

    @Test fun prefillIsEmptyWithNeither() = assertNull(Prefill.value(null, null))

    private fun problem(measure: Measure = Measure.Minutes, value: Int? = 20, focus: Int? = 7, note: String? = null, day: Long = 100, today: Long = 100) =
        EntryRules.problem(measure, value, focus, note, day, today)

    @Test fun aGoodEntryHasNoProblem() = assertNull(problem())

    @Test fun focusIsOptional() = assertNull(problem(focus = null))

    @Test fun pastDaysAreAllowed() = assertNull(problem(day = 3))

    @Test fun futureDaysAreRefused() = assertEquals(EntryProblem.FutureDay, problem(day = 101))

    @Test fun minutesNeedAValueFromOneUp() {
        assertEquals(EntryProblem.BadValue, problem(value = null))
        assertEquals(EntryProblem.BadValue, problem(value = 0))
        assertEquals(EntryProblem.BadValue, problem(value = EntryRules.MAX_VALUE + 1))
    }

    @Test fun doneAlwaysHasValueOne() {
        assertNull(problem(measure = Measure.Done, value = 1, focus = null))
        assertEquals(EntryProblem.BadValue, problem(measure = Measure.Done, value = 2, focus = null))
    }

    @Test fun focusMustBeOneToTen() {
        assertEquals(EntryProblem.BadFocus, problem(focus = 0))
        assertEquals(EntryProblem.BadFocus, problem(focus = 11))
        assertNull(problem(focus = 10))
    }

    @Test fun longNotesAreRefused() = assertEquals(EntryProblem.NoteTooLong, problem(note = "x".repeat(EntryRules.MAX_NOTE + 1)))

    @Test fun practiceNamesMatchWithCaseIgnored() {
        assertEquals(PracticeProblem.NameTaken, PracticeRules.problem(" hatha yoga ", Measure.Done, null, listOf("Hatha Yoga")))
        assertNull(PracticeRules.problem("Kriya", Measure.Count, 108, listOf("Hatha Yoga")))
    }

    @Test fun practiceNeedsAName() = assertEquals(PracticeProblem.NameBlank, PracticeRules.problem("  ", Measure.Done, null, emptyList()))

    @Test fun targetMustBePositive() {
        assertEquals(PracticeProblem.TargetInvalid, PracticeRules.problem("A", Measure.Minutes, 0, emptyList()))
        assertNull(PracticeRules.problem("A", Measure.Minutes, null, emptyList()))
    }

    @Test fun doneHasNoTarget() {
        assertNull(PracticeRules.targetFor(Measure.Done, 5))
        assertNull(PracticeRules.problem("A", Measure.Done, 0, emptyList()))
    }

    @Test fun notesMayHaveNewLinesButNoOtherHiddenCharacters() {
        assertNull(problem(note = "line one\nline two"))
        assertEquals(EntryProblem.NoteBadChars, problem(note = "a\u202Eb"))
        assertEquals(EntryProblem.NoteBadChars, problem(note = "a\tb"))
    }

    @Test fun practiceNamesRefuseHiddenCharactersAndNewLines() {
        assertEquals(PracticeProblem.NameHidden, PracticeRules.problem("Yo\u200Bga", Measure.Done, null, emptyList()))
        assertEquals(PracticeProblem.NameHidden, PracticeRules.problem("Yo\nga", Measure.Done, null, emptyList()))
    }

    @Test fun practiceNamesCompareInNfcForm() {
        assertEquals(PracticeProblem.NameTaken, PracticeRules.problem("Cafe\u0301", Measure.Done, null, listOf("Caf\u00E9")))
        assertEquals("Caf\u00E9", PracticeRules.clean("  Cafe\u0301 "))
    }

    @Test fun practiceLimitIsTwoHundred() = assertEquals(200, PracticeRules.MAX_PRACTICES)
}
