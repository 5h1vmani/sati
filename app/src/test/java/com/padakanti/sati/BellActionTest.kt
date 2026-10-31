package com.padakanti.sati

import org.junit.Assert.assertEquals
import org.junit.Test

class BellActionTest {
    private fun of(dnd: Boolean = false, silent: Boolean = false, call: Boolean = false, zero: Boolean = false) =
        BellAction.of(doNotDisturb = dnd, ringerSilent = silent, inCall = call, volumeZero = zero)

    @Test fun normalPhonePlays() = assertEquals(BellAction.Play, of())

    @Test fun doNotDisturbIsQuiet() = assertEquals(BellAction.Quiet, of(dnd = true))

    @Test fun doNotDisturbWinsOverEverything() =
        assertEquals(BellAction.Quiet, of(dnd = true, silent = true, call = true, zero = true))

    @Test fun silentVibrates() = assertEquals(BellAction.Vibrate, of(silent = true))

    @Test fun callVibrates() = assertEquals(BellAction.Vibrate, of(call = true))

    @Test fun zeroVolumeVibrates() = assertEquals(BellAction.Vibrate, of(zero = true))
}
