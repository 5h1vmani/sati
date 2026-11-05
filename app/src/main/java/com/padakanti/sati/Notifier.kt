package com.padakanti.sati

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

/**
 * The lock-screen notification of a running sadhana. It counts down to the
 * next bell and has a Pause or Resume button. Stop is only in the app, where
 * it asks first, so a stray tap with closed eyes cannot end the sadhana.
 */
object Notifier {
    private const val CHANNEL = "sadhana"
    private const val ID = 1

    fun show(context: Context, s: Session) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Sadhana", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the time left in a running sadhana."
                setShowBadge(false)
            },
        )
        val now = System.currentTimeMillis()
        val position = s.position(now)
        val stage = TimeText.stage(s.sadhana, position)
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.lucide_timer)
            .setContentTitle(s.sadhana.name)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(Alarms.openApp(context))
        if (s.paused) {
            builder.setContentText("Paused · $stage · ${TimeText.countdown(position.totalLeftMs)} left")
                .setShowWhen(false)
                .addAction(action(context, SadhanaActionReceiver.ACTION_RESUME, R.drawable.lucide_play, "Resume"))
        } else {
            builder.setContentText("$stage · next bell")
                .setWhen(s.nextTime() ?: now)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .addAction(action(context, SadhanaActionReceiver.ACTION_PAUSE, R.drawable.lucide_pause, "Pause"))
        }
        nm.notify(ID, builder.build())
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }

    private fun action(context: Context, action: String, icon: Int, title: String): Notification.Action {
        val intent = PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, SadhanaActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(context, icon), title, intent).build()
    }
}
