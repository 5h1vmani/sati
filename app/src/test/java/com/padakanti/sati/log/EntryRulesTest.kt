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

    private fun problem(measure: Measure = Measure.Duration, value: Int? = 20, focus: Int? = 7, note: String? = null, day: Long = 100, today: Long = 100) =
        EntryRules.problem(measure, value, focus, note, day, today)

    @Test fun aGoodEntryHasNoProblem() = assertNull(problem())

    @Test fun focusIsOptional() = assertNull(problem(focus = null))

    @Test fun pastDaysAreAllowed() = assertNull(problem(day = 3))

    @Test fun futureDaysAreRefused() = assertEquals(EntryProblem.FutureDay, problem(day = 101))

    @Test fun aDurationIsCountedInSecondsUpToOneDay() {
        assertEquals(EntryProblem.BadValue, problem(value = null))
        assertNull(problem(value = EntryRules.MAX_SECONDS))
        assertEquals(EntryProblem.BadValue, problem(value = EntryRules.MAX_SECONDS + 1))
        assertEquals(86_400, EntryRules.MAX_SECONDS)
    }

    @Test fun doneHasValueZeroOrOne() {
        assertNull(problem(measure = Measure.Done, value = 1, focus = null))
        assertNull(problem(measure = Measure.Done, value = 0, focus = null))
        assertEquals(EntryProblem.BadValue, problem(measure = Measure.Done, value = 2, focus = null))
        assertEquals(EntryProblem.BadValue, problem(measure = Measure.Done, value = -1, focus = null))
        assertEquals(EntryProblem.BadValue, problem(measure = Measure.Done, value = null, focus = null))
    }

    @Test fun aNotDoneEntryHasNoFocus() {
        assertEquals(EntryProblem.BadFocus, problem(measure = Measure.Done, value = 0, focus = 5))
        assertNull(problem(measure = Measure.Done, value = 1, focus = 5))
    }

    @Test fun percentIsZeroToOneHundred() {
        assertNull(problem(measure = Measure.Percent, value = 0, focus = null))
        assertNull(problem(measure = Measure.Percent, value = 100, focus = null))
        assertEquals(EntryProblem.BadValue, problem(measure = Measure.Percent, value = 101, focus = null))
        assertEquals(EntryProblem.BadValue, problem(measure = Measure.Percent, value = -1, focus = null))
        assertEquals(EntryProblem.BadValue, problem(measure = Measure.Percent, value = null, focus = null))
    }

    @Test fun scaleIsOneToTenAndZeroMeansNotDone() {
        assertNull(problem(measure = Measure.Scale, value = 0, focus = null))
        assertNull(problem(measure = Measure.Scale, value = 1, focus = null))
        assertNull(problem(measure = Measure.Scale, value = 10, focus = null))
        assertEquals(EntryProblem.BadValue, problem(measure = Measure.Scale, value = -1, focus = null))
        assertEquals(EntryProblem.BadValue, problem(measure = Measure.Scale, value = 11, focus = null))
        assertEquals(EntryProblem.BadValue, problem(measure = Measure.Scale, value = null, focus = null))
    }

    @Test fun countIsOneToTheMaximum() {
        assertNull(problem(measure = Measure.Count, value = 1))
        assertNull(problem(measure = Measure.Count, value = EntryRules.MAX_VALUE))
        assertEquals(EntryProblem.BadValue, problem(measure = Measure.Count, value = EntryRules.MAX_VALUE + 1))
        assertEquals(EntryProblem.BadValue, problem(measure = Measure.Count, value = -1))
    }

    @Test fun valueZeroIsNotDoneForAllFiveMeasuresAndNeedsNoFocus() {
        Measure.entries.forEach {
            assertNull("$it", problem(measure = it, value = 0, focus = null))
            assertEquals("$it", EntryProblem.BadFocus, problem(measure = it, value = 0, focus = 5))
        }
    }

    @Test fun everyMeasureMayHaveAFocusFromOneToTen() {
        Measure.entries.forEach {
            val value = if (it == Measure.Done) 1 else 5
            assertNull("$it", problem(measure = it, value = value, focus = 10))
            assertEquals("$it", EntryProblem.BadFocus, problem(measure = it, value = value, focus = 11))
            assertEquals("$it", EntryProblem.BadFocus, problem(measure = it, value = value, focus = 0))
        }
    }

    @Test fun theRangesAreTheNamedConstants() {
        assertEquals(0..1, EntryRules.valueRange(Measure.Done))
        assertEquals(0..100, EntryRules.valueRange(Measure.Percent))
        assertEquals(0..10, EntryRules.valueRange(Measure.Scale))
        assertEquals(0..99_999, EntryRules.valueRange(Measure.Count))
        assertEquals(0..86_400, EntryRules.valueRange(Measure.Duration))
        assertEquals(0, EntryRules.NOT_DONE_VALUE)
        assertEquals(1, EntryRules.DONE_VALUE)
    }

    @Test fun theFocusSwitchStartsOnlyForMinutesAndCount() {
        assertEquals(true, PracticeRules.defaultAskFocus(Measure.Duration))
        assertEquals(true, PracticeRules.defaultAskFocus(Measure.Count))
        assertEquals(false, PracticeRules.defaultAskFocus(Measure.Done))
        assertEquals(false, PracticeRules.defaultAskFocus(Measure.Percent))
        assertEquals(false, PracticeRules.defaultAskFocus(Measure.Scale))
    }

    @Test fun onlyMinutesAndCountHaveATarget() {
        assertEquals(30, PracticeRules.targetFor(Measure.Duration, 30))
        assertEquals(108, PracticeRules.targetFor(Measure.Count, 108))
        assertNull(PracticeRules.targetFor(Measure.Done, 5))
        assertNull(PracticeRules.targetFor(Measure.Percent, 5))
        assertNull(PracticeRules.targetFor(Measure.Scale, 5))
        assertNull(PracticeRules.problem("A", Measure.Percent, 0, emptyList()))
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
        assertEquals(PracticeProblem.TargetInvalid, PracticeRules.problem("A", Measure.Duration, 0, emptyList()))
        assertNull(PracticeRules.problem("A", Measure.Duration, null, emptyList()))
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
