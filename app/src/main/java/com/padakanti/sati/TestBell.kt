package com.padakanti.sati

/**
 * The test bell rings through the same path as the hourly bell, from an alarm
 * to [BellService], a few seconds after the user asks. It shows what the real
 * bell would do now, and whether Android lets the alarm through at all.
 * Pure logic, no Android code, so it is unit tested.
 */
sealed interface TestBell {
    data object None : TestBell

    /** Set and not yet fired. [at] is epoch milliseconds. */
    data class Waiting(val at: Long) : TestBell

    data class Done(val at: Long, val outcome: TestOutcome) : TestBell

    /** The alarm did not come. Something on the phone stops Sati in the background. */
    data class Missed(val at: Long) : TestBell

    companion object {
        /** The delay after the tap, long enough to lock the phone. */
        const val DELAY_MS = 10_000L

        /** How late the alarm may be before the test counts as missed. */
        const val MISSED_AFTER_MS = 60_000L

        /**
         * [setAt] is when the test bell was set to fire, or 0. [firedAt] is when
         * it last fired, or 0. [outcome] is what it did then.
         */
        fun of(setAt: Long, firedAt: Long, outcome: TestOutcome?, now: Long): TestBell = when {
            setAt == 0L -> None
            // A firing from an earlier test does not count for this one.
            firedAt >= setAt - DELAY_MS && outcome != null -> Done(firedAt, outcome)
            now < setAt + MISSED_AFTER_MS -> Waiting(setAt)
            else -> Missed(setAt)
        }
    }
}

/** What the test bell did when it fired. */
enum class TestOutcome {
    Rang,
    Vibrated,
    QuietDoNotDisturb,
    QuietCall;

    companion object {
        /** [action] is the hourly bell's decision. A quiet bell is quiet for Do Not Disturb or a call. */
        fun of(action: BellAction, doNotDisturb: Boolean): TestOutcome = when (action) {
            BellAction.Play -> Rang
            BellAction.Vibrate -> Vibrated
            BellAction.Quiet -> if (doNotDisturb) QuietDoNotDisturb else QuietCall
        }
    }
}
