@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.padakanti.sati.ui

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.padakanti.sati.HomeState
import com.padakanti.sati.Hours
import com.padakanti.sati.R
import com.padakanti.sati.Scheduler
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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
    val previewing: Boolean,
    val message: String?,
)

interface SatiActions {
    fun setEnabled(on: Boolean)
    fun pause(hours: Int)
    fun resume()
    fun setRandom(random: Boolean)
    fun setInterval(minutes: Int)
    fun setHour(field: HourField, minute: Int)
    fun chooseSound()
    fun useDefaultSound()
    fun togglePreview()
    fun setVolume(percent: Int)
    fun fixBattery()
    fun fixExact()
    fun fixHibernation()
    fun messageShown()
}

enum class HourField(val title: String) {
    WeekdayStart("Weekdays start"),
    WeekdayEnd("Weekdays end"),
    WeekendStart("Weekends start"),
    WeekendEnd("Weekends end"),
}

val Intervals = listOf(15, 20, 30, 45, 60, 90, 120)

@Composable
fun SatiScreen(ui: SatiUi, now: Long, actions: SatiActions) {
    val snackbar = remember { SnackbarHostState() }
    var editing by rememberSaveable { mutableStateOf<HourField?>(null) }

    LaunchedEffect(ui.message) {
        val message = ui.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        actions.messageShown()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Sati",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
            )
            Hero(ui, now, actions)
            if (ui.home !is HomeState.Off) {
                if (ui.doNotDisturb) {
                    Notice(R.drawable.lucide_moon, "Do Not Disturb is on. Sati stays quiet until it ends.")
                } else if (ui.inaudible) {
                    Notice(R.drawable.lucide_volume_x, "Notification volume is off. Turn it up to hear the bell.", warning = true)
                } else if (ui.silenced) {
                    Notice(R.drawable.lucide_vibrate, "Your phone is silent. Sati will vibrate instead of ringing.")
                }
                if (ui.needsBattery || ui.needsExact || ui.needsHibernation) Setup(ui, actions)
            }
            RhythmCard(ui, actions)
            HoursCard(ui) { editing = it }
            SoundCard(ui, actions)
            Text(
                "On silent, Sati vibrates instead. During Do Not Disturb, it stays quiet. The bell " +
                    "follows your notification volume. Sounds stop after 30 seconds.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }

    editing?.let { field ->
        val initial = when (field) {
            HourField.WeekdayStart -> ui.weekday.startMin
            HourField.WeekdayEnd -> ui.weekday.endMin
            HourField.WeekendStart -> ui.weekend.startMin
            HourField.WeekendEnd -> ui.weekend.endMin
        }
        TimeDialog(
            title = field.title,
            initial = initial,
            onDismiss = { editing = null },
            onConfirm = {
                editing = null
                actions.setHour(field, it)
            },
        )
    }
}

// ---------- Hero: one card per state ----------

@Composable
private fun Hero(ui: SatiUi, now: Long, actions: SatiActions) {
    AnimatedContent(
        targetState = ui.home,
        contentKey = { it::class },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "hero",
    ) { home ->
        when (home) {
            HomeState.Off -> OffHero(actions)
            is HomeState.Paused -> PausedHero(home, actions)
            is HomeState.On -> OnHero(home, ui, now, actions)
        }
    }
}

@Composable
private fun OffHero(actions: SatiActions) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(24.dp)) {
            Icon(
                painterResource(R.drawable.lucide_bell_off), null,
                Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text("Sati is off", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Turn it on to hear a bell now and then during your waking hours. " +
                    "Each bell is a moment to come back to the present.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = { actions.setEnabled(true) }, Modifier.fillMaxWidth().height(52.dp)) {
                Icon(painterResource(R.drawable.lucide_bell), null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Turn on")
            }
        }
    }
}

