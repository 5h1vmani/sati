package com.padakanti.sati

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import java.text.SimpleDateFormat
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale

/** One short line for the Quick Settings tile. */
object Status {
    fun line(context: Context, store: Store): String =
        when (val home = HomeState.of(store.enabled, store.pausedUntil, store.nextRing, ZonedDateTime.now(), store.plan())) {
            HomeState.Off -> "Off"
            is HomeState.Paused -> "Paused until ${time(context, home.until)}"
            is HomeState.On -> if (home.next > 0) "Next ${time(context, home.next)}" else "On"
        }

    /** The time alone for today, or with a short day name for a later day. */
    private fun time(context: Context, millis: Long): String {
        val clock = DateFormat.getTimeFormat(context).format(Date(millis))
        if (DateUtils.isToday(millis)) return clock
        val day = SimpleDateFormat("EEE", Locale.getDefault()).format(Date(millis))
        return "$day $clock"
    }
}
