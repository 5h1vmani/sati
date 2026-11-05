@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.padakanti.sati.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.padakanti.sati.R
import com.padakanti.sati.log.Entry
import com.padakanti.sati.log.EntryProblem
import com.padakanti.sati.log.EntryRules
import com.padakanti.sati.log.Focus
import com.padakanti.sati.log.LogController
import com.padakanti.sati.log.LogDay
import com.padakanti.sati.log.Measure
import com.padakanti.sati.log.Practice
import com.padakanti.sati.log.PracticeProblem
import com.padakanti.sati.log.Prefill
import kotlinx.coroutines.launch

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * The short form for one entry, as a bottom sheet. The value is filled in.
 * A tap on a focus button saves. A done practice has no value and no focus,
 * so it has one Save button. [onSubmit] returns what is wrong, or null when
 * the entry was saved.
 */
@Composable
internal fun EntrySheet(
    practice: Practice,
    entry: Entry?,
    today: Long,
    onSubmit: suspend (day: Long, value: Int?, focus: Int?, note: String) -> EntryProblem?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val uiScope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDone = practice.measure == Measure.Done
    var day by rememberSaveable { mutableLongStateOf(entry?.day ?: today) }
    // The target fills the value at once. Only a practice without a target waits for the last value.
    var valueText by rememberSaveable { mutableStateOf(entry?.value?.toString() ?: practice.target?.toString() ?: "") }
    var ready by rememberSaveable { mutableStateOf(entry != null || isDone || practice.target != null) }
    var note by rememberSaveable { mutableStateOf(entry?.note ?: "") }
    var noteShown by rememberSaveable { mutableStateOf(!entry?.note.isNullOrEmpty()) }
    var problem by remember { mutableStateOf<EntryProblem?>(null) }
    var picking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // The value starts as the target, else the last value, else empty. The save buttons wait for it.
    LaunchedEffect(practice.id) {
        if (ready) return@LaunchedEffect
        if (valueText.isEmpty()) {
            valueText = Prefill.value(practice.target, LogController.lastValue(context, practice.id))?.toString() ?: ""
        }
        ready = true
    }

    fun save(focus: Int?) {
        if (saving || !ready) return
        saving = true
        // The write runs in the controller's scope, so leaving the screen does not stop it.
        LogController.scope.launch {
            var problemFound: EntryProblem? = null
            var finished = false
            try {
                problemFound = onSubmit(day, if (isDone) EntryRules.DONE_VALUE else valueText.toIntOrNull(), focus, note)
                finished = true
            } finally {
                // If the write threw, the sheet must still work again, so saving is always reset.
                uiScope.launch {
                    saving = false
                    if (finished) {
                        if (problemFound == null) {
                            sheet.hide()
                            onDismiss()
                        } else {
                            problem = problemFound
                        }
                    }
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (entry == null) stringResource(R.string.log_entry_new_title, practice.name) else stringResource(R.string.log_entry_edit_title),
                style = MaterialTheme.typography.titleLarge,
            )
            if (!isDone) {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { v -> valueText = v.filter(Char::isDigit).take(5) },
                    label = {
                        Text(stringResource(if (practice.measure == Measure.Minutes) R.string.log_value_minutes else R.string.log_value_cycles))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.log_focus), style = MaterialTheme.typography.titleSmall)
                FocusRow(selected = entry?.focus, enabled = ready && !saving, onPick = ::save)
                OutlinedButton(onClick = { save(null) }, enabled = ready && !saving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.log_no_score))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.lucide_calendar), null, Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(dayLabel(day, today), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { picking = true }) { Text(stringResource(R.string.log_change_day)) }
            }
            if (noteShown) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(EntryRules.MAX_NOTE) },
                    label = { Text(stringResource(R.string.log_note)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TextButton(onClick = { noteShown = true }) { Text(stringResource(R.string.log_add_note)) }
            }
            if (isDone) {
                Button(onClick = { save(null) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.log_save)) }
            } else if (entry != null) {
                // Editing: this keeps the old score, so a change of note or value does not wipe it.
                Button(onClick = { save(entry.focus) }, enabled = ready && !saving, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.log_save_keep_focus))
                }
            }
            problem?.let { Text(entryProblemText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    if (picking) {
        DayPicker(day = day, today = today, onPick = { day = it; picking = false }, onDismiss = { picking = false })
    }
}

/**
 * Ten buttons, 1 to 10, in two rows of five. Each is at least 48 dp wide and
 * tall. A tap calls [onPick]. [selected] is the current score when an entry is edited.
 */
@Composable
private fun FocusRow(selected: Int?, enabled: Boolean, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Focus.MIN..5, 6..Focus.MAX).forEach { numbers ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                numbers.forEach { n ->
                    val description = stringResource(R.string.log_focus_button, n)
                    Surface(
                        onClick = { onPick(n) },
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics { contentDescription = description },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected == n) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (selected == n) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Box(Modifier.defaultMinSize(minHeight = 48.dp), contentAlignment = Alignment.Center) {
                            // One line, so "10" never wraps at a large font size.
                            Text("$n", style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

/** A calendar that allows today and any past day, never a future day. */
@Composable
private fun DayPicker(day: Long, today: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    // The picker counts in UTC milliseconds at midnight, so a day number converts by plain multiplication.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = day * MILLIS_PER_DAY,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = Math.floorDiv(utcTimeMillis, MILLIS_PER_DAY) <= today
            override fun isSelectableYear(year: Int) = year <= LogDay.date(today).year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(Math.floorDiv(it, MILLIS_PER_DAY)) } ?: onDismiss() }) {
                Text(stringResource(R.string.log_pick_day))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.log_cancel)) } },
    ) { DatePicker(state = state) }
}