@Composable
private fun OnHero(home: HomeState.On, ui: SatiUi, now: Long, actions: SatiActions) {
    val context = LocalContext.current
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Brand, contentColor = OnBrand),
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Next bell", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = true,
                    onCheckedChange = { actions.setEnabled(false) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Brand,
                        checkedTrackColor = OnBrand,
                        checkedBorderColor = OnBrand,
                    ),
                    modifier = Modifier.semantics { contentDescription = "Sati is on. Turn off." },
                )
            }
            Text(
                if (home.next > 0) DateFormat.getTimeFormat(context).format(Date(home.next)) else "--:--",
                style = MaterialTheme.typography.displayLarge,
            )
            Text(relative(home.next, now), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(rhythmLine(ui.random, ui.intervalMin), style = MaterialTheme.typography.bodyMedium)
            if (home.restingNow) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.lucide_moon), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Outside your waking hours. Rest well.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(1, 2).forEach { hours ->
                    OutlinedButton(
                        onClick = { actions.pause(hours) },
                        border = BorderStroke(1.dp, OnBrand),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OnBrand),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(painterResource(R.drawable.lucide_pause), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Pause $hours h")
                    }
                }
            }
        }
    }
}

@Composable
private fun PausedHero(home: HomeState.Paused, actions: SatiActions) {
    val context = LocalContext.current
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.lucide_pause), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Paused", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = true,
                    onCheckedChange = { actions.setEnabled(false) },
                    modifier = Modifier.semantics { contentDescription = "Sati is on. Turn off." },
                )
            }
            Text(
                "until " + DateFormat.getTimeFormat(context).format(Date(home.until)),
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(4.dp))
            Text("The bell starts again by itself.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            Button(onClick = { actions.resume() }, Modifier.fillMaxWidth().height(52.dp)) {
                Icon(painterResource(R.drawable.lucide_play), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Resume now")
            }
        }
    }
}

// ---------- Notices and setup ----------

@Composable
private fun Notice(@DrawableRes icon: Int, text: String, warning: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (warning) colors.primaryContainer else colors.surfaceContainer,
            contentColor = if (warning) colors.onPrimaryContainer else colors.onSurface,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Setup(ui: SatiUi, actions: SatiActions) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.lucide_triangle_alert), null, Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text("Help Sati ring on time", style = MaterialTheme.typography.titleMedium)
            }
            if (ui.needsBattery) {
                SetupItem(
                    R.drawable.lucide_battery, "Run in the background",
                    "Without this, Android may delay the bell to save battery.", "Allow", actions::fixBattery,
                )
            }
            if (ui.needsExact) {
                SetupItem(
                    R.drawable.lucide_alarm_clock, "Exact timing",
                    "This lets the bell ring at the planned minute.", "Allow", actions::fixExact,
                )
            }
            if (ui.needsHibernation) {
                SetupItem(
                    R.drawable.lucide_snowflake, "Stay active when unused",
                    "Android pauses apps you do not open for a few months. On the next screen, " +
                        "turn off the option for unused apps.",
                    "Open", actions::fixHibernation,
                )
            }
        }
    }
}

@Composable
private fun SetupItem(@DrawableRes icon: Int, title: String, body: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onClick) { Text(action) }
    }
}

// ---------- Settings sections ----------

