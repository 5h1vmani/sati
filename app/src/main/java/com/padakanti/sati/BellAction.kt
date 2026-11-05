package com.padakanti.sati

/** What a bell does when it fires. Pure logic, no Android code, so it is unit tested. */
enum class BellAction {
    /** No sound and no vibration. */
    Quiet,
    Vibrate,
    Play;

    companion object {
        /**
         * The hourly bell. Do Not Disturb and phone calls silence it fully.
         * Silent or vibrate mode, or a notification volume of zero, would
         * leave it unheard, so it vibrates instead.
         */
        fun of(doNotDisturb: Boolean, ringerSilent: Boolean, inCall: Boolean, volumeZero: Boolean): BellAction = when {
            doNotDisturb || inCall -> Quiet
            ringerSilent || volumeZero -> Vibrate
            else -> Play
        }
    }
}

/**
 * How a sadhana bell sounds. It is never quiet: the user started the
 * sadhana and is waiting for it. Pure logic, so it is unit tested.
 */
enum class SadhanaBell {
    /** The alarm channel. Rings through Do Not Disturb and silent mode, like the Clock app's timer. */
    Alarm,

    /** The media channel, for a Do Not Disturb that blocks alarms but allows media. */
    Media,
    Vibrate;

    companion object {
        /**
         * The alarm channel when Do Not Disturb allows it. A zero alarm volume
         * means the user wants it silent, so the bell vibrates. When Do Not
         * Disturb blocks alarms but allows media, the bell plays as media. It
         * vibrates when the user asked for that, during a call, or when
         * nothing else can be heard.
         */
        fun of(
            vibrateOnly: Boolean,
            inCall: Boolean,
            alarmsBlocked: Boolean,
            alarmVolumeZero: Boolean,
            mediaBlocked: Boolean,
            mediaVolumeZero: Boolean,
        ): SadhanaBell = when {
            vibrateOnly || inCall -> Vibrate
            !alarmsBlocked -> if (alarmVolumeZero) Vibrate else Alarm
            !mediaBlocked && !mediaVolumeZero -> Media
            else -> Vibrate
        }
    }
}
