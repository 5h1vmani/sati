package com.padakanti.sati

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log

/** Plays the bell, vibrates when the phone is silenced, and stays quiet during Do Not Disturb. */
object Ringer {
    /** Long sounds are cut here. A broadcast receiver may not run much longer. */
    const val MAX_PLAY_MS = 30_000L
    private const val TAG = "Sati"

    fun ring(context: Context, done: () -> Unit) {
        val wake = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sati:ring")
        wake.acquire(MAX_PLAY_MS + 5_000)
        val finish = {
            if (wake.isHeld) wake.release()
            done()
        }
        // The next bell is already scheduled, so a quiet bell does not end the chain.
        when (action(context)) {
            BellAction.Quiet -> finish()
            BellAction.Vibrate -> {
                vibrate(context)
                finish()
            }
            BellAction.Play -> play(context, finish)
        }
    }

    /** Reads the phone's state and decides; see [BellAction.of]. */
    private fun action(context: Context): BellAction = BellAction.of(
        doNotDisturb = isDoNotDisturb(context),
        ringerSilent = isRingerSilent(context),
        inCall = isInCall(context),
        volumeZero = isVolumeZero(context),
    )

    /** True while Do Not Disturb is on, in any of its modes. */
    fun isDoNotDisturb(context: Context): Boolean {
        val filter = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    /** True on silent or vibrate, or while a call rings or is active. */
    fun isSilenced(context: Context): Boolean = isRingerSilent(context) || isInCall(context)

    /** True when the bell would play but the notification volume is at zero. */
    fun isInaudible(context: Context): Boolean =
        !isDoNotDisturb(context) && !isSilenced(context) && isVolumeZero(context)

    private fun isRingerSilent(context: Context): Boolean =
        context.getSystemService(AudioManager::class.java).ringerMode != AudioManager.RINGER_MODE_NORMAL

    // A bell over a ringing phone or in the ear during a call is worse
    // than a short vibration.
    private fun isInCall(context: Context): Boolean =
        context.getSystemService(AudioManager::class.java).mode in setOf(
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION,
            AudioManager.MODE_CALL_SCREENING,
        )

    private fun isVolumeZero(context: Context): Boolean =
        context.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_NOTIFICATION) == 0

    /**
     * Plays the chosen sound at the app's volume, within the notification
     * volume, and lowers any music while it plays. Calls [done] once, when the
     * sound ends, fails, or hits the cap. Returns a function that stops it early.
     */
    fun play(context: Context, done: () -> Unit): () -> Unit {
        val audio = context.getSystemService(AudioManager::class.java)
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .build()
        val player = MediaPlayer()
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        val finish = {
            if (!finished) {
                finished = true
                handler.removeCallbacksAndMessages(null)
                runCatching { player.stop() }
                player.release()
                audio.abandonAudioFocusRequest(focus)
                done()
            }
        }
        try {
            player.setAudioAttributes(attrs)
            Sounds.setSource(context, player)
            player.setOnCompletionListener { finish() }
            player.setOnErrorListener { _, what, extra ->
                // A file can load and still fail while playing. Vibrate, so
                // the user still gets the reminder.
                Log.w(TAG, "Sound failed while playing ($what, $extra); vibrating instead")
                vibrate(context)
                finish()
                true
            }
            player.prepare()
            val gain = gain(Store(context).volume)
            player.setVolume(gain, gain)
            audio.requestAudioFocus(focus)
            player.start()
            handler.postDelayed({ finish() }, MAX_PLAY_MS)
        } catch (e: Exception) {
            // A broken sound file must not leave the user without a reminder.
            Log.w(TAG, "Could not play the sound; vibrating instead", e)
            vibrate(context)
            finish()
        }
        return { handler.post { finish() } }
    }

    /** Ears hear loudness on a curve, so the slider's percent is squared. */
    private fun gain(percent: Int): Float {
        val p = percent.coerceIn(Store.MIN_VOLUME, 100) / 100f
        return p * p
    }

    /** Two firm pulses that can be felt in a pocket. Alarm usage lets them through silent mode. */
    fun vibrate(context: Context) {
        val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 300, 250, 300), -1)
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }
}
