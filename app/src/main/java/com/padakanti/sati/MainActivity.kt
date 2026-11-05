package com.padakanti.sati

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.padakanti.sati.ui.Preview
import com.padakanti.sati.ui.SatiActions
import com.padakanti.sati.ui.SatiScreen
import com.padakanti.sati.ui.SatiTheme
import com.padakanti.sati.ui.SatiUi
import com.padakanti.sati.ui.Tab
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

/**
 * Hosts the one screen. The settings live in [Store]; the screen is rebuilt
 * whenever they change, from here, from the tile, or from a bell that rang.
 * Changes go through [BellController] and [SadhanaController].
 */
class MainActivity : ComponentActivity(), SatiActions {
    private lateinit var store: Store

    // Bumped on every settings change and every resume, so the screen rebuilds.
    private var tick by mutableIntStateOf(0)
    private var tab by mutableStateOf(Tab.Bell)
    private var preview by mutableStateOf<Preview?>(null)
    private var previewStop: (() -> Unit)? = null

    // Kept in a field: SharedPreferences holds listeners only weakly.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> tick++ }

    // Do Not Disturb and the ringer mode change outside the app.
    // Rebuild at once, so the notices never show the old state.
    private val phoneState = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            tick++
        }
    }

    private val pickSound = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importSound(uri)
    }

    // The sadhana starts whatever the answer. Without notifications it has no lock-screen countdown.
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = Store(this)
        store.prefs.registerOnSharedPreferenceChangeListener(listener)
        tab = savedInstanceState?.getString(KEY_TAB)?.let(Tab::valueOf) ?: Tab.Bell
        if (savedInstanceState == null) openTabFrom(intent)
        setContent {
            SatiTheme {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(tick) {
                    while (true) {
                        now = System.currentTimeMillis()
                        delay(15_000)
                    }
                }
                val ui = remember(tick, now, preview, Memory.importing, Memory.message) { buildUi(now) }
                SatiScreen(ui = ui, now = now, tab = tab, onTab = { tab = it }, actions = this)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_TAB, tab.name)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openTabFrom(intent)
    }

    /** The sadhana notification and alarm icon open the Sadhana tab. */
    private fun openTabFrom(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SADHANA, false) == true) tab = Tab.Sadhana
    }

    override fun onResume() {
        super.onResume()
        // A force-stop or a revoked permission may have cancelled the alarms.
        Alarms.rearm(this)
        SadhanaController.rearm(this)
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
            home = store.homeState(zoned),
            random = store.random,
            intervalMin = store.intervalMin,
            weekday = store.weekday,
            weekend = store.weekend,
            soundName = store.soundName,
            volume = store.volume,
            doNotDisturb = PhoneState.isDoNotDisturb(this),
            silenced = PhoneState.isRingerSilent(this),
            inaudible = !PhoneState.isDoNotDisturb(this) && !PhoneState.isRingerSilent(this) &&
                PhoneState.isVolumeZero(this, Channel.Notification),
            needsBattery = !getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
            needsExact = !getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            // Some phones have no setting for unused apps. Do not ask for one that cannot be changed.
            needsHibernation = !packageManager.isAutoRevokeWhitelisted && hibernationIntent().resolveActivity(packageManager) != null,
            importing = Memory.importing,
            previewing = preview,
            message = Memory.message,
            sadhanas = store.sadhanas,
            session = store.session,
            summary = store.summary,
            sadhanaVolume = store.sadhanaVolume,
            alarmVolumeZero = PhoneState.isVolumeZero(this, Channel.Alarm),
            alarmsBlocked = PhoneState.alarmsBlocked(this),
            sadhanaBell = PhoneState.sadhanaBell(this, vibrateOnly = false),
        )
    }

    // ---------- SatiActions: hourly bell ----------

    override fun setEnabled(on: Boolean) {
        BellController.setEnabled(this, on)
        // First turn-on: ask for background running at once, while the
        // user is thinking about the bell, instead of hoping they find it.
        if (on && !store.askedSetup) {
            store.askedSetup = true
            if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) fixBattery()
        }
    }

    override fun pause(hours: Int) = BellController.pause(this, hours)

    override fun resume() = BellController.resume(this)

    override fun setRandom(random: Boolean) = BellController.setRandom(this, random)

    override fun setInterval(minutes: Int) = BellController.setInterval(this, minutes)

    override fun setHour(field: HourField, minute: Int) {
        if (!BellController.setHour(this, field, minute)) Memory.message = field.refusal
    }

    override fun chooseSound() {
        if (Memory.importing) return
        pickSound.launch(arrayOf("audio/*"))
    }

    private fun importSound(uri: Uri) {
        Memory.importing = true
        val app = applicationContext
        Thread {
            val result = Sounds.importFrom(app, uri)
            // Written to process memory, not this activity, so a screen rotation does not lose it.
            Handler(Looper.getMainLooper()).post {
                Memory.importing = false
                Memory.message = when (result) {
                    is Sounds.ImportResult.Ok -> "Sound set to ${result.name}."
                    Sounds.ImportResult.TooLarge ->
                        "That file is larger than ${Sounds.MAX_BYTES / (1024 * 1024)} MB. The sound is unchanged."
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

    override fun togglePreview(preview: Preview) {
        val playing = this.preview
        previewStop?.invoke()
        if (playing != preview) startPreview(preview)
    }

    private fun startPreview(kind: Preview) {
        // Stopping is asynchronous, so an old preview can end after a new one
        // starts. Each preview clears the state only if it is still the current one.
        var self: (() -> Unit)? = null
        var ended = false
        val (channel, volume) = when (kind) {
            Preview.Bell -> Channel.Notification to store.volume
            // The test plays where a real sadhana bell would: alarm, or media when Do Not Disturb blocks alarms.
            Preview.Sadhana -> (if (PhoneState.sadhanaBell(this, false) == SadhanaBell.Media) Channel.Media else Channel.Alarm) to
                store.sadhanaVolume
        }
        self = Ringer.play(applicationContext, channel, volume) {
            ended = true
            if (previewStop === self) {
                previewStop = null
                preview = null
            }
        }
        if (!ended) {
            previewStop = self
            preview = kind
        }
    }

    override fun setVolume(percent: Int) {
        store.volume = percent
        // Let the user hear the new volume.
        previewStop?.invoke()
        startPreview(Preview.Bell)
    }

    override fun setSadhanaVolume(percent: Int) {
        store.sadhanaVolume = percent
        previewStop?.invoke()
        startPreview(Preview.Sadhana)
    }

    override fun fixBattery() = open(
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
    )

    override fun fixExact() = open(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")),
    )

    override fun fixHibernation() = open(hibernationIntent())

    // Android has no public name for the Do Not Disturb screen. This action
    // opens it on Pixel and most phones; elsewhere the sound settings open.
    override fun openDoNotDisturb() = open(
        Intent("android.settings.ZEN_MODE_SETTINGS"),
        fallback = Intent(Settings.ACTION_SOUND_SETTINGS),
    )

    private fun hibernationIntent() =
        Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, Uri.fromParts("package", packageName, null))

    override fun messageShown() {
        Memory.message = null
    }

    // ---------- SatiActions: sadhana ----------

    override fun startSadhana(sadhana: Sadhana) {
        previewStop?.invoke()
        // First sadhana: ask to show the lock-screen countdown. Android 12 allows it without asking.
        if (Build.VERSION.SDK_INT >= 33 && !store.askedNotifications &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            store.askedNotifications = true
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        SadhanaController.start(this, sadhana)
    }

    override fun pauseSadhana() = SadhanaController.pause(this)

    override fun resumeSadhana() = SadhanaController.resume(this)

    override fun stopSadhana() = SadhanaController.stop(this)

    override fun saveSadhana(sadhana: Sadhana) {
        val list = store.sadhanas
        store.sadhanas = if (list.any { it.id == sadhana.id }) list.map { if (it.id == sadhana.id) sadhana else it } else list + sadhana
    }

    override fun deleteSadhana(id: String) {
        store.sadhanas = store.sadhanas.filterNot { it.id == id }
    }

    override fun dismissSummary() {
        store.summary = null
    }

    /** Opens a system settings screen, or [fallback] if that screen is missing. */
    private fun open(
        intent: Intent,
        fallback: Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
    ) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(fallback)
        }
    }

    /** State that must outlive one activity, such as a sound import across a screen rotation. */
    private object Memory {
        var importing by mutableStateOf(false)
        var message by mutableStateOf<String?>(null)
    }

    companion object {
        const val EXTRA_SADHANA = "com.padakanti.sati.extra.SADHANA"
        private const val KEY_TAB = "tab"
    }
}
