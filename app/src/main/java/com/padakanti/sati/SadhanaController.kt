package com.padakanti.sati

import android.content.Context

/**
 * The one place that starts, pauses, resumes and ends a sadhana. The screen,
 * the notification buttons and the alarm all call it. Each change saves the
 * session, sets the next sadhana alarm and updates the notification.
 */
object SadhanaController {
    /** After a restart, a sadhana whose end bell is this late ends without ringing. */
    private const val LATE_END_MS = 2 * 60_000L

    fun start(context: Context, sadhana: Sadhana) {
        val store = Store(context)
        store.summary = null
        var session = Session(sadhana, System.currentTimeMillis())
        // With no settling time, the start bell rings at once instead of through an alarm.
        if (sadhana.settleSec == 0) {
            Ringer.ringSadhana(context.applicationContext, Strike.Start, sadhana.vibrateOnly) {}
            session = session.copy(nextCue = 1)
        }
        store.session = session
        schedule(context, session)
        // The hourly bell is muted while a sadhana runs.
        Alarms.refreshTile(context)
    }

    fun pause(context: Context) {
        val store = Store(context)
        val s = store.session ?: return
        if (s.paused) return
        val paused = s.copy(pausedAt = System.currentTimeMillis())
        store.session = paused
        Alarms.cancel(context, Alarms.Slot.Sadhana)
        Notifier.show(context, paused)
    }

    fun resume(context: Context) {
        val store = Store(context)
        val s = store.session ?: return
        if (!s.paused) return
        val now = System.currentTimeMillis()
        val resumed = s.copy(pausedMs = s.pausedMs + (now - s.pausedAt), pausedAt = 0)
        store.session = resumed
        schedule(context, resumed)
    }

    fun stop(context: Context) {
        val s = Store(context).session ?: return
        finish(context, s, completed = false)
    }

    /** The sadhana alarm fired. Rings the due bell and sets the next one. [done] runs when the bell ends. */
    fun onAlarm(context: Context, done: () -> Unit) {
        val store = Store(context)
        val s = store.session ?: return done()
        val index = s.due(System.currentTimeMillis())
        if (index == null) {
            // Early, or paused since the alarm was set: set it again for the right time.
            if (!s.paused) schedule(context, s)
            return done()
        }
        val cue = s.cues[index]
        val next = s.copy(nextCue = index + 1)
        if (cue.strike == Strike.End) {
            finish(context, next, completed = true)
        } else {
            store.session = next
            schedule(context, next)
        }
        Ringer.ringSadhana(context, cue.strike, s.sadhana.vibrateOnly, done)
    }

    /**
     * After a restart, an app update or a clock change: sets the next bell
     * again. A sadhana whose time ran out while the phone was off ends quietly.
     */
    fun rearm(context: Context) {
        val s = Store(context).session ?: return
        if (s.paused) return Notifier.show(context, s)
        if (System.currentTimeMillis() > s.timeOf(s.cues.lastIndex) + LATE_END_MS) {
            finish(context, s, completed = true)
        } else {
            schedule(context, s)
        }
    }

    private fun schedule(context: Context, s: Session) {
        s.nextTime()?.let { Alarms.setAlarmClock(context, it) }
        Notifier.show(context, s)
    }

    private fun finish(context: Context, s: Session, completed: Boolean) {
        val store = Store(context)
        store.summary = Summary(s.sadhana.name, s.satMs(System.currentTimeMillis()), completed)
        store.session = null
        Alarms.cancel(context, Alarms.Slot.Sadhana)
        Notifier.cancel(context)
        Alarms.refreshTile(context)
    }
}
