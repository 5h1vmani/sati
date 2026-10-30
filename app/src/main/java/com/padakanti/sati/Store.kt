package com.padakanti.sati

import android.content.Context
import android.content.SharedPreferences

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

    var weekdayStart: Int
        get() = prefs.getInt("weekdayStart", 7 * 60)
        set(v) = prefs.edit().putInt("weekdayStart", v).apply()

    var weekdayEnd: Int
        get() = prefs.getInt("weekdayEnd", 22 * 60)
        set(v) = prefs.edit().putInt("weekdayEnd", v).apply()

    var weekendStart: Int
        get() = prefs.getInt("weekendStart", 8 * 60)
        set(v) = prefs.edit().putInt("weekendStart", v).apply()

    var weekendEnd: Int
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

    /** Bell volume, 5 to 100 percent of the notification volume. */
    var volume: Int
        get() = prefs.getInt("volume", 70)
        set(v) = prefs.edit().putInt("volume", v.coerceIn(MIN_VOLUME, 100)).apply()

    /** True once the app has asked for background running on first turn-on. */
    var askedSetup: Boolean
        get() = prefs.getBoolean("askedSetup", false)
        set(v) = prefs.edit().putBoolean("askedSetup", v).apply()

    fun plan() = Plan(
        intervalMin = intervalMin,
        random = random,
        weekday = Hours(weekdayStart, weekdayEnd),
        weekend = Hours(weekendStart, weekendEnd),
        pausedUntilMillis = pausedUntil,
    )

    companion object {
        const val MIN_VOLUME = 5
    }
}
