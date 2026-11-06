@file:OptIn(ExperimentalMaterial3Api::class)

package com.padakanti.sati.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.padakanti.sati.R
import com.padakanti.sati.log.DurationText
import com.padakanti.sati.log.Entry
import com.padakanti.sati.log.EntryProblem
import com.padakanti.sati.log.EntryRules
import com.padakanti.sati.log.Focus
import com.padakanti.sati.log.LogController
import com.padakanti.sati.log.LogDay
import com.padakanti.sati.log.Measure
import com.padakanti.sati.log.Practice
import com.padakanti.sati.log.PracticeProblem
import com.padakanti.sati.log.PracticeRules
import com.padakanti.sati.log.Prefill
import kotlinx.coroutines.launch

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * The short form for one entry, as a bottom sheet. The value is filled in.
 * When the practice asks for focus, a tap on a focus button saves, and "No
 * score" saves without one. Otherwise one Save button saves, or a Done button
 * for a done practice. A scale practice picks its value in a row of ten
 * buttons first. A duration has a Minutes and a Seconds field. The outlined
 * "Not done" button saves value 0 with no focus, for every measure. An entry
 * that is not done opens like a new one, so it can become a real entry.
 * When an entry is edited, "Add another entry" switches to a new one.
 * [onSubmit] returns what is wrong, or null when the entry was saved.
 */
