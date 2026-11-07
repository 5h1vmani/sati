@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.padakanti.sati.ui

import android.text.format.DateFormat
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.padakanti.sati.HomeState
import com.padakanti.sati.HourField
import com.padakanti.sati.Hours
import com.padakanti.sati.R
import com.padakanti.sati.Ringer
import com.padakanti.sati.TestBell
import com.padakanti.sati.TestOutcome
import com.padakanti.sati.TimeText
import java.time.Instant
import java.time.ZoneId
import java.util.Date

/** The hourly bell: its state, rhythm, waking hours and sound. */
@Composable
internal fun BellTab(ui: SatiUi, now: Long, actions: SatiActions) {
    var editing by rememberSaveable { mutableStateOf<HourField?>(null) }

    Hero(ui, now, actions)
    if (ui.home !is HomeState.Off && ui.home !is HomeState.Muted) {
        if (ui.doNotDisturb) {
            Notice(R.drawable.lucide_moon, "Do Not Disturb is on. Sati stays quiet until it ends.")
        } else if (ui.inaudible) {
            Notice(R.drawable.lucide_volume_x, "Notification volume is off. Turn it up to hear the bell.", warning = true)
        } else if (ui.silenced) {
            Notice(R.drawable.lucide_vibrate, "Your phone is silent. Sati will vibrate instead of ringing.")
        }
    }
    if (ui.home !is HomeState.Off && (ui.needsBattery || ui.needsExact || ui.needsHibernation)) Setup(ui, actions)
    RhythmCard(ui, actions)
    HoursCard(ui) { editing = it }
    SoundCard(ui, actions)
    TestCard(ui.testBell, actions)
    Text(
        "On silent, Sati vibrates instead. During Do Not Disturb or a call, it stays quiet. The bell " +
            "follows your notification volume. Sounds stop after ${Ringer.MAX_PLAY_MS / 1000} seconds.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )

    editing?.let { field ->
        TimeDialog(
            title = field.title,
            initial = field.valueIn(ui.weekday, ui.weekend),
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
            HomeState.Muted -> MutedHero(actions)
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
private fun MutedHero(actions: SatiActions) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.lucide_timer), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sadhana running", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = true,
                    onCheckedChange = { actions.setEnabled(false) },
                    modifier = Modifier.semantics { contentDescription = "Sati is on. Turn off." },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("The bell is quiet", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text("It comes back by itself when the sadhana ends.", style = MaterialTheme.typography.bodyMedium)
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
            Text(
                TimeText.relative(home.next, now, ZoneId.systemDefault(), LocalConfiguration.current.locales[0]),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(TimeText.rhythmLine(ui.random, ui.intervalMin), style = MaterialTheme.typography.bodyMedium)
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

// ---------- Setup ----------

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

// ---------- Settings sections ----------

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
                    label = { Text(TimeText.shortInterval(minutes)) },
                )
            }
        }
        Text(
            TimeText.rhythmHint(ui.random, ui.intervalMin),
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
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val locale = LocalConfiguration.current.locales[0]
    // With large text the times move under the label, instead of squeezing it into a narrow column.
    FlowRow(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TimePill(TimeText.clock(hours.startMin, is24, locale), "$title start", onStart)
            Text("–", Modifier.padding(horizontal = 6.dp))
            TimePill(TimeText.clock(hours.endMin, is24, locale), "$title end", onEnd)
        }
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

/**
 * The test bell. It rings through the real alarm path a few seconds after the
 * tap, so it shows what the hourly bell would do now, and whether the phone
 * lets Sati's alarms through at all.
 */
@Composable
private fun TestCard(test: TestBell, actions: SatiActions) {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val locale = LocalConfiguration.current.locales[0]
    fun clock(at: Long) = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
        .let { TimeText.clock(it.hour * 60 + it.minute, is24, locale) }

    SectionCard(R.drawable.lucide_bell, stringResource(R.string.test_bell)) {
        val seconds = (TestBell.DELAY_MS / 1000).toInt()
        val (line, warning) = when (test) {
            TestBell.None -> pluralStringResource(R.plurals.test_bell_idle, seconds, seconds) to false
            is TestBell.Waiting -> stringResource(R.string.test_bell_waiting) to false
            is TestBell.Done -> stringResource(
                when (test.outcome) {
                    TestOutcome.Rang -> R.string.test_bell_rang
                    TestOutcome.Vibrated -> R.string.test_bell_vibrated
                    TestOutcome.QuietDoNotDisturb -> R.string.test_bell_quiet_dnd
                    TestOutcome.QuietCall -> R.string.test_bell_quiet_call
                },
                clock(test.at),
            ) to false
            is TestBell.Missed -> stringResource(R.string.test_bell_missed, clock(test.at)) to true
        }
        Text(
            line,
            style = MaterialTheme.typography.bodyMedium,
            color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions::testBell, enabled = test !is TestBell.Waiting) {
                Text(stringResource(R.string.test_bell))
            }
            if (test is TestBell.Missed) {
                TextButton(onClick = actions::fixBattery) { Text(stringResource(R.string.test_bell_background)) }
            }
        }
    }
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
            PreviewButton(ui.previewing == Preview.Bell) { actions.togglePreview(Preview.Bell) }
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
        VolumeRow(ui.volume, "Bell volume", actions::setVolume)
    }
}
