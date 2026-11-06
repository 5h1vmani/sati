package com.padakanti.sati.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayTest {
    private val aum = Practice("p1", "AUM Chanting", Measure.Count, 108, 0, archived = false, position = 0)
    private val yoga = Practice("p2", "Hatha Yoga", Measure.Done, null, 0, archived = false, position = 1)
    private val old = Practice("p3", "Old", Measure.Duration, 10, 0, archived = true, position = 2)
    private val mood = Practice("p4", "Mood", Measure.Scale, null, 0, archived = false, position = 3, askFocus = false)
    private val energy = Practice("p5", "Energy", Measure.Percent, null, 0, archived = false, position = 4, askFocus = false)
    private val day = 500L

    private fun entry(id: String, practice: Practice, value: Int, focus: Int?, d: Long = day) =
        Entry(id, practice.id, d, value, focus, null, 0)

    @Test fun listsEveryActivePracticeInOrder() {
        val rows = Today.rows(listOf(aum, yoga, old), emptyList(), day)
        assertEquals(listOf("p1", "p2"), rows.map { it.practice.id })
    }

    @Test fun aPracticeWithNoEntryIsNotDone() {
        val row = Today.rows(listOf(aum), emptyList(), day).single()
        assertEquals(0, row.total)
        assertFalse(row.done)
        assertNull(row.focusAverage)
        assertEquals(108, row.target)
    }

    @Test fun valuesAddUp() {
        val row = Today.rows(listOf(aum), listOf(entry("a", aum, 40, null), entry("b", aum, 40, null)), day).single()
        assertEquals(80, row.total)
    }

    @Test fun anyEntryCountsAsDoneEvenBelowTheTarget() {
        val row = Today.rows(listOf(aum), listOf(entry("a", aum, 80, null)), day).single()
        assertTrue(row.done)
        assertEquals(80, row.total)
        assertEquals(108, row.target)
    }

    @Test fun focusAveragesOnlyTheScoredEntries() {
        val entries = listOf(entry("a", aum, 10, 6), entry("b", aum, 10, null), entry("c", aum, 10, 9))
        assertEquals(7.5, Today.rows(listOf(aum), entries, day).single().focusAverage!!, 0.0001)
    }

    @Test fun entriesOfOtherDaysAndPracticesAreIgnored() {
        val entries = listOf(entry("a", aum, 50, 5, d = day - 1), entry("b", yoga, 1, null))
        val rows = Today.rows(listOf(aum, yoga), entries, day)
        assertEquals(0, rows[0].total)
        assertFalse(rows[0].done)
        assertTrue(rows[1].done)
    }

    @Test fun anArchivedPracticeStaysOffTodayEvenWithAnEntry() =
        assertTrue(Today.rows(listOf(old), listOf(entry("a", old, 5, null)), day).isEmpty())

    private fun row(practice: Practice, vararg entries: Entry) = Today.rows(listOf(practice), entries.toList(), day).single()

    @Test fun aDonePracticeWithNothingLoggedIsNeitherDoneNorNotDone() {
        val r = row(yoga)
        assertFalse(r.logged)
        assertFalse(r.done)
        assertFalse(r.notDone)
    }

    @Test fun aDonePracticeWithAnEntryOfOneIsDone() {
        val r = row(yoga, entry("a", yoga, 1, null))
        assertTrue(r.logged)
        assertTrue(r.done)
        assertFalse(r.notDone)
    }

    @Test fun aDonePracticeWithOnlyAnEntryOfZeroIsNotDone() {
        val r = row(yoga, entry("a", yoga, 0, null))
        assertTrue(r.logged)
        assertFalse(r.done)
        assertTrue(r.notDone)
    }

    @Test fun aDonePracticeWithBothMarksCountsAsDone() {
        val r = row(yoga, entry("a", yoga, 0, null), entry("b", yoga, 1, null))
        assertTrue(r.done)
        assertFalse(r.notDone)
    }

    @Test fun aRatingIsTheAverageNotTheSum() {
        val r = row(mood, entry("a", mood, 7, null), entry("b", mood, 3, null))
        assertEquals(5, r.rating)
        assertEquals(10, r.total)
        assertTrue(r.done)
        assertNull(r.target)
    }

    @Test fun aRatingRoundsHalfUp() {
        assertEquals(8, row(mood, entry("a", mood, 7, null), entry("b", mood, 8, null)).rating)
        assertEquals(7, row(mood, entry("a", mood, 7, null), entry("b", mood, 7, null), entry("c", mood, 8, null)).rating)
        assertEquals(8, row(mood, entry("a", mood, 8, null), entry("b", mood, 8, null), entry("c", mood, 7, null)).rating)
        assertEquals(73, row(energy, entry("a", energy, 72, null), entry("b", energy, 73, null)).rating)
    }

    @Test fun averageRoundedHalfUpHandlesTheEdges() {
        assertEquals(1, Today.averageRoundedHalfUp(listOf(0, 1)))
        assertEquals(0, Today.averageRoundedHalfUp(listOf(0, 0, 1)))
        assertEquals(100, Today.averageRoundedHalfUp(listOf(100, 100, 100)))
        assertEquals(72, Today.averageRoundedHalfUp(listOf(72)))
    }

    @Test fun aRatingIsNullWithNothingLoggedAndForOtherMeasures() {
        assertNull(row(mood).rating)
        assertFalse(row(mood).done)
        assertNull(row(aum, entry("a", aum, 5, null)).rating)
        assertNull(row(yoga, entry("a", yoga, 1, null)).rating)
    }

    @Test fun aRatingPracticeWithOnlyANotDoneEntryIsNotDoneAndHasNoRating() {
        val r = row(energy, entry("a", energy, 0, null))
        assertTrue(r.notDone)
        assertFalse(r.done)
        assertNull(r.rating)
    }

    @Test fun theFocusAverageIsHiddenWhenThePracticeDoesNotAskForFocus() {
        val withFocus = row(energy.copy(askFocus = true), entry("a", energy, 50, 6), entry("b", energy, 60, 8))
        assertEquals(7.0, withFocus.focusAverage!!, 0.0001)
        assertNull(row(energy, entry("a", energy, 50, 6)).focusAverage)
        assertNull(row(aum.copy(askFocus = false), entry("a", aum, 50, 6)).focusAverage)
    }

    @Test fun targetsExistOnlyForMinutesAndCycles() {
        assertEquals(108, row(aum).target)
        assertNull(row(yoga).target)
        assertNull(row(energy.copy(target = 5)).target)
    }

    private val every3 = Practice("p6", "Weekly-ish", Measure.Done, null, 0, archived = false, position = 5, everyDays = 3, scheduleStart = 498)
    private val later = Practice("p7", "Later", Measure.Count, 10, 0, archived = false, position = 6, everyDays = 2, scheduleStart = 499)
    private val soon = Practice("p8", "Soon", Measure.Duration, null, 0, archived = false, position = 7, scheduleStart = 505)

    @Test fun duePracticesComeFirstInOrderAndTheOthersFollowInOrder() {
        // On day 500: aum and yoga are due daily. later is due on 499 and 501, every3 on 498 and 501, and soon starts on 505.
        val rows = Today.rows(listOf(later, aum, soon, every3, yoga), emptyList(), day)
        assertEquals(listOf("p1", "p2", "p7", "p8", "p6"), rows.map { it.practice.id })
        assertEquals(listOf(true, true, false, false, false), rows.map { it.due })
    }

    @Test fun duePracticesKeepTheOrderTheyWereGivenIn() {
        val rows = Today.rows(listOf(yoga, aum), emptyList(), day)
        assertEquals(listOf("p2", "p1"), rows.map { it.practice.id })
    }

    @Test fun aPracticeThatIsNotDueShowsItsNextDueDay() {
        val rows = Today.rows(listOf(every3, later, soon), emptyList(), day)
        assertEquals(listOf(false, false, false), rows.map { it.due })
        assertEquals(mapOf("p6" to 501L, "p7" to 501L, "p8" to 505L), rows.associate { it.practice.id to it.nextDue })
    }

    @Test fun aDuePracticeHasItsOwnDayAsNextDue() {
        assertEquals(day, row(aum).nextDue)
        assertTrue(row(aum).due)
    }

    @Test fun anExtraEntryOnADayThatIsNotDueStillCountsAsDone() {
        val r = row(every3, entry("a", every3, 1, null))
        assertFalse(r.due)
        assertTrue(r.done)
    }

    @Test fun aNotDoneEntryMakesAnyMeasureNotDone() {
        listOf(aum, yoga, mood, energy, soon.copy(scheduleStart = 0)).forEach {
            val r = row(it, entry("a", it, 0, null))
            assertTrue("${it.measure}", r.logged)
            assertFalse("${it.measure}", r.done)
            assertTrue("${it.measure}", r.notDone)
        }
    }

    @Test fun anEntryAboveZeroMakesAnyMeasureDone() {
        listOf(aum, yoga, mood, energy).forEach {
            val r = row(it, entry("a", it, 1, null))
            assertTrue("${it.measure}", r.done)
            assertFalse("${it.measure}", r.notDone)
        }
    }

    @Test fun aNotDoneEntryCountsAsZeroInTheRatingAverage() {
        assertEquals(4, row(mood, entry("a", mood, 8, null), entry("b", mood, 0, null)).rating)
        assertEquals(50, row(energy, entry("a", energy, 100, null), entry("b", energy, 0, null)).rating)
        assertEquals(1, row(energy, entry("a", energy, 1, null), entry("b", energy, 0, null)).rating)
    }

    @Test fun theLatestEntryIsTheNewestOfTheDay() {
        val older = Entry("a", aum.id, day, 5, null, null, 100)
        val newer = Entry("b", aum.id, day, 6, null, null, 200)
        assertEquals("b", row(aum, older, newer).latest!!.id)
        // The order of the list must not matter.
        assertEquals("b", row(aum, newer, older).latest!!.id)
        assertNull(row(aum).latest)
    }

    // ---------- summary, sections, ring ----------

    private fun rowsFor(practices: List<Practice>, vararg entries: Entry) = Today.rows(practices, entries.toList(), day)

    @Test fun theSummaryCountsOnlyPracticesDueToday() {
        // aum and yoga are due, every3 is not. The entry of the practice that is not due is not counted.
        val rows = rowsFor(listOf(aum, yoga, every3), entry("a", every3, 1, null), entry("b", aum, 5, null))
        assertEquals(DaySummary(due = 2, done = 1, notDone = 0), Today.summary(rows))
        assertEquals(1, Today.summary(rows).left)
    }

    @Test fun theSummaryCountsDoneNotDoneAndLeft() {
        val rows = rowsFor(
            listOf(aum, yoga, mood, energy),
            entry("a", aum, 5, null),
            entry("b", yoga, 0, null),
            entry("c", mood, 0, null),
        )
        val summary = Today.summary(rows)
        assertEquals(DaySummary(due = 4, done = 1, notDone = 2), summary)
        assertEquals(1, summary.left)
        assertFalse(summary.allLogged)
        assertFalse(summary.nothingDue)
    }

    @Test fun aDayWithBothMarksOnOnePracticeCountsAsDoneOnly() {
        val summary = Today.summary(rowsFor(listOf(yoga), entry("a", yoga, 0, null), entry("b", yoga, 1, null)))
        assertEquals(DaySummary(1, 1, 0), summary)
    }

    @Test fun everyDuePracticeLoggedMeansAllLogged() {
        val summary = Today.summary(rowsFor(listOf(aum, yoga), entry("a", aum, 5, null), entry("b", yoga, 0, null)))
        assertEquals(DaySummary(2, 1, 1), summary)
        assertTrue(summary.allLogged)
        assertEquals(0, summary.left)
    }

    @Test fun withNothingDueTheSummaryIsEmpty() {
        val summary = Today.summary(rowsFor(listOf(every3, soon)))
        assertEquals(DaySummary(0, 0, 0), summary)
        assertTrue(summary.nothingDue)
        assertFalse(summary.allLogged)
        assertEquals(DaySummary(0, 0, 0), Today.summary(emptyList()))
    }

    @Test fun sectionsAreToDoLoggedAndNotDue() {
        val rows = rowsFor(
            listOf(aum, yoga, mood, energy, every3),
            entry("a", aum, 5, null),
            entry("b", mood, 0, null),
            entry("c", every3, 1, null),
        )
        val sections = Today.sections(rows)
        assertEquals(listOf("p2", "p5"), sections.todo.map { it.practice.id })
        assertEquals(listOf("p1", "p4"), sections.logged.map { it.practice.id })
        assertEquals(listOf("p6"), sections.notDue.map { it.practice.id })
    }

    @Test fun aNotDueRowStaysInItsSectionEvenWhenLogged() {
        val sections = Today.sections(rowsFor(listOf(every3), entry("a", every3, 1, null)))
        assertEquals(emptyList<TodayRow>(), sections.todo)
        assertEquals(emptyList<TodayRow>(), sections.logged)
        assertEquals(1, sections.notDue.size)
    }

    @Test fun eachSectionKeepsPositionOrder() {
        val first = aum.copy(position = 7)
        val second = yoga.copy(position = 2)
        val third = mood.copy(position = 4)
        val sections = Today.sections(rowsFor(listOf(first, second, third)))
        assertEquals(listOf("p2", "p4", "p1"), sections.todo.map { it.practice.id })
    }

    @Test fun anEmptySectionHasNoRows() {
        val sections = Today.sections(rowsFor(listOf(aum), entry("a", aum, 5, null)))
        assertEquals(emptyList<TodayRow>(), sections.todo)
        assertEquals(emptyList<TodayRow>(), sections.notDue)
        assertEquals(1, sections.logged.size)
    }

    @Test fun theStatusIsEmptyWithNothingLogged() {
        assertEquals(RowStatus.Empty, row(aum).status)
        assertEquals(0f, row(aum).fraction, 0f)
    }

    @Test fun belowTheTargetTheRingIsPartialAndFilledByTotalOverTarget() {
        val r = row(aum, entry("a", aum, 27, null))
        assertEquals(RowStatus.Partial, r.status)
        assertEquals(0.25f, r.fraction, 0.0001f)
    }

    @Test fun atOrAboveTheTargetTheStatusIsDoneAndTheRingIsFull() {
        assertEquals(RowStatus.Done, row(aum, entry("a", aum, 108, null)).status)
        assertEquals(RowStatus.Done, row(aum, entry("a", aum, 200, null)).status)
        assertEquals(1f, row(aum, entry("a", aum, 200, null)).fraction, 0f)
    }

    @Test fun withoutATargetAnyEntryAboveZeroIsDone() {
        assertEquals(RowStatus.Done, row(mood, entry("a", mood, 3, null)).status)
        assertEquals(RowStatus.Done, row(yoga, entry("a", yoga, 1, null)).status)
        assertEquals(0f, row(yoga, entry("a", yoga, 1, null)).fraction, 0f)
    }

    @Test fun onlyNotDoneEntriesGiveTheRedCross() {
        assertEquals(RowStatus.NotDone, row(aum, entry("a", aum, 0, null)).status)
        assertEquals(RowStatus.NotDone, row(yoga, entry("a", yoga, 0, null)).status)
    }

    @Test fun nextDueIsTomorrowForTheNextDay() {
        assertEquals(NextDueWording.Tomorrow, Today.nextDueWording(501, 500))
    }

    @Test fun nextDueIsAWeekdayForTheSecondToSixthDayAhead() {
        (502L..506L).forEach { assertEquals("$it", NextDueWording.Weekday, Today.nextDueWording(it, 500)) }
    }

    @Test fun nextDueIsADateFromTheSeventhDayAhead() {
        assertEquals(NextDueWording.Date, Today.nextDueWording(507, 500))
        assertEquals(NextDueWording.Date, Today.nextDueWording(900, 500))
    }
}