@Composable
internal fun EntrySheet(
    practice: Practice,
    entry: Entry?,
    today: Long,
    onSubmit: suspend (day: Long, value: Int?, focus: Int?, note: String) -> EntryProblem?,
    onAddAnother: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val uiScope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val measure = practice.measure
    val isDone = measure == Measure.Done
    val isScale = measure == Measure.Scale
    val isDuration = measure == Measure.Duration
    // Every piece of state starts again when the sheet switches from an entry to a new one.
    val key = entry?.id ?: ""
    // The entry whose values fill the form. A not-done entry has none, so the form starts as for a new entry.
    val real = entry?.takeIf { it.value != EntryRules.NOT_DONE_VALUE }
    // The target fills the value at once. Only a practice without a target waits for the last value.
    val start = real?.value ?: practice.target
    val startFields = when {
        start == null -> "" to ""
        isDuration -> DurationText.fieldsOf(start)
        else -> start.toString() to ""
    }
    var day by rememberSaveable(key) { mutableLongStateOf(entry?.day ?: today) }
    var valueText by rememberSaveable(key) { mutableStateOf(startFields.first) }
    var secondsText by rememberSaveable(key) { mutableStateOf(startFields.second) }
    var ready by rememberSaveable(key) { mutableStateOf(real != null || isDone || isScale || practice.target != null) }
    // The chosen value of a scale practice.
    var scaleValue by rememberSaveable(key) { mutableStateOf(if (isScale) real?.value else null) }
    var note by rememberSaveable(key) { mutableStateOf(entry?.note ?: "") }
    var noteShown by rememberSaveable(key) { mutableStateOf(!entry?.note.isNullOrEmpty()) }
    var problem by remember(key) { mutableStateOf<EntryProblem?>(null) }
    var picking by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val asksFocus = practice.askFocus
    // With no focus row, an edit keeps the focus the entry already has.
    val keptFocus = real?.focus
    val value = when {
        isDone -> EntryRules.DONE_VALUE
        isScale -> scaleValue
        isDuration -> DurationText.fromFields(valueText, secondsText)
        else -> valueText.toIntOrNull()
    }
    val canSave = ready && !saving && (!isScale || scaleValue != null)

    // The value starts as the target, else the last value, else empty. The save buttons wait for it.
    LaunchedEffect(practice.id, key) {
        if (ready) return@LaunchedEffect
        if (valueText.isEmpty() && secondsText.isEmpty()) {
            val last = Prefill.value(practice.target, LogController.lastValue(context, practice.id))
            if (last != null) {
                val fields = if (isDuration) DurationText.fieldsOf(last) else last.toString() to ""
                valueText = fields.first
                secondsText = fields.second
            }
        }
        ready = true
    }

    fun save(focus: Int?, savedValue: Int? = value, notDone: Boolean = false) {
        if (saving || !ready) return
        // A value of 0 can only come from the Not done button, so a typed 0 is not saved by mistake.
        if (!notDone && savedValue == EntryRules.NOT_DONE_VALUE) {
            problem = EntryProblem.BadValue
            return
        }
        saving = true
        // The write runs in the controller's scope, so leaving the screen does not stop it.
        LogController.scope.launch {
            var problemFound: EntryProblem? = null
            var finished = false
            try {
                problemFound = onSubmit(day, savedValue, focus, note)
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
                // The sheet pads for the system bars by itself, and an inset that is already used counts as zero here.
                // This makes sure the last button clears the navigation bar, and the keyboard when it is open.
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (entry == null) stringResource(R.string.log_entry_new_title, practice.name) else stringResource(R.string.log_entry_edit_title),
                style = MaterialTheme.typography.titleLarge,
            )
            if (entry != null) {
                TextButton(onClick = onAddAnother) { Text(stringResource(R.string.log_add_another)) }
            }
            if (isDuration) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { v -> valueText = v.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.log_value_minutes)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = secondsText,
                        onValueChange = { v -> secondsText = v.filter(Char::isDigit).take(2) },
                        label = { Text(stringResource(R.string.log_value_seconds)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            } else if (!isDone && !isScale) {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { v -> valueText = v.filter(Char::isDigit).take(if (measure == Measure.Percent) 3 else 5) },
                    label = { Text(stringResource(if (measure == Measure.Percent) R.string.log_value_percent else R.string.log_value_cycles)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (isScale) {
                Text(stringResource(R.string.log_score), style = MaterialTheme.typography.titleSmall)
                FocusRow(
                    selected = scaleValue,
                    enabled = ready && !saving,
                    descriptionRes = R.string.log_score_button,
                    // With a focus row below, the tap only selects. Without one, it saves.
                    onPick = { n -> if (asksFocus) scaleValue = n else save(keptFocus, savedValue = n) },
                )
            }
            if (asksFocus) {
                Text(stringResource(R.string.log_focus), style = MaterialTheme.typography.titleSmall)
                FocusRow(selected = real?.focus, enabled = canSave, descriptionRes = R.string.log_focus_button, onPick = { save(it) })
                OutlinedButton(onClick = { save(null) }, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
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
            if (asksFocus) {
                // Editing: this keeps the old score, so a change of note or value does not wipe it.
                if (real != null) {
                    Button(onClick = { save(real.focus) }, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.log_save_keep_focus))
                    }
                }
            } else if (!(isScale && !asksFocus)) {
                // A scale practice without a focus row has already saved on the tap of its value.
                Button(onClick = { save(keptFocus) }, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (isDone) R.string.log_done else R.string.log_save))
                }
            }
            OutlinedButton(
                onClick = { save(null, savedValue = EntryRules.NOT_DONE_VALUE, notDone = true) },
                enabled = ready && !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.log_not_done))
            }
            problem?.let {
                Text(entryProblemText(it, measure), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (picking) {
        DayPicker(day = day, today = today, onPick = { day = it; picking = false }, onDismiss = { picking = false })
    }
}

/** One radio row, at least 48 dp tall. A vertical list of these fits any width and any font size. */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
    }
}

/**
 * Ten buttons, 1 to 10, in two rows of five. Each is at least 48 dp wide and
 * tall. A tap calls [onPick]. [selected] is the current number when an entry is edited.
 * [descriptionRes] is the spoken label of a button, with the number in it.
 */
@Composable
private fun FocusRow(selected: Int?, enabled: Boolean, @StringRes descriptionRes: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Focus.MIN..5, 6..Focus.MAX).forEach { numbers ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                numbers.forEach { n ->
                    val description = stringResource(descriptionRes, n)
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

/** A calendar that allows today and any past day. It allows a future day only with [allowFuture]. */
@Composable
private fun DayPicker(day: Long, today: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit, allowFuture: Boolean = false) {
    // The picker counts in UTC milliseconds at midnight, so a day number converts by plain multiplication.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = day * MILLIS_PER_DAY,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = allowFuture || Math.floorDiv(utcTimeMillis, MILLIS_PER_DAY) <= today
            override fun isSelectableYear(year: Int) = allowFuture || year <= LogDay.date(today).year
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

private fun measureOf(index: Int): Measure = Measure.entries[index]

/** A full-screen editor for a new practice, or for [initial]. [onSave] returns what is wrong, or null when it was saved. */
@Composable
internal fun PracticeEditor(
    initial: Practice?,
    today: Long,
    onSave: suspend (name: String, measure: Measure, target: Int?, askFocus: Boolean, everyDays: Int, scheduleStart: Long) -> PracticeProblem?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val uiScope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var measureIndex by rememberSaveable { mutableIntStateOf((initial?.measure ?: Measure.Duration).ordinal) }
    // A duration target has two fields, minutes and seconds. Other targets use the first one.
    val startTarget = initial?.target?.let { if (initial.measure == Measure.Duration) DurationText.fieldsOf(it) else it.toString() to "" }
    var targetText by rememberSaveable { mutableStateOf(startTarget?.first ?: "") }
    var targetSecondsText by rememberSaveable { mutableStateOf(startTarget?.second ?: "") }
    var everyDays by rememberSaveable { mutableIntStateOf(initial?.everyDays ?: 1) }
    var scheduleStart by rememberSaveable { mutableLongStateOf(initial?.scheduleStart ?: today) }
    var pickingStart by remember { mutableStateOf(false) }
    // The focus switch follows the measure's default until the user touches it.
    var askFocus by rememberSaveable { mutableStateOf(initial?.askFocus ?: PracticeRules.defaultAskFocus(measureOf(measureIndex))) }
    var focusTouched by rememberSaveable { mutableStateOf(false) }
    var problem by remember { mutableStateOf<PracticeProblem?>(null) }
    var hasEntries by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val measure = measureOf(measureIndex)

    // The measure can change only while there are no entries.
    LaunchedEffect(initial?.id) {
        hasEntries = initial != null && LogController.entryCount(context, initial.id) > 0
    }

    fun save() {
        if (saving) return
        val isDuration = measure == Measure.Duration
        val target = if (isDuration) DurationText.fromFields(targetText, targetSecondsText) else targetText.toIntOrNull()
        val typed = targetText.isNotEmpty() || (isDuration && targetSecondsText.isNotEmpty())
        // A target text that is not a number is a problem, not "no target".
        if (typed && target == null) {
            problem = PracticeProblem.TargetInvalid
            return
        }
        saving = true
        // The write runs in the controller's scope, so leaving the screen does not stop it.
        LogController.scope.launch {
            var found: PracticeProblem? = null
            var finished = false
            try {
                found = onSave(name, measure, target, askFocus, everyDays, scheduleStart)
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
                    // A vertical list, because five measures do not fit in one row at a large font size.
                    Column(Modifier.selectableGroup()) {
                        Measure.entries.forEach { m ->
                            ChoiceRow(
                                measureName(m),
                                selected = measure == m,
                                enabled = !hasEntries || m == initial?.measure,
                            ) {
                                if (m != measure) {
                                    // A target in seconds means nothing to another measure, so it starts again.
                                    targetText = ""
                                    targetSecondsText = ""
                                }
                                measureIndex = m.ordinal
                                if (!focusTouched) askFocus = PracticeRules.defaultAskFocus(m)
                            }
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
                if (measure == Measure.Duration) {
                    Text(stringResource(R.string.log_target_label), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = targetText,
                            onValueChange = { v -> targetText = v.filter(Char::isDigit).take(4) },
                            label = { Text(stringResource(R.string.log_value_minutes)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = targetSecondsText,
                            onValueChange = { v -> targetSecondsText = v.filter(Char::isDigit).take(2) },
                            label = { Text(stringResource(R.string.log_value_seconds)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else if (PracticeRules.hasTarget(measure)) {
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { v -> targetText = v.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.log_target_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                RepeatControl(
                    everyDays = everyDays,
                    start = scheduleStart,
                    today = today,
                    onEveryDays = { everyDays = it },
                    onChangeStart = { pickingStart = true },
                )
                if (pickingStart) {
                    DayPicker(
                        day = scheduleStart,
                        today = today,
                        onPick = { scheduleStart = it; pickingStart = false },
                        onDismiss = { pickingStart = false },
                        allowFuture = true,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .toggleable(value = askFocus, role = Role.Switch, onValueChange = { askFocus = it; focusTouched = true }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.log_ask_focus), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = askFocus, onCheckedChange = null)
                }
                problem?.let { Text(practiceProblemText(it, measure), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

/**
 * How often a practice is due: every day, or every 2 to 7 days from a start day. The line under it says
 * whether the practice is due today, or when it is next due.
 */
@Composable
private fun RepeatControl(everyDays: Int, start: Long, today: Long, onEveryDays: (Int) -> Unit, onChangeStart: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.log_repeat), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            ChoiceRow(stringResource(R.string.log_every_day), selected = everyDays == 1) { onEveryDays(1) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    val shown = maxOf(everyDays, 2)
                    ChoiceRow(pluralStringResource(R.plurals.log_every_n_days, shown, shown), selected = everyDays > 1) {
                        if (everyDays == 1) onEveryDays(2)
                    }
                }
                IconButton(onClick = { onEveryDays(everyDays - 1) }, enabled = everyDays > 2) {
                    Icon(painterResource(R.drawable.lucide_minus), stringResource(R.string.log_fewer_days), Modifier.size(20.dp))
                }
                IconButton(onClick = { onEveryDays(everyDays + 1) }, enabled = everyDays in 2 until PracticeRules.MAX_EVERY_DAYS) {
                    Icon(painterResource(R.drawable.lucide_plus), stringResource(R.string.log_more_days), Modifier.size(20.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.log_starts_on), style = MaterialTheme.typography.titleSmall)
                Text(dayLabel(start, today), style = MaterialTheme.typography.bodyLarge)
            }
            TextButton(onClick = onChangeStart) { Text(stringResource(R.string.log_change_day)) }
        }
        Text(
            if (PracticeRules.isDue(everyDays, start, today)) stringResource(R.string.log_due_today)
            else stringResource(R.string.log_next_due, dayLabel(PracticeRules.nextDue(everyDays, start, today), today)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                    Column(Modifier.selectableGroup()) {
                        Text(name, style = MaterialTheme.typography.titleSmall)
                        Measure.entries.forEach { m ->
                            ChoiceRow(measureName(m), selected = chosen[name] == m) { chosen[name] = m }
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
