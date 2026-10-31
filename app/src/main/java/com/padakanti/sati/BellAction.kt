package com.padakanti.sati

/** What the hourly bell does when it fires. Pure logic, no Android code, so it is unit tested. */
enum class BellAction {
    /** No sound and no vibration. */
    Quiet,
    Vibrate,
    Play;

    companion object {
        /**
         * Do Not Disturb wins over everything. Silent or vibrate mode, a call,
         * or a notification volume of zero would leave the bell unheard, so it
         * vibrates instead.
         */
        fun of(doNotDisturb: Boolean, ringerSilent: Boolean, inCall: Boolean, volumeZero: Boolean): BellAction = when {
            doNotDisturb -> Quiet
            ringerSilent || inCall || volumeZero -> Vibrate
            else -> Play
        }
    }
}
