package com.padakanti.sati.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTest {
    private fun due(every: Int, start: Long, day: Long) = PracticeRules.isDue(every, start, day)

    @Test fun everyDayIsDueOnEveryDayFromTheStart() {
        assertTrue(due(1, 10, 10))
        assertTrue(due(1, 10, 11))
        assertTrue(due(1, 10, 500))
    }

    @Test fun nothingIsDueBeforeTheStart() {
        assertFalse(due(1, 10, 9))
        assertFalse(due(3, 10, 7))
        assertFalse(due(3, 10, 4))
    }

    @Test fun everyThreeDaysIsDueOnTheStartAndEveryThirdDayAfter() {
        val dueDays = (10L..20L).filter { due(3, 10, it) }
        assertEquals(listOf(10L, 13L, 16L, 19L), dueDays)
    }

    @Test fun everySevenDaysIsWeekly() {
        assertEquals(listOf(10L, 17L, 24L), (10L..30L).filter { due(7, 10, it) })
    }

    @Test fun aStartInTheFutureHasThatDayAsItsNextDue() {
        assertEquals(50L, PracticeRules.nextDue(3, 50, 20))
        assertEquals(50L, PracticeRules.nextDue(3, 50, 50))
        assertEquals(50L, PracticeRules.nextDue(1, 50, 49))
    }

    @Test fun theNextDueOfADueDayIsThatDay() {
        assertEquals(13L, PracticeRules.nextDue(3, 10, 13))
        assertEquals(10L, PracticeRules.nextDue(3, 10, 10))
    }

    @Test fun theNextDueOfADayBetweenDueDaysIsTheFollowingDueDay() {
        assertEquals(13L, PracticeRules.nextDue(3, 10, 11))
        assertEquals(13L, PracticeRules.nextDue(3, 10, 12))
        assertEquals(16L, PracticeRules.nextDue(3, 10, 14))
        assertEquals(17L, PracticeRules.nextDue(7, 10, 11))
    }

    @Test fun aMissedDueDayDoesNotMoveTheSchedule() {
        // Day 13 was due and nothing was logged. The next due day is still 16, not 17.
        assertEquals(16L, PracticeRules.nextDue(3, 10, 14))
        assertFalse(due(3, 10, 14))
        assertTrue(due(3, 10, 16))
    }

    @Test fun everyDayIsAlwaysItsOwnNextDue() {
        assertEquals(77L, PracticeRules.nextDue(1, 10, 77))
    }

    @Test fun theNextDueIsAlwaysDueAndNeverEarlierThanFrom() {
        for (every in 1..7) for (start in listOf(0L, 5L, 40L)) for (from in 0L..60L) {
            val next = PracticeRules.nextDue(every, start, from)
            assertTrue("$every $start $from", next >= from && due(every, start, next))
            assertTrue("$every $start $from", (from until next).none { due(every, start, it) })
        }
    }

    @Test fun aPracticeKnowsItsOwnSchedule() {
        val p = Practice("p", "A", Measure.Done, null, 0, false, 0, everyDays = 2, scheduleStart = 4)
        assertTrue(PracticeRules.isDue(p, 6))
        assertEquals(8L, PracticeRules.nextDue(p, 7))
    }

    @Test fun repeatMustBeOneToSevenDays() {
        assertEquals(PracticeProblem.ScheduleInvalid, PracticeRules.problem("A", Measure.Done, null, emptyList(), everyDays = 0))
        assertEquals(PracticeProblem.ScheduleInvalid, PracticeRules.problem("A", Measure.Done, null, emptyList(), everyDays = 8))
        assertNull(PracticeRules.problem("A", Measure.Done, null, emptyList(), everyDays = 7))
        assertNull(PracticeRules.problem("A", Measure.Done, null, emptyList(), everyDays = 1))
    }

    @Test fun aDurationTargetIsInSecondsUpToOneDay() {
        assertNull(PracticeRules.problem("A", Measure.Duration, 86_400, emptyList()))
        assertEquals(PracticeProblem.TargetInvalid, PracticeRules.problem("A", Measure.Duration, 86_401, emptyList()))
        assertNull(PracticeRules.problem("A", Measure.Count, 86_401, emptyList()))
    }

    // ---------- order ----------

    private fun p(id: String, position: Int) = Practice(id, id, Measure.Done, null, 0, false, position)

    private fun positions(list: List<Practice>) = list.associate { it.id to it.position }

    @Test fun movingUpSwapsWithTheOneAbove() {
        val changed = PracticeRules.moved(listOf(p("a", 0), p("b", 1), p("c", 2)), "b", up = true)
        assertEquals(mapOf("a" to 1, "b" to 0), positions(changed))
    }

    @Test fun movingDownSwapsWithTheOneBelow() {
        val changed = PracticeRules.moved(listOf(p("a", 0), p("b", 1), p("c", 2)), "b", up = false)
        assertEquals(mapOf("b" to 2, "c" to 1), positions(changed))
    }

    @Test fun movingPastAnEndChangesNothing() {
        val list = listOf(p("a", 0), p("b", 1))
        assertEquals(emptyList<Practice>(), PracticeRules.moved(list, "a", up = true))
        assertEquals(emptyList<Practice>(), PracticeRules.moved(list, "b", up = false))
        assertEquals(emptyList<Practice>(), PracticeRules.moved(list, "missing", up = true))
    }

    @Test fun gapsAndEqualPositionsDoNotStopAMove() {
        // After deletes the positions have gaps, and an old import may have left two equal ones.
        val list = listOf(p("a", 3), p("b", 3), p("c", 9))
        val changed = PracticeRules.moved(list, "c", up = true)
        assertEquals(mapOf("a" to 0, "b" to 2, "c" to 1), positions(changed))
    }
}
