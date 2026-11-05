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
}
