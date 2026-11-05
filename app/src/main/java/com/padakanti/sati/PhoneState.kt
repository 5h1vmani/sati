package com.padakanti.sati

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager

/** An audio channel: what Android calls the sound, and which volume slider controls it. */
enum class Channel(val usage: Int, val stream: Int) {
    /** The hourly bell. Follows the notification volume and stays quiet in Do Not Disturb. */
    Notification(AudioAttributes.USAGE_NOTIFICATION_EVENT, AudioManager.STREAM_NOTIFICATION),

    /** Sadhana bells. Follow the alarm volume and ring through Do Not Disturb and silent mode. */
    Alarm(AudioAttributes.USAGE_ALARM, AudioManager.STREAM_ALARM),

    /** Sadhana bells when Do Not Disturb blocks alarms but allows media. Follow the media volume. */
    Media(AudioAttributes.USAGE_MEDIA, AudioManager.STREAM_MUSIC),
}

/** Reads the phone's sound state. The decisions on it live in [BellAction] and [SadhanaBell]. */
object PhoneState {
    /** True while Do Not Disturb is on, in any of its modes. */
    fun isDoNotDisturb(context: Context): Boolean {
        val filter = notifications(context).currentInterruptionFilter
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    fun isRingerSilent(context: Context): Boolean =
        audio(context).ringerMode != AudioManager.RINGER_MODE_NORMAL

    /** True while a call rings or is active. A bell in the ear during a call is worse than none. */
    fun isInCall(context: Context): Boolean =
        audio(context).mode in setOf(
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION,
            AudioManager.MODE_CALL_SCREENING,
        )

    fun isVolumeZero(context: Context, channel: Channel): Boolean =
        audio(context).getStreamVolume(channel.stream) == 0

    /**
     * True when Do Not Disturb would silence alarms: total silence, or
     * priority mode with alarms turned off. Reads the default policy;
     * a custom schedule with its own rules may differ.
     */
    fun alarmsBlocked(context: Context): Boolean = blocks(context, NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS)

    /** True when Do Not Disturb silences the sounds in [category]. "Alarms only" mode allows alarms and media. */
    private fun blocks(context: Context, category: Int): Boolean {
        val nm = notifications(context)
        return when (nm.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_NONE -> true
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> runCatching {
                nm.notificationPolicy.priorityCategories and category == 0
            }.getOrDefault(false)
            else -> false
        }
    }

    /** True when Do Not Disturb would silence media: total silence, or priority mode with media turned off. */
    fun mediaBlocked(context: Context): Boolean = blocks(context, NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA)

    /** What the hourly bell does now. */
    fun bellAction(context: Context): BellAction = BellAction.of(
        doNotDisturb = isDoNotDisturb(context),
        ringerSilent = isRingerSilent(context),
        inCall = isInCall(context),
        volumeZero = isVolumeZero(context, Channel.Notification),
    )

    /** How a sadhana bell sounds now. */
    fun sadhanaBell(context: Context, vibrateOnly: Boolean): SadhanaBell = SadhanaBell.of(
        vibrateOnly = vibrateOnly,
        inCall = isInCall(context),
        alarmsBlocked = alarmsBlocked(context),
        alarmVolumeZero = isVolumeZero(context, Channel.Alarm),
        mediaBlocked = mediaBlocked(context),
        mediaVolumeZero = isVolumeZero(context, Channel.Media),
    )

    private fun audio(context: Context) = context.getSystemService(AudioManager::class.java)
    private fun notifications(context: Context) = context.getSystemService(NotificationManager::class.java)
}
