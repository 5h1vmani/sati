package com.padakanti.sati

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

/** Plays bells and vibrations. What to do is decided in [BellAction] and [SadhanaBell]; this only carries it out. */
object Ringer {
    /** Long sounds are cut here. A broadcast receiver may not run much longer. */
    const val MAX_PLAY_MS = 30_000L

    /** Time between strikes of a sadhana bell. */
    const val STRIKE_GAP_MS = 4_000L

    private const val TAG = "Sati"

    /** The hourly bell: plays, vibrates or stays quiet, following the phone's state. */
    fun ringBell(context: Context, done: () -> Unit) = withWakeLock(context, done) { finish ->
        act(context, PhoneState.bellAction(context), Channel.Notification, Store(context).volume, 1, finish)
    }

    /** A sadhana bell: [strike] decides how many times it sounds. */
    fun ringSadhana(context: Context, strike: Strike, vibrateOnly: Boolean, done: () -> Unit) =
        withWakeLock(context, done) { finish ->
            val volume = Store(context).sadhanaVolume
            when (PhoneState.sadhanaBell(context, vibrateOnly)) {
                SadhanaBell.Alarm -> play(context, Channel.Alarm, volume, strike.times, finish)
                SadhanaBell.Media -> play(context, Channel.Media, volume, strike.times, finish)
                SadhanaBell.Vibrate -> {
                    vibrate(context, strike.times)
                    finish()
                }
            }
        }

    private fun act(context: Context, action: BellAction, channel: Channel, volume: Int, times: Int, finish: () -> Unit) {
        when (action) {
            BellAction.Quiet -> finish()
            BellAction.Vibrate -> {
                vibrate(context, times)
                finish()
            }
            BellAction.Play -> play(context, channel, volume, times, finish)
        }
    }

    /**
     * Keeps the phone awake while the bell sounds. [done] runs exactly once,
     * even if something fails, so the receiver is always released.
     */
    private fun withWakeLock(context: Context, done: () -> Unit, block: (finish: () -> Unit) -> Unit) {
        val wake = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Sati:ring")
        wake.acquire(MAX_PLAY_MS + 5_000)
        var finished = false
        val finish = {
            if (!finished) {
                finished = true
                if (wake.isHeld) wake.release()
                done()
            }
        }
        try {
            block(finish)
        } catch (e: Exception) {
            Log.w(TAG, "Could not ring", e)
            finish()
        }
    }

    /**
     * Plays the chosen sound [times] times, [STRIKE_GAP_MS] apart, on [channel]
     * at [volume] percent of that channel's volume. Lowers any music while it
     * plays. Calls [done] once, when the last strike ends, on failure, or at
     * [MAX_PLAY_MS]. Returns a function that stops it early.
     */
    fun play(context: Context, channel: Channel, volume: Int, times: Int = 1, done: () -> Unit): () -> Unit {
        val audio = context.getSystemService(AudioManager::class.java)
        val attrs = AudioAttributes.Builder()
            .setUsage(channel.usage)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .build()
        val handler = Handler(Looper.getMainLooper())
        val players = mutableListOf<MediaPlayer>()
        var finished = false
        var completed = 0
        val finish = {
            if (!finished) {
                finished = true
                handler.removeCallbacksAndMessages(null)
                players.forEach {
                    runCatching { it.stop() }
                    it.release()
                }
                audio.abandonAudioFocusRequest(focus)
                done()
            }
        }
        val fail = { what: String ->
            // A broken sound file must not leave the user without a reminder.
            Log.w(TAG, "$what; vibrating instead")
            vibrate(context, times)
            finish()
        }
        try {
            val gain = gain(volume)
            repeat(times) {
                val player = MediaPlayer()
                players += player
                player.setAudioAttributes(attrs)
                Sounds.setSource(context, player)
                player.setOnCompletionListener { if (++completed == times) finish() }
                player.setOnErrorListener { _, what, extra ->
                    fail("Sound failed while playing ($what, $extra)")
                    true
                }
                player.prepare()
                player.setVolume(gain, gain)
            }
            audio.requestAudioFocus(focus)
            players.forEachIndexed { i, player ->
                handler.postDelayed({ if (!finished) player.start() }, i * STRIKE_GAP_MS)
            }
            handler.postDelayed({ finish() }, MAX_PLAY_MS)
        } catch (e: Exception) {
            fail("Could not play the sound: $e")
        }
        return { handler.post { finish() } }
    }

    /** Ears hear loudness on a curve, so the slider's percent is squared. */
    private fun gain(percent: Int): Float {
        val p = percent.coerceIn(Store.MIN_VOLUME, Store.MAX_VOLUME) / 100f
        return p * p
    }

    /**
     * Firm pulses that can be felt in a pocket, one pair per strike. Alarm
     * usage lets them through silent mode.
     */
    fun vibrate(context: Context, times: Int = 1) {
        val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator
        val pattern = mutableListOf(0L)
        repeat(times) {
            if (it > 0) pattern += 700L
            pattern += listOf(300L, 250L, 300L)
        }
        val effect = VibrationEffect.createWaveform(pattern.toLongArray(), -1)
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }
}
