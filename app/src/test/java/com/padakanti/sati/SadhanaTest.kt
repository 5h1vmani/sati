package com.padakanti.sati

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SadhanaTest {
    private val min = 60_000L
    private val halves = Sadhana("a", "Breath", listOf(Stage(30, "Breathing"), Stage(30, "Silent")))
    private val settled = halves.copy(settleSec = 60)

    @Test fun cuesForTwoHalves() =
        assertEquals(
            listOf(Cue(0, Strike.Start, 0), Cue(30 * min, Strike.StageChange, 1), Cue(60 * min, Strike.End, 1)),
            Session.cuesOf(halves),
        )

    @Test fun settlingDelaysEveryCue() =
        assertEquals(listOf(min, 31 * min, 61 * min), Session.cuesOf(settled).map { it.offsetMs })

    @Test fun oneStageHasStartAndEndOnly() =
        assertEquals(
            listOf(Strike.Start, Strike.End),
            Session.cuesOf(Sadhana("b", "One", listOf(Stage(20)))).map { it.strike },
        )

    @Test fun strikeCounts() = assertEquals(listOf(1, 2, 3), Strike.entries.map { it.times })

    @Test fun dueReturnsTheLatestDueCueOnly() {
        val s = Session(halves, startedAt = 0, nextCue = 1)
        assertNull(s.due(29 * min))
        assertEquals(1, s.due(30 * min))
        // After the phone was off past the end, only the end bell rings.
        assertEquals(2, s.due(90 * min))
    }

    @Test fun dueAllowsAnEarlyAlarm() =
        assertEquals(1, Session(halves, 0, nextCue = 1).due(30 * min - 500))

    @Test fun nothingIsDueWhilePaused() =
        assertNull(Session(halves, 0, pausedAt = 40 * min, nextCue = 1).due(40 * min))

    @Test fun pausesMoveLaterCues() {
        val s = Session(halves, startedAt = 0, pausedMs = 5 * min, nextCue = 1)
        assertEquals(35 * min, s.nextTime())
        assertEquals(30 * min, s.elapsed(35 * min))
    }

    @Test fun elapsedStopsWhilePaused() =
        assertEquals(10 * min, Session(halves, 0, pausedAt = 10 * min).elapsed(50 * min))

    @Test fun positionWhileSettling() {
        val p = Session(settled, 0).position(20_000)
        assertTrue(p.settling)
        assertEquals(40_000, p.toNextBellMs)
        assertEquals(61 * min - 20_000, p.totalLeftMs)
    }

    @Test fun positionInSecondStage() {
        val p = Session(halves, 0).position(45 * min)
        assertFalse(p.settling)
        assertEquals(1, p.stage)
        assertEquals(15 * min, p.toNextBellMs)
        assertEquals(15 * min, p.totalLeftMs)
        assertEquals(0.75f, p.progress, 0.0001f)
    }

    @Test fun positionAfterTheEndStaysAtTheEnd() {
        val p = Session(halves, 0).position(70 * min)
        assertEquals(1, p.stage)
        assertEquals(0, p.totalLeftMs)
        assertEquals(1f, p.progress, 0.0001f)
    }

    @Test fun satLeavesOutSettlingAndPauses() {
        assertEquals(0, Session(settled, 0).satMs(30_000))
        assertEquals(20 * min, Session(settled, 0, pausedMs = 4 * min).satMs(25 * min))
        assertEquals(60 * min, Session(halves, 0).satMs(99 * min))
    }

    @Test fun splitEvenly() {
        assertEquals(listOf(Stage(20), Stage(20), Stage(20)), Sadhana.splitEvenly(60, 3))
        assertEquals(listOf(Stage(15), Stage(15), Stage(14), Stage(14)), Sadhana.splitEvenly(58, 4))
        assertNull(Sadhana.splitEvenly(3, 4))
        assertNull(Sadhana.splitEvenly(10, 0))
    }

    @Test fun problems() {
        assertNull(Sadhana.problem(halves))
        assertEquals("Give the sadhana a name.", Sadhana.problem(halves.copy(name = " ")))
        assertEquals("Add at least one stage.", Sadhana.problem(halves.copy(stages = emptyList())))
        assertEquals("Each stage needs at least 1 minute.", Sadhana.problem(halves.copy(stages = listOf(Stage(0)))))
        assertEquals("A stage can be up to 240 minutes.", Sadhana.problem(halves.copy(stages = listOf(Stage(241)))))
        assertEquals("A sadhana can have up to 10 stages.", Sadhana.problem(halves.copy(stages = List(11) { Stage(1) })))
        assertEquals("Choose a settling time from the list.", Sadhana.problem(halves.copy(settleSec = 45)))
        assertEquals(null, Sadhana.problem(halves.copy(settleSec = 10)))
    }

    @Test fun startersAreValid() = Sadhana.STARTERS.forEach { assertNull(Sadhana.problem(it)) }
}
