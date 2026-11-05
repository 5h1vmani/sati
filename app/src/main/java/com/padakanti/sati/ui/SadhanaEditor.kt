@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.padakanti.sati.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.padakanti.sati.R
import com.padakanti.sati.Sadhana
import com.padakanti.sati.SadhanaCheck
import com.padakanti.sati.SadhanaFlaw
import com.padakanti.sati.Stage
import com.padakanti.sati.TimeText

/** One stage while it is being edited. Minutes stay text until saved, so the field can be empty. */
private data class StageDraft(val minutes: String, val label: String)

/** A full-screen editor for one sadhana. Checks it with [Sadhana.problem] before saving. */
@Composable
internal fun SadhanaEditor(
    initial: Sadhana,
    isNew: Boolean,
    onSave: (Sadhana) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val flawId = stringResource(R.string.sadhana_flaw_id)
    val flawHidden = stringResource(R.string.sadhana_flaw_hidden)
    val flawLong = pluralStringResource(R.plurals.sadhana_flaw_long, SadhanaCheck.MAX_TEXT, SadhanaCheck.MAX_TEXT)
    var name by remember { mutableStateOf(initial.name) }
    val stages = remember { mutableStateListOf(*initial.stages.map { StageDraft("${it.minutes}", it.label) }.toTypedArray()) }
    var settle by remember { mutableIntStateOf(initial.settleSec) }
    var vibrateOnly by remember { mutableStateOf(initial.vibrateOnly) }
    var splitTotal by remember { mutableStateOf("${initial.totalMin}") }
    var problem by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        val s = SadhanaCheck.normalized(
            Sadhana(
                id = initial.id,
                name = name.trim(),
                stages = stages.map { Stage(it.minutes.toIntOrNull() ?: 0, it.label.trim()) },
                settleSec = settle,
                vibrateOnly = vibrateOnly,
            ),
        )
        problem = Sadhana.problem(s) ?: when (SadhanaCheck.flaw(s)) {
            null, SadhanaFlaw.Basic -> null
            SadhanaFlaw.BadId -> flawId
            SadhanaFlaw.HiddenCharacters -> flawHidden
            SadhanaFlaw.TooLong -> flawLong
        }
        if (problem == null) onSave(s)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(if (isNew) "New sadhana" else "Edit sadhana") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(painterResource(R.drawable.lucide_x), "Close without saving", Modifier.size(20.dp))
                        }
                    },
                    actions = { TextButton(onClick = ::save) { Text("Save") } },
                )
            },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Stages", style = MaterialTheme.typography.titleMedium)
                stages.forEachIndexed { i, stage ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = stage.minutes,
                            onValueChange = { v -> stages[i] = stage.copy(minutes = v.filter(Char::isDigit).take(3)) },
                            label = { Text("Minutes") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(110.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = stage.label,
                            onValueChange = { v -> stages[i] = stage.copy(label = v) },
                            label = { Text("Label (optional)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { stages.removeAt(i) }, enabled = stages.size > 1) {
                            Icon(painterResource(R.drawable.lucide_trash_2), "Remove stage ${i + 1}", Modifier.size(20.dp))
                        }
                    }
                }
                if (stages.size < Sadhana.MAX_STAGES) {
                    TextButton(onClick = { stages.add(StageDraft(stages.lastOrNull()?.minutes ?: "10", "")) }) {
                        Icon(painterResource(R.drawable.lucide_plus), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add stage")
                    }
                }

                SectionCard(R.drawable.lucide_timer, "Split evenly") {
                    Text(
                        "Divide a total time into equal stages. This replaces the stages above.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = splitTotal,
                            onValueChange = { v -> splitTotal = v.filter(Char::isDigit).take(3) },
                            label = { Text("Total minutes") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(140.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        listOf(2, 3, 4).forEach { parts ->
                            TextButton(onClick = {
                                val split = Sadhana.splitEvenly(splitTotal.toIntOrNull() ?: 0, parts)
                                if (split == null) {
                                    problem = "Each stage needs at least 1 minute."
                                } else {
                                    val labels = stages.map { it.label }
                                    stages.clear()
                                    split.forEachIndexed { i, st -> stages.add(StageDraft("${st.minutes}", labels.getOrElse(i) { "" })) }
                                    problem = null
                                }
                            }) { Text("× $parts") }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Settling time", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "A quiet pause before the start bell, to put the phone down and close your eyes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Sadhana.SETTLE_CHOICES.forEach { sec ->
                            FilterChip(
                                selected = settle == sec,
                                onClick = { settle = sec },
                                label = { Text(if (sec == 0) "None" else TimeText.settle(sec)) },
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Vibrate only", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "For shared rooms. The bells vibrate instead of ringing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = vibrateOnly, onCheckedChange = { vibrateOnly = it })
                }

                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

                if (!isNew) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(painterResource(R.drawable.lucide_trash_2), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete sadhana")
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this sadhana?") },
            text = { Text("\"${initial.name}\" is removed from your list.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
