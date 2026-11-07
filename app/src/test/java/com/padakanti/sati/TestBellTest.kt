package com.padakanti.sati

import org.junit.Assert.assertEquals
import org.junit.Test

class TestBellTest {
    private val set = 1_000_000L

    @Test
    fun noTestYet() {
        assertEquals(TestBell.None, TestBell.of(0, 0, null, set))
    }

    @Test
    fun waitingUntilItFires() {
        assertEquals(TestBell.Waiting(set), TestBell.of(set, 0, null, set - 5_000))
        assertEquals(TestBell.Waiting(set), TestBell.of(set, 0, null, set + 59_999))
    }

    @Test
    fun firedShowsWhatItDid() {
        assertEquals(TestBell.Done(set + 300, TestOutcome.Vibrated), TestBell.of(set, set + 300, TestOutcome.Vibrated, set + 1_000))
    }

    @Test
    fun anAlarmMoreThanAMinuteLateIsMissed() {
        assertEquals(TestBell.Missed(set), TestBell.of(set, 0, null, set + 60_000))
    }

    @Test
    fun anEarlierTestDoesNotCountForANewOne() {
        val earlier = set - 120_000
        assertEquals(TestBell.Waiting(set), TestBell.of(set, earlier, TestOutcome.Rang, set - 2_000))
        assertEquals(TestBell.Missed(set), TestBell.of(set, earlier, TestOutcome.Rang, set + 60_000))
    }

    @Test
    fun outcomeFollowsTheBellDecision() {
        assertEquals(TestOutcome.Rang, TestOutcome.of(BellAction.Play, doNotDisturb = false))
        assertEquals(TestOutcome.Vibrated, TestOutcome.of(BellAction.Vibrate, doNotDisturb = false))
        assertEquals(TestOutcome.QuietDoNotDisturb, TestOutcome.of(BellAction.Quiet, doNotDisturb = true))
        assertEquals(TestOutcome.QuietCall, TestOutcome.of(BellAction.Quiet, doNotDisturb = false))
    }
}
