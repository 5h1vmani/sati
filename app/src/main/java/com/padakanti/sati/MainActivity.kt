package com.padakanti.sati

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.padakanti.sati.ui.HourField
import com.padakanti.sati.ui.SatiActions
import com.padakanti.sati.ui.SatiScreen
import com.padakanti.sati.ui.SatiTheme
import com.padakanti.sati.ui.SatiUi
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

/**
 * Hosts the one screen. The settings live in [Store]; the screen is rebuilt
 * whenever they change, from here, from the tile, or from a bell that rang.
 */
class MainActivity : ComponentActivity(), SatiActions {
    private lateinit var store: Store

    // Bumped on every settings change and every resume, so the screen rebuilds.
    private var tick by mutableIntStateOf(0)
    private var importing by mutableStateOf(false)
    private var previewStop by mutableStateOf<(() -> Unit)?>(null)
    private var message by mutableStateOf<String?>(null)

    // Kept in a field: SharedPreferences holds listeners only weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> tick++ }

    // Do Not Disturb and the ringer mode change outside the app. Rebuild at
    // once, so the notices never show the old state.
    private val phoneState = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            tick++
        }
    }

    private val pickSound = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importSound(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = Store(this)
        store.prefs.registerOnSharedPreferenceChangeListener(listener)
        setContent {
            SatiTheme {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(tick) {
                    while (true) {
                        now = System.currentTimeMillis()
                        delay(15_000)
                    }
                }
                val ui = remember(tick, now, importing, previewStop, message) { buildUi(now) }
                SatiScreen(ui = ui, now = now, actions = this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // A force-stop or a revoked permission may have cancelled the alarm.
        Alarms.rearm(this)
        tick++
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        // Not exported: only the system sends these, and it still reaches the receiver.
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(phoneState, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(phoneState, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(phoneState)
        previewStop?.invoke()
    }

    override fun onDestroy() {
        super.onDestroy()
        store.prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun buildUi(now: Long): SatiUi {
        val zoned = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        return SatiUi(
            home = HomeState.of(store.enabled, store.pausedUntil, store.nextRing, zoned, store.plan()),
            random = store.random,
            intervalMin = store.intervalMin,
            weekday = Hours(store.weekdayStart, store.weekdayEnd),
            weekend = Hours(store.weekendStart, store.weekendEnd),
            soundName = store.soundName,
            volume = store.volume,
            doNotDisturb = Ringer.isDoNotDisturb(this),
            silenced = Ringer.isSilenced(this),
            inaudible = Ringer.isInaudible(this),
            needsBattery = !getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
            needsExact = !getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            // Some phones have no setting for unused apps. Do not ask for one that cannot be changed.
            needsHibernation = !packageManager.isAutoRevokeWhitelisted && hibernationIntent().resolveActivity(packageManager) != null,
            importing = importing,
            previewing = previewStop != null,
            message = message,
        )
    }

    // ---------- SatiActions ----------

    override fun setEnabled(on: Boolean) {
        store.enabled = on
        if (on) store.pausedUntil = 0
        Alarms.reschedule(this)
        // First turn-on: ask for background running at once, while the
        // user is thinking about the bell, instead of hoping they find it.
        if (on && !store.askedSetup) {
            store.askedSetup = true
            if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) fixBattery()
        }
    }

    override fun pause(hours: Int) {
        store.pausedUntil = System.currentTimeMillis() + hours * 3_600_000L
        Alarms.reschedule(this)
    }

    override fun resume() {
        store.pausedUntil = 0
        Alarms.reschedule(this)
    }

    override fun setRandom(random: Boolean) {
        if (store.random == random) return
        store.random = random
        Alarms.reschedule(this)
    }

    override fun setInterval(minutes: Int) {
        if (store.intervalMin == minutes) return
        store.intervalMin = minutes
        Alarms.reschedule(this)
    }

    override fun setHour(field: HourField, minute: Int) {
        val weekend = field == HourField.WeekendStart || field == HourField.WeekendEnd
        val start = if (weekend) store.weekendStart else store.weekdayStart
        val end = if (weekend) store.weekendEnd else store.weekdayEnd
        when (field) {
            HourField.WeekdayStart, HourField.WeekendStart -> {
                if (minute >= end) return run { message = "The start must be before the end." }
                if (weekend) store.weekendStart = minute else store.weekdayStart = minute
            }
            HourField.WeekdayEnd, HourField.WeekendEnd -> {
                if (minute <= start) return run { message = "The end must be after the start." }
                if (weekend) store.weekendEnd = minute else store.weekdayEnd = minute
            }
        }
        Alarms.reschedule(this)
    }

    override fun chooseSound() {
        if (importing) return
        pickSound.launch(arrayOf("audio/*"))
    }

    private fun importSound(uri: Uri) {
        importing = true
        Thread {
            val result = Sounds.importFrom(applicationContext, uri)
            runOnUiThread {
                importing = false
                message = when (result) {
                    is Sounds.ImportResult.Ok -> "Sound set to ${result.name}."
                    Sounds.ImportResult.TooLarge -> "That file is larger than 20 MB. The sound is unchanged."
                    Sounds.ImportResult.Unplayable -> "That file could not be played. The sound is unchanged."
                    Sounds.ImportResult.Failed -> "That file could not be copied. The sound is unchanged."
                }
            }
        }.start()
    }

    override fun useDefaultSound() {
        previewStop?.invoke()
        Sounds.useDefault(this)
    }

    override fun togglePreview() {
        val stop = previewStop
        if (stop != null) stop() else startPreview()
    }

    private fun startPreview() {
        // Stopping is asynchronous, so an old preview can end after a new one
        // starts. Each preview clears the state only if it is still the current one.
        var self: (() -> Unit)? = null
        var ended = false
        self = Ringer.play(applicationContext) {
            ended = true
            if (previewStop === self) previewStop = null
        }
        if (!ended) previewStop = self
    }

    override fun setVolume(percent: Int) {
        store.volume = percent
        // Let the user hear the new volume.
        previewStop?.invoke()
        startPreview()
    }

    override fun fixBattery() = open(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
    )

    override fun fixExact() = open(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")),
    )

    override fun fixHibernation() = open(hibernationIntent())

    private fun hibernationIntent() =
        Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, Uri.fromParts("package", packageName, null))

    override fun messageShown() {
        message = null
    }

    /** Opens a system settings screen, or the app's info page if that screen is missing. */
    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
    }
}
