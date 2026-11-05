package com.padakanti.sati

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives the sadhana alarm and rings the due bell. */
class SadhanaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        SadhanaController.onAlarm(context.applicationContext) { pending.finish() }
    }
}

/** Receives the Pause and Resume buttons on the sadhana notification. */
class SadhanaActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PAUSE -> SadhanaController.pause(context)
            ACTION_RESUME -> SadhanaController.resume(context)
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.padakanti.sati.action.PAUSE_SADHANA"
        const val ACTION_RESUME = "com.padakanti.sati.action.RESUME_SADHANA"
    }
}
