package com.padakanti.sati

import android.content.Context

/**
 * The one place that changes the hourly bell's settings. The screen and the
 * Quick Settings tile both call it, so they always do the same thing.
 * Every change sets the next alarm again.
 */
object BellController {
    fun setEnabled(context: Context, on: Boolean) {
        val store = Store(context)
        store.enabled = on
        // Turning the bell on again also ends any pause.
        if (on) store.pausedUntil = 0
        Alarms.reschedule(context)
    }

    /** Sets the test bell to ring in [TestBell.DELAY_MS]. The hourly bell's next alarm does not move. */
    fun testBell(context: Context) {
        val store = Store(context)
        val at = System.currentTimeMillis() + TestBell.DELAY_MS
        store.testOutcome = null
        store.testFiredAt = 0
        store.testSetAt = at
        Alarms.setTest(context, at)
    }

    fun pause(context: Context, hours: Int) {
        Store(context).pausedUntil = System.currentTimeMillis() + hours * 3_600_000L
        Alarms.reschedule(context)
    }

    fun resume(context: Context) {
        Store(context).pausedUntil = 0
        Alarms.reschedule(context)
    }

    fun setRandom(context: Context, random: Boolean) {
        val store = Store(context)
        if (store.random == random) return
        store.random = random
        Alarms.reschedule(context)
    }

    fun setInterval(context: Context, minutes: Int) {
        val store = Store(context)
        if (store.intervalMin == minutes) return
        store.intervalMin = minutes
        Alarms.reschedule(context)
    }

    /** Sets one waking-hour time. Returns false, and changes nothing, when the start would not be before the end. */
    fun setHour(context: Context, field: HourField, minute: Int): Boolean {
        val store = Store(context)
        val current = if (field.weekend) store.weekend else store.weekday
        val updated = field.apply(current, minute) ?: return false
        if (field.weekend) store.weekend = updated else store.weekday = updated
        Alarms.reschedule(context)
        return true
    }
}
