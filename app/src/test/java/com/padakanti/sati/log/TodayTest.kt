package com.padakanti.sati.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayTest {
    private val aum = Practice("p1", "AUM Chanting", Measure.Count, 108, 0, archived = false, position = 0)
    private val yoga = Practice("p2", "Hatha Yoga", Measure.Done, null, 0, archived = false, position = 1)
    private val old = Practice("p3", "Old", Measure.Minutes, 10, 0, archived = true, position = 2)
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
        assertEquals(0, row(energy, entry("a", energy, 0, null)).rating)
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

    @Test fun anySingleEntryOfARatingPracticeIsDoneEvenAtZeroPercent() {
        assertTrue(row(energy, entry("a", energy, 0, null)).done)
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
}