@Composable
private fun SectionCard(@DrawableRes icon: Int, title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun RhythmCard(ui: SatiUi, actions: SatiActions) {
    SectionCard(R.drawable.lucide_shuffle, "Rhythm") {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = ui.random,
                onClick = { actions.setRandom(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text("Random") }
            SegmentedButton(
                selected = !ui.random,
                onClick = { actions.setRandom(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text("On the clock") }
        }
        Text("Interval", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Intervals.forEach { minutes ->
                FilterChip(
                    selected = ui.intervalMin == minutes,
                    onClick = { actions.setInterval(minutes) },
                    label = { Text(shortInterval(minutes)) },
                )
            }
        }
        Text(
            rhythmHint(ui.random, ui.intervalMin),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HoursCard(ui: SatiUi, onEdit: (HourField) -> Unit) {
    SectionCard(R.drawable.lucide_sunrise, "Waking hours") {
        HoursRow("Weekdays", "Monday to Friday", ui.weekday, { onEdit(HourField.WeekdayStart) }, { onEdit(HourField.WeekdayEnd) })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        HoursRow("Weekends", "Saturday and Sunday", ui.weekend, { onEdit(HourField.WeekendStart) }, { onEdit(HourField.WeekendEnd) })
    }
}

@Composable
private fun HoursRow(title: String, sub: String, hours: Hours, onStart: () -> Unit, onEnd: () -> Unit) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TimePill(clock(context, hours.startMin), "$title start", onStart)
        Text("–", Modifier.padding(horizontal = 6.dp))
        TimePill(clock(context, hours.endMin), "$title end", onEnd)
    }
}

@Composable
private fun TimePill(text: String, label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = Modifier.semantics { contentDescription = "$label, $text. Change." },
    ) { Text(text) }
}

@Composable
private fun SoundCard(ui: SatiUi, actions: SatiActions) {
    SectionCard(R.drawable.lucide_music, "Sound") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ui.soundName ?: "Default bell", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (ui.soundName == null) "Built in" else "Your sound",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalIconButton(onClick = actions::togglePreview, modifier = Modifier.size(48.dp)) {
                Icon(
                    painterResource(if (ui.previewing) R.drawable.lucide_square else R.drawable.lucide_play),
                    contentDescription = if (ui.previewing) "Stop sound" else "Play sound",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions::chooseSound, enabled = !ui.importing) {
                Text(if (ui.importing) "Copying…" else "Choose sound")
            }
            if (ui.soundName != null && !ui.importing) {
                TextButton(onClick = actions::useDefaultSound) { Text("Use default") }
            }
        }
        if (ui.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
        VolumeRow(ui.volume, actions)
    }
}

@Composable
private fun VolumeRow(volume: Int, actions: SatiActions) {
    // Local while dragging; saved, and the bell played, when the finger lifts.
    var value by remember(volume) { mutableFloatStateOf(volume.toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.lucide_volume_2), null, Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { actions.setVolume(value.roundToInt()) },
            valueRange = 5f..100f,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Bell volume" },
        )
        Spacer(Modifier.width(12.dp))
        Text("${value.roundToInt()}%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(40.dp))
    }
}

@Composable
private fun TimeDialog(title: String, initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initial / 60,
        initialMinute = initial % 60,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------- Text helpers ----------

private fun shortInterval(min: Int) = when {
    min < 60 -> "$min min"
    min % 60 == 0 -> "${min / 60} h"
    else -> "${min / 60} h ${min % 60}"
}

private fun longInterval(min: Int) = when {
    min < 60 -> "$min minutes"
    min == 60 -> "hour"
    min % 60 == 0 -> "${min / 60} hours"
    else -> "${min / 60} h ${min % 60} min"
}

private fun rhythmLine(random: Boolean, min: Int) =
    if (random) "Random, about every ${longInterval(min)}" else "Every ${longInterval(min)}, on the clock"

private fun rhythmHint(random: Boolean, min: Int): String =
    if (random) {
        val low = (min * Scheduler.RANDOM_LOW).toInt()
        val high = (min * Scheduler.RANDOM_HIGH).toInt()
        "Bells come at unexpected times, $low to $high minutes apart. The mind does not learn to tune them out."
    } else {
        "Bells come every ${longInterval(min)}, counted from the start of your waking hours."
    }

/** "in 23 min", "in 1 h 5 min", "tomorrow", or "on Saturday". */
private fun relative(next: Long, now: Long): String {
    if (next <= 0) return ""
    val zone = ZoneId.systemDefault()
    val days = ChronoUnit.DAYS.between(
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
        Instant.ofEpochMilli(next).atZone(zone).toLocalDate(),
    )
    val mins = ((next - now + 59_999) / 60_000).coerceAtLeast(0)
    return when {
        days == 0L && mins < 1 -> "in under a minute"
        days == 0L && mins < 60 -> "in $mins min"
        days == 0L && mins % 60 == 0L -> "in ${mins / 60} h"
        days == 0L -> "in ${mins / 60} h ${mins % 60} min"
        days == 1L -> "tomorrow"
        else -> "on " + Instant.ofEpochMilli(next).atZone(zone).dayOfWeek
            .getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
}

private fun clock(context: android.content.Context, minute: Int): String {
    val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
    return LocalTime.of(minute / 60, minute % 60).format(DateTimeFormatter.ofPattern(pattern))
}
