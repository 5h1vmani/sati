package com.padakanti.sati

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Android clears alarms on restart, and a clock or time zone change makes the
 * set alarm wrong. Each of these events sets the next bell again.
 */
class RescheduleReceiver : BroadcastReceiver() {
    private val actions = setOf(
        Intent.ACTION_LOCKED_BOOT_COMPLETED,
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in actions) return
        // Passing the last bell keeps a small clock step backwards from
        // ringing the same bell twice.
        Alarms.reschedule(context, notBefore = Store(context).lastRing)
    }
}
