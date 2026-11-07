package com.padakanti.sati

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime

/** Receives the alarm, rings the bell, and sets the next alarm. */
class BellReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store(context)
        val fired = maxOf(store.nextRing, System.currentTimeMillis())
        store.lastRing = fired
        // A bell delivered very late, for example after the phone was off,
        // must not ring outside waking hours.
        // The hourly bell stays quiet while a sadhana runs.
        val ring = store.enabled && store.session == null && Scheduler.shouldRing(ZonedDateTime.now(), store.plan())

        // Set the next bell first, so one failed ring cannot stop the chain.
        Alarms.reschedule(context, notBefore = fired)

        if (!ring) return
        val pending = goAsync()
        BellService.ringBell(context.applicationContext) { pending.finish() }
    }
}
