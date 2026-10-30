package com.padakanti.sati

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import java.time.Instant
import java.time.ZoneId

/**
 * Keeps exactly one alarm set: the next bell. Every change to the settings,
 * every bell, and every restart calls [reschedule]. Opening the app calls [rearm].
 */
object Alarms {
    /**
     * Cancels the pending bell and picks a new time. [notBefore] is epoch
     * milliseconds of a bell that already fired; see [Scheduler.base].
     */
    fun reschedule(context: Context, notBefore: Long = 0) {
        val store = Store(context)
        if (store.enabled) {
            val base = Scheduler.base(System.currentTimeMillis(), notBefore)
            val now = Instant.ofEpochMilli(base).atZone(ZoneId.systemDefault())
            val at = Scheduler.next(now, store.plan()).toInstant().toEpochMilli()
            set(context, at)
            store.nextRing = at
        } else {
            context.getSystemService(AlarmManager::class.java).cancel(pending(context))
            store.nextRing = 0
        }
        refreshTile(context)
    }

    /**
     * Sets the alarm again at the stored time, without picking a new one.
     * A force-stop, or revoking exact alarms, cancels the alarm but leaves the
     * stored time, so the screen would show a bell that never comes. Setting
     * the same PendingIntent replaces any alarm that is still there.
     */
    fun rearm(context: Context, refresh: Boolean = true) {
        val store = Store(context)
        if (store.enabled && store.nextRing > System.currentTimeMillis()) {
            set(context, store.nextRing)
            if (refresh) refreshTile(context)
        } else {
            reschedule(context)
        }
    }

    private fun set(context: Context, at: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        // Exact alarms wake the phone at the planned minute, even in Doze.
        // Without the permission, Android may deliver the bell a large share
        // of the wait late, and a bell that lands after waking hours is dropped.
        try {
            if (alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
                return
            }
        } catch (e: SecurityException) {
            // The permission was revoked between the check and the call.
            // Fall through to an inexact alarm so the chain does not end.
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
    }

    private fun pending(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, BellReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Asks the Quick Settings tile, if the user added it, to redraw. */
    private fun refreshTile(context: Context) {
        runCatching {
            TileService.requestListeningState(context, ComponentName(context, BellTileService::class.java))
        }
    }
}
