package com.padakanti.sati.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveRulesTest {
    private val practice = Practice("p1", "Kriya", Measure.Count, 108, 0, archived = false, position = 0)

    @Test fun archivingOpensAPeriodStartingToday() {
        val change = ArchiveRules.change(practice, openPeriod = null, archive = true, day = 100)!!
        assertEquals(practice.copy(archived = true), change.practice)
        assertEquals(ArchivePeriod(practiceId = "p1", fromDay = 100, toDay = null), change.open)
        assertNull(change.close)
    }

    @Test fun returningClosesTheOpenPeriodWithTheReturnDay() {
        val archived = practice.copy(archived = true)
        val open = ArchivePeriod(5, "p1", 100, null)
        val change = ArchiveRules.change(archived, open, archive = false, day = 130)!!
        assertEquals(practice, change.practice)
        assertNull(change.open)
        assertEquals(ArchivePeriod(5, "p1", 100, 130), change.close)
    }

    @Test fun archivingAgainAfterAReturnOpensANewPeriod() {
        // The first period is closed. A second archive opens a second one and leaves the first alone.
        val back = practice
        val change = ArchiveRules.change(back, openPeriod = null, archive = true, day = 200)!!
        assertEquals(200L, change.open!!.fromDay)
        assertNull(change.close)
    }

    @Test fun archivingAnArchivedPracticeChangesNothing() =
        assertNull(ArchiveRules.change(practice.copy(archived = true), ArchivePeriod(1, "p1", 1, null), archive = true, day = 9))

    @Test fun returningAPracticeThatIsActiveChangesNothing() =
        assertNull(ArchiveRules.change(practice, null, archive = false, day = 9))

    @Test fun anArchiveNeverOpensASecondPeriodOverAnOpenOne() {
        val change = ArchiveRules.change(practice, ArchivePeriod(1, "p1", 1, null), archive = true, day = 9)!!
        assertNull(change.open)
    }

    @Test fun measureCanChangeWithNoEntries() = assertNull(PracticeRules.measureProblem(Measure.Duration, Measure.Count, 0))

    @Test fun measureIsLockedOnceThereIsAnEntry() =
        assertEquals(PracticeProblem.MeasureLocked, PracticeRules.measureProblem(Measure.Duration, Measure.Count, 1))

    @Test fun keepingTheMeasureIsAlwaysAllowed() = assertNull(PracticeRules.measureProblem(Measure.Duration, Measure.Duration, 50))
}
