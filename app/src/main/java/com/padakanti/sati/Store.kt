package com.padakanti.sati

import android.content.Context
import android.content.SharedPreferences
import java.time.ZonedDateTime

/**
 * The app's settings, kept in SharedPreferences in device-protected storage.
 * That storage is readable before the first unlock after a restart, so the
 * bell keeps ringing even when the phone restarts and stays locked.
 */
class Store(context: Context) {
    val prefs: SharedPreferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("bell", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    var intervalMin: Int
        get() = prefs.getInt("intervalMin", 60)
        set(v) = prefs.edit().putInt("intervalMin", v).apply()

    var random: Boolean
        get() = prefs.getBoolean("random", true)
        set(v) = prefs.edit().putBoolean("random", v).apply()

    private var weekdayStart: Int
        get() = prefs.getInt("weekdayStart", 7 * 60)
        set(v) = prefs.edit().putInt("weekdayStart", v).apply()

    private var weekdayEnd: Int
        get() = prefs.getInt("weekdayEnd", 22 * 60)
        set(v) = prefs.edit().putInt("weekdayEnd", v).apply()

    private var weekendStart: Int
        get() = prefs.getInt("weekendStart", 8 * 60)
        set(v) = prefs.edit().putInt("weekendStart", v).apply()

    private var weekendEnd: Int
        get() = prefs.getInt("weekendEnd", 22 * 60)
        set(v) = prefs.edit().putInt("weekendEnd", v).apply()

    /** Epoch milliseconds. 0 means not paused. */
    var pausedUntil: Long
        get() = prefs.getLong("pausedUntil", 0)
        set(v) = prefs.edit().putLong("pausedUntil", v).apply()

    /** Epoch milliseconds of the scheduled bell. 0 means none. */
    var nextRing: Long
        get() = prefs.getLong("nextRing", 0)
        set(v) = prefs.edit().putLong("nextRing", v).apply()

    /** Epoch milliseconds of the last bell slot that fired. Guards against a repeat. */
    var lastRing: Long
        get() = prefs.getLong("lastRing", 0)
        set(v) = prefs.edit().putLong("lastRing", v).apply()

    /** Display name of the chosen sound. Null means the built-in bell. */
    var soundName: String?
        get() = prefs.getString("soundName", null)
        set(v) = prefs.edit().putString("soundName", v).apply()

    /** Hourly bell volume, in percent of the notification volume. */
    var volume: Int
        get() = prefs.getInt("volume", DEFAULT_VOLUME)
        set(v) = prefs.edit().putInt("volume", v.coerceIn(MIN_VOLUME, MAX_VOLUME)).apply()

    /** Sadhana bell volume, in percent of the alarm volume. */
    var sadhanaVolume: Int
        get() = prefs.getInt("sadhanaVolume", DEFAULT_VOLUME)
        set(v) = prefs.edit().putInt("sadhanaVolume", v.coerceIn(MIN_VOLUME, MAX_VOLUME)).apply()

    /** Saved sadhanas. The starters appear until the user saves their own list. */
    var sadhanas: List<Sadhana>
        get() = prefs.getString("sadhanas", null)?.let(SadhanaJson::decodeList) ?: Sadhana.STARTERS
        set(v) = prefs.edit().putString("sadhanas", SadhanaJson.encodeList(v)).apply()

    /**
     * Saves the sadhanas and waits until they are on disk. Slow, so call it
     * off the main thread. The import uses it, so the sadhanas it merged are
     * not lost if the process ends right after.
     */
    fun saveSadhanasNow(v: List<Sadhana>) {
        prefs.edit().putString("sadhanas", SadhanaJson.encodeList(v)).commit()
    }

    /** The sadhana in progress, or null. */
    var session: Session?
        get() = prefs.getString("session", null)?.let(SadhanaJson::decodeSession)
        set(v) = prefs.edit().putString("session", v?.let(SadhanaJson::encodeSession)).apply()

    /** The last sadhana that ended, until the user dismisses it. */
    var summary: Summary?
        get() = prefs.getString("summary", null)?.let(SadhanaJson::decodeSummary)
        set(v) = prefs.edit().putString("summary", v?.let(SadhanaJson::encodeSummary)).apply()

    /** Epoch milliseconds of the last practice log export. 0 means never. */
    var lastLogExport: Long
        get() = prefs.getLong("lastLogExport", 0)
        set(v) = prefs.edit().putLong("lastLogExport", v).apply()

    /** True once the app has asked to show notifications, on the first sadhana. */
    var askedNotifications: Boolean
        get() = prefs.getBoolean("askedNotifications", false)
        set(v) = prefs.edit().putBoolean("askedNotifications", v).apply()

    /** True once the app has asked for background running on first turn-on. */
    var askedSetup: Boolean
        get() = prefs.getBoolean("askedSetup", false)
        set(v) = prefs.edit().putBoolean("askedSetup", v).apply()

    var weekday: Hours
        get() = Hours(weekdayStart, weekdayEnd)
        set(v) {
            weekdayStart = v.startMin
            weekdayEnd = v.endMin
        }

    var weekend: Hours
        get() = Hours(weekendStart, weekendEnd)
        set(v) {
            weekendStart = v.startMin
            weekendEnd = v.endMin
        }

    /** What the screen and the tile show for the hourly bell. */
    fun homeState(now: ZonedDateTime): HomeState =
        HomeState.of(enabled, pausedUntil, nextRing, now, plan(), sadhanaRunning = session != null)

    fun plan() = Plan(
        intervalMin = intervalMin,
        random = random,
        weekday = weekday,
        weekend = weekend,
        pausedUntilMillis = pausedUntil,
    )

    companion object {
        const val MIN_VOLUME = 5
        const val MAX_VOLUME = 100
        const val DEFAULT_VOLUME = 70
    }
}
