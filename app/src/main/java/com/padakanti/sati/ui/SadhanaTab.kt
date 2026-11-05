package com.padakanti.sati.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.padakanti.sati.R
import com.padakanti.sati.Sadhana
import com.padakanti.sati.SadhanaBell
import com.padakanti.sati.SadhanaCheck
import com.padakanti.sati.SadhanaJson
import com.padakanti.sati.Session
import com.padakanti.sati.Stage
import com.padakanti.sati.Summary
import com.padakanti.sati.TimeText
import kotlinx.coroutines.delay

/** Keeps the open editor across a screen rotation. Empty string means no editor. */
private val EditingSaver = Saver<Sadhana?, String>(
    save = { it?.let { s -> SadhanaJson.encodeList(listOf(s)) } ?: "" },
    restore = { json -> if (json.isEmpty()) null else SadhanaJson.decodeDraft(json) },
)

/** The timer for a timed practice: saved sadhanas, the running one, and its bells. */
@Composable
internal fun SadhanaTab(ui: SatiUi, actions: SatiActions) {
    var editing by rememberSaveable(stateSaver = EditingSaver) { mutableStateOf<Sadhana?>(null) }

    val session = ui.session
    if (session != null) {
        Running(session, actions)
        BellNotices(ui, actions)
    } else {
        ui.summary?.let { SummaryCard(it, actions) }
        if (ui.needsExact) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    SetupItem(
                        R.drawable.lucide_alarm_clock, "Exact timing",
                        "This lets each bell ring at the planned second.", "Allow", actions::fixExact,
                    )
                }
            }
        }
        BellNotices(ui, actions)
        ui.sadhanas.forEach { s ->
            SadhanaCard(s, onStart = { actions.startSadhana(s) }, onEdit = { editing = s })
        }
        // At the limit, every export can still be imported back, so no more can be added.
        val full = ui.sadhanas.size >= SadhanaCheck.MAX_SADHANAS
        OutlinedButton(
            onClick = { editing = Sadhana("s-${System.currentTimeMillis()}", "", listOf(Stage(20))) },
            enabled = !full,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(painterResource(R.drawable.lucide_plus), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("New sadhana")
        }
        if (full) {
            Text(
                pluralStringResource(R.plurals.sadhana_limit, SadhanaCheck.MAX_SADHANAS, SadhanaCheck.MAX_SADHANAS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        BellsCard(ui, actions)
        Text(
            "While a sadhana runs, the hourly bell stays quiet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )
    }

    editing?.let { draft ->
        SadhanaEditor(
            initial = draft,
            isNew = ui.sadhanas.none { it.id == draft.id },
            onSave = {
                actions.saveSadhana(it)
                editing = null
            },
            onDelete = {
                actions.deleteSadhana(draft.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/**
 * Tells the user when sadhana bells will not sound as expected, and how to
 * fix it. Shown before and during a sadhana, since Do Not Disturb can start
 * at any time.
 */
@Composable
private fun BellNotices(ui: SatiUi, actions: SatiActions) {
    when {
        ui.sadhanaBell == SadhanaBell.Media -> Notice(
            R.drawable.lucide_moon,
            "Do Not Disturb blocks alarms, so sadhana bells play at your media volume.",
        )
        ui.alarmsBlocked -> Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                SetupItem(
                    R.drawable.lucide_triangle_alert, "Do Not Disturb blocks the bells",
                    "Sadhana bells cannot be heard. In Do Not Disturb settings, allow Alarms.",
                    "Open", actions::openDoNotDisturb,
                )
            }
        }
        ui.alarmVolumeZero -> Notice(
            R.drawable.lucide_volume_x,
            "Alarm volume is off. Sadhana bells will vibrate instead. Turn up the alarm volume to hear them.",
            warning = true,
        )
    }
}

@Composable
private fun SadhanaCard(s: Sadhana, onStart: () -> Unit, onEdit: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium)
                val total = if (s.stages.size > 1) " · ${TimeText.length(s.totalMin)}" else ""
                Text(TimeText.stages(s.stages) + total, style = MaterialTheme.typography.bodyMedium)
                val extras = listOfNotNull(
                    if (s.settleSec > 0) "Settle ${TimeText.settle(s.settleSec)}" else null,
                    if (s.vibrateOnly) "Vibrate only" else null,
                )
                if (extras.isNotEmpty()) {
                    Text(
                        extras.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(painterResource(R.drawable.lucide_pencil), "Edit ${s.name}", Modifier.size(20.dp))
            }
            Button(onClick = onStart) {
                Icon(painterResource(R.drawable.lucide_play), null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Start")
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: Summary, actions: SatiActions) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (summary.completed) "Sadhana complete" else "Sadhana ended",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(4.dp))
            val minutes = (summary.satMs / 60_000).toInt()
            val time = if (minutes < 1) "less than a minute" else TimeText.length(minutes)
            Text("${summary.name}: $time of practice.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = actions::dismissSummary) { Text("Done") }
        }
    }
}

@Composable
private fun BellsCard(ui: SatiUi, actions: SatiActions) {
    SectionCard(R.drawable.lucide_bell, "Sadhana bells") {
        Text(
            "1 strike to start, 2 when a stage changes, 3 at the end. They ring through Do Not Disturb " +
                "and silent mode, at your alarm volume. If Do Not Disturb blocks alarms, they play at your " +
                "media volume instead.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ui.soundName ?: "Default bell", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "The same sound as the hourly bell",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PreviewButton(ui.previewing == Preview.Sadhana) { actions.togglePreview(Preview.Sadhana) }
        }
        VolumeRow(ui.sadhanaVolume, "Sadhana bell volume", actions::setSadhanaVolume)
    }
}

/** The running sadhana. Dark in both themes, with large numbers, so it reads at a glance. */
@Composable
private fun Running(session: Session, actions: SatiActions) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000 - now % 1_000)
        }
    }
    var confirmStop by remember { mutableStateOf(false) }
    val p = session.position(now)
    val lastStage = p.stage == session.sadhana.stages.lastIndex

    MaterialTheme(colorScheme = Dark) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(session.sadhana.name, style = MaterialTheme.typography.titleLarge)
                Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { p.progress },
                        modifier = Modifier.fillMaxSize(),
                        strokeWidth = 4.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(TimeText.countdown(p.toNextBellMs), style = MaterialTheme.typography.displayMedium)
                        Text(
                            when {
                                p.settling -> "until the start bell"
                                lastStage -> "until the end bell"
                                else -> "until the next bell"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(TimeText.stage(session.sadhana, p), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(
                    if (session.paused) "Paused · ${TimeText.countdown(p.totalLeftMs)} left in all"
                    else "${TimeText.countdown(p.totalLeftMs)} left in all",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = if (session.paused) actions::resumeSadhana else actions::pauseSadhana) {
                        Icon(
                            painterResource(if (session.paused) R.drawable.lucide_play else R.drawable.lucide_pause),
                            null, Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (session.paused) "Resume" else "Pause")
                    }
                    OutlinedButton(onClick = { confirmStop = true }) {
                        Icon(painterResource(R.drawable.lucide_square), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("End")
                    }
                }
            }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("End this sadhana?") },
            text = { Text("The bells stop, and the hourly bell comes back.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    actions.stopSadhana()
                }) { Text("End") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Keep going") } },
        )
    }
}
