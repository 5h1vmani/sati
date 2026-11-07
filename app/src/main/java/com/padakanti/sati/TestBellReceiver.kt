package com.padakanti.sati

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the test bell's alarm and rings it like the hourly bell: it plays,
 * vibrates or stays quiet, following the phone's state now. Unlike the hourly
 * bell, it ignores waking hours, pauses and a running sadhana, and it sets no
 * next alarm. It records what it did, so the screen can show it.
 */
class TestBellReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store(context)
        val action = PhoneState.bellAction(context)
        store.testOutcome = TestOutcome.of(action, PhoneState.isDoNotDisturb(context))
        store.testFiredAt = System.currentTimeMillis()
        if (action == BellAction.Quiet) return
        val pending = goAsync()
        BellService.ringBell(context.applicationContext) { pending.finish() }
    }
}
