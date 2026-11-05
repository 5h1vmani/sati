package com.padakanti.sati.log

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The export is the only backup, so the Log tab asks for a new one now and then. */
object ExportReminder {
    const val ASK_AFTER_DAYS = 30

    /** Calendar days from the last export to [now], or null when there was no export. [lastExport] 0 means none. */
    fun daysSince(lastExport: Long, now: Long, zone: ZoneId): Int? {
        if (lastExport <= 0) return null
        val from = Instant.ofEpochMilli(lastExport).atZone(zone).toLocalDate()
        val to = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(from, to).toInt().coerceAtLeast(0)
    }

    /** True when there is something to lose and no export yet, or the last export is over 30 days old. */
    fun shouldAsk(lastExport: Long, now: Long, zone: ZoneId, hasEntries: Boolean): Boolean {
        val days = daysSince(lastExport, now, zone)
        return if (days == null) hasEntries else days > ASK_AFTER_DAYS
    }
}
