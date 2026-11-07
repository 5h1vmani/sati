package com.padakanti.sati

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Rings one bell inside a short foreground service.
 *
 * Android 17 mutes sound from an app in the background unless the app runs a
 * foreground service. This applies to every app, whatever its target version.
 * The hourly bell and the sadhana bells ring from alarms, when Sati is
 * usually in the background, so they ring here.
 *
 * The service type is `systemExempted`. Android allows it for apps that hold
 * USE_EXACT_ALARM or SCHEDULE_EXACT_ALARM, and Sati holds one of them on every
 * version it runs on. A bell ends within [Ringer.MAX_PLAY_MS], so the service
 * stops before Android shows its notification in most cases.
 */
class BellService : Service() {
    /** Bells still ringing. The service stops when this reaches 0. */
    private var ringing = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires this call soon after startForegroundService, before any other work.
        try {
            goForeground()
        } catch (e: Exception) {
            // The bell still rings. On Android 17 its sound may then be muted, but vibration still works.
            Log.w(TAG, "Could not start the foreground service", e)
        }
        ringing++
        val done = {
            ringing--
            if (ringing <= 0) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        val app = applicationContext
        when (intent?.getStringExtra(EXTRA_KIND)) {
            KIND_HOURLY -> Ringer.ringBell(app, done)
            KIND_SADHANA -> {
                val strike = intent.getStringExtra(EXTRA_STRIKE)
                    ?.let { name -> Strike.entries.firstOrNull { it.name == name } }
                if (strike == null) {
                    done()
                } else {
                    Ringer.ringSadhana(app, strike, intent.getBooleanExtra(EXTRA_VIBRATE_ONLY, false), done)
                }
            }
            else -> done()
        }
        // A bell is never rung again after the process dies. The next alarm brings the next bell.
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.bell_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.bell_channel_description)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            },
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.lucide_bell)
            .setContentTitle(getString(R.string.bell_ringing))
            .setContentIntent(Alarms.openApp(this))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
        } else {
            startForeground(ID, notification)
        }
    }

    companion object {
        private const val TAG = "Sati"
        private const val CHANNEL = "bell"

        /** Different from the sadhana notification's ID, so neither replaces the other. */
        private const val ID = 2

        private const val EXTRA_KIND = "kind"
        private const val EXTRA_STRIKE = "strike"
        private const val EXTRA_VIBRATE_ONLY = "vibrateOnly"
        private const val KIND_HOURLY = "hourly"
        private const val KIND_SADHANA = "sadhana"

        /** Rings the hourly bell. [done] runs when the caller may stop waiting. */
        fun ringBell(context: Context, done: () -> Unit) {
            val intent = Intent(context, BellService::class.java).putExtra(EXTRA_KIND, KIND_HOURLY)
            if (start(context, intent)) done() else Ringer.ringBell(context, done)
        }

        /** Rings a sadhana bell. [done] runs when the caller may stop waiting. */
        fun ringSadhana(context: Context, strike: Strike, vibrateOnly: Boolean, done: () -> Unit) {
            val intent = Intent(context, BellService::class.java)
                .putExtra(EXTRA_KIND, KIND_SADHANA)
                .putExtra(EXTRA_STRIKE, strike.name)
                .putExtra(EXTRA_VIBRATE_ONLY, vibrateOnly)
            if (start(context, intent)) done() else Ringer.ringSadhana(context, strike, vibrateOnly, done)
        }

        /**
         * Starts the service. False when Android refuses, so the caller rings
         * the bell itself, as Sati 1.3.0 did. A bell muted on Android 17 is
         * better than no bell and no vibration.
         */
        private fun start(context: Context, intent: Intent): Boolean = try {
            context.startForegroundService(intent) != null
        } catch (e: Exception) {
            Log.w(TAG, "Android refused the bell service; ringing without it", e)
            false
        }
    }
}
