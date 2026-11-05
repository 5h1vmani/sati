package com.padakanti.sati

import org.junit.Assert.assertEquals
import org.junit.Test

class SadhanaBellTest {
    private fun of(
        vibrate: Boolean = false,
        call: Boolean = false,
        alarmsBlocked: Boolean = false,
        alarmZero: Boolean = false,
        mediaBlocked: Boolean = false,
        mediaZero: Boolean = false,
    ) = SadhanaBell.of(vibrate, call, alarmsBlocked, alarmZero, mediaBlocked, mediaZero)

    @Test fun alarmByDefault() = assertEquals(SadhanaBell.Alarm, of())

    @Test fun vibrateOnlyWins() = assertEquals(SadhanaBell.Vibrate, of(vibrate = true))

    @Test fun callVibrates() = assertEquals(SadhanaBell.Vibrate, of(call = true))

    @Test fun zeroAlarmVolumeVibrates() = assertEquals(SadhanaBell.Vibrate, of(alarmZero = true))

    @Test fun blockedAlarmsFallBackToMedia() = assertEquals(SadhanaBell.Media, of(alarmsBlocked = true))

    @Test fun blockedAlarmsIgnoreTheAlarmVolume() =
        assertEquals(SadhanaBell.Media, of(alarmsBlocked = true, alarmZero = true))

    @Test fun blockedAlarmsAndMediaVibrate() =
        assertEquals(SadhanaBell.Vibrate, of(alarmsBlocked = true, mediaBlocked = true))

    @Test fun blockedAlarmsAndZeroMediaVibrate() =
        assertEquals(SadhanaBell.Vibrate, of(alarmsBlocked = true, mediaZero = true))

    @Test fun mediaIsNotUsedWhenAlarmsWork() = assertEquals(SadhanaBell.Alarm, of(mediaBlocked = true, mediaZero = true))
}