/** A full-screen editor for a new practice, or for [initial]. [onSave] returns what is wrong, or null when it was saved. */
@Composable
internal fun PracticeEditor(
    initial: Practice?,
    onSave: suspend (name: String, measure: Measure, target: Int?) -> PracticeProblem?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val uiScope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var measureIndex by rememberSaveable { mutableIntStateOf((initial?.measure ?: Measure.Minutes).ordinal) }
    var targetText by rememberSaveable { mutableStateOf(initial?.target?.toString() ?: "") }
    var problem by remember { mutableStateOf<PracticeProblem?>(null) }
    var hasEntries by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val measure = Measure.entries[measureIndex]

    // The measure can change only while there are no entries.
    LaunchedEffect(initial?.id) {
        hasEntries = initial != null && LogController.entryCount(context, initial.id) > 0
    }

    fun save() {
        if (saving) return
        val target = targetText.toIntOrNull()
        // A target text that is not a number is a problem, not "no target".
        if (targetText.isNotEmpty() && target == null) {
            problem = PracticeProblem.TargetInvalid
            return
        }
        saving = true
        // The write runs in the controller's scope, so leaving the screen does not stop it.
        LogController.scope.launch {
            var found: PracticeProblem? = null
            var finished = false
            try {
                found = onSave(name, measure, target)
                finished = true
            } finally {
                uiScope.launch {
                    saving = false
                    if (finished) {
                        problem = found
                        if (found == null) onDismiss()
                    }
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(stringResource(if (initial == null) R.string.log_practice_new_title else R.string.log_practice_edit_title))
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(painterResource(R.drawable.lucide_x), stringResource(R.string.log_close_editor), Modifier.size(20.dp))
                        }
                    },
                    actions = { TextButton(onClick = ::save) { Text(stringResource(R.string.log_save)) } },
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
                    label = { Text(stringResource(R.string.log_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.log_measure), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Measure.entries.forEach { m ->
                            FilterChip(
                                selected = measure == m,
                                onClick = { measureIndex = m.ordinal },
                                label = { Text(measureName(m)) },
                                enabled = !hasEntries || m == initial?.measure,
                            )
                        }
                    }
                    if (hasEntries) {
                        Text(
                            stringResource(R.string.log_measure_locked),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (measure != Measure.Done) {
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { v -> targetText = v.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.log_target_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                problem?.let { Text(practiceProblemText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

/** Asks for the measure of each practice name an import does not know. The import runs only after every name has one. */
@Composable
internal fun MeasureDialog(names: List<String>, onImport: (Map<String, Measure>) -> Unit, onCancel: () -> Unit) {
    val chosen = remember { mutableStateMapOf<String, Measure>() }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.log_import_ask_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.log_import_ask_body), style = MaterialTheme.typography.bodyMedium)
                names.forEach { name ->
                    Column {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Measure.entries.forEach { m ->
                                FilterChip(
                                    selected = chosen[name] == m,
                                    onClick = { chosen[name] = m },
                                    label = { Text(measureName(m)) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(chosen.toMap()) }, enabled = names.all { it in chosen }) {
                Text(stringResource(R.string.log_import_start))
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.log_cancel)) } },
    )
}
