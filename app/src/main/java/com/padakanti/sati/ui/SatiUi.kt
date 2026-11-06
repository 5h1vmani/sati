package com.padakanti.sati.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.padakanti.sati.HomeState
import com.padakanti.sati.HourField
import com.padakanti.sati.Hours
import com.padakanti.sati.R
import com.padakanti.sati.Sadhana
import com.padakanti.sati.SadhanaBell
import com.padakanti.sati.Session
import com.padakanti.sati.Summary
import com.padakanti.sati.log.LogImportState
import com.padakanti.sati.log.Measure

/** Everything the screen shows. Built fresh from the settings on every change. */
data class SatiUi(
    val home: HomeState,
    val random: Boolean,
    val intervalMin: Int,
    val weekday: Hours,
    val weekend: Hours,
    val soundName: String?,
    val volume: Int,
    val doNotDisturb: Boolean,
    val silenced: Boolean,
    val inaudible: Boolean,
    val needsBattery: Boolean,
    val needsExact: Boolean,
    val needsHibernation: Boolean,
    val importing: Boolean,
    val previewing: Preview?,
    val message: String?,
    val sadhanas: List<Sadhana>,
    val session: Session?,
    val summary: Summary?,
    val sadhanaVolume: Int,
    val alarmVolumeZero: Boolean,
    val alarmsBlocked: Boolean,
    /** How a sadhana bell would sound now, for a sadhana that is not set to vibrate only. */
    val sadhanaBell: SadhanaBell,
    /** Epoch milliseconds of the last log export. 0 means never. */
    val logLastExport: Long,
    /** True while an export or an import runs. */
    val logBusy: Boolean,
    val logImport: LogImportState,
)

/** Which bell a test play uses: the hourly bell's channel and volume, or the sadhana's. */
enum class Preview { Bell, Sadhana }

/**
 * The tabs. Bell and Sadhana keep their titles as plain text for now. A tab
 * with a [titleRes] takes its title from the string resources instead.
 */
enum class Tab(val title: String, @DrawableRes val icon: Int, @StringRes val titleRes: Int = 0) {
    Bell("Bell", R.drawable.lucide_bell),
    Sadhana("Sadhana", R.drawable.lucide_timer),
    Log("", R.drawable.lucide_notebook_pen, R.string.tab_log),
}

interface SatiActions {
    fun setEnabled(on: Boolean)
    fun pause(hours: Int)
    fun resume()
    fun setRandom(random: Boolean)
    fun setInterval(minutes: Int)
    fun setHour(field: HourField, minute: Int)
    fun chooseSound()
    fun useDefaultSound()
    fun togglePreview(preview: Preview)
    fun setVolume(percent: Int)
    fun setSadhanaVolume(percent: Int)
    fun fixBattery()
    fun fixExact()
    fun fixHibernation()
    fun openDoNotDisturb()
    fun messageShown()
    fun startSadhana(sadhana: Sadhana)
    fun pauseSadhana()
    fun resumeSadhana()
    fun stopSadhana()
    fun saveSadhana(sadhana: Sadhana)
    fun deleteSadhana(id: String)
    fun dismissSummary()
    fun exportLog()
    fun importLog()

    /** Runs the import that is waiting for measures. [measures] maps each new practice name to its measure. */
    fun finishLogImport(measures: Map<String, Measure>)
    fun dismissLogImport()
}

val Intervals = listOf(15, 20, 30, 45, 60, 90, 120)
