package com.padakanti.sati.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.padakanti.sati.R
import com.padakanti.sati.log.Entry
import com.padakanti.sati.log.ExportReminder
import com.padakanti.sati.log.LogController
import com.padakanti.sati.log.LogDay
import com.padakanti.sati.log.LogImportState
import com.padakanti.sati.log.Measure
import com.padakanti.sati.log.Practice
import com.padakanti.sati.log.PracticeRules
import com.padakanti.sati.log.Today
import com.padakanti.sati.log.TodayRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId

/** Entries shown at a time on a practice screen. A year of daily entries stays quick. */
private const val PAGE = 60

/** How long the Undo snackbar stays, before Android's "time to take action" setting changes it. */
private const val UNDO_MILLIS = 5_000L

private const val TODAY = "today"
private const val PRACTICES = "practices"
private const val PRACTICE_PREFIX = "practice:"

/** The open entry form: a practice, and the entry when one is edited. Kept across a screen rotation. */
private class FormTarget(val practiceId: String, val entry: Entry?)

private val FormTargetSaver = Saver<FormTarget?, List<Any?>>(
    save = { t ->
        if (t == null) emptyList() else {
            val e = t.entry
            listOf(t.practiceId, e?.id, e?.day, e?.value, e?.focus, e?.note, e?.createdAt)
        }
    },
    restore = { l ->
        if (l.isEmpty()) null else {
            val practiceId = l[0] as String
            val id = l[1] as String?
            FormTarget(
                practiceId,
                id?.let { Entry(it, practiceId, l[2] as Long, l[3] as Int, l[4] as Int?, l[5] as String?, l[6] as Long) },
            )
        }
    },
)

/**
 * The practice log: Today, one practice's entries, and the practices list.
 * Reads come from the log's flows. Every change goes through [LogController].
 */
@Composable
internal fun LogTab(
    ui: SatiUi,
    now: Long,
    actions: SatiActions,
    snackbar: SnackbarHostState,
    /** Owned by the screen, not by this tab, so a switch of tab does not cancel an Undo snackbar. */
    scope: CoroutineScope,
) {
    val context = LocalContext.current.applicationContext
    val today = LogDay.of(now, ZoneId.systemDefault())
    val practices by remember { LogController.practices(context) }.collectAsState(initial = null)

    var view by rememberSaveable { mutableStateOf(TODAY) }
    var origin by rememberSaveable { mutableStateOf(TODAY) }
    var form by rememberSaveable(stateSaver = FormTargetSaver) { mutableStateOf<FormTarget?>(null) }
    // "" is a new practice, otherwise the id of the practice to edit. Null means closed.
    var editor by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Practice?>(null) }
    // Import first explains the file formats, then opens the file picker.
    var importHelp by remember { mutableStateOf(false) }

    // The user's "time to take action" setting can make the Undo last longer than the default.
    val undoMillis = LocalAccessibilityManager.current
        ?.calculateRecommendedTimeoutMillis(UNDO_MILLIS, containsIcons = false, containsText = true, containsControls = true)
        ?: UNDO_MILLIS

    /** Shows [message] with an Undo button for [undoMillis]. [undo] runs if the user taps it. */
    fun showUndo(message: String, undo: suspend () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            // The snackbar has no end of its own. Cancelling showSnackbar when the time is up dismisses it.
            val result = withTimeoutOrNull(undoMillis) {
                snackbar.showSnackbar(
                    message, actionLabel = context.getString(R.string.log_undo), duration = SnackbarDuration.Indefinite,
                )
            }
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    fun openPractice(id: String, from: String) {
        origin = from
        view = PRACTICE_PREFIX + id
    }

    fun deleteEntry(entry: Entry) {
        LogController.scope.launch {
            LogController.deleteEntry(context, entry.id)
            showUndo(context.getString(R.string.log_entry_deleted)) {
                // The practice may have been deleted, or its measure changed, while Undo was on screen.
                if (!LogController.undoDelete(context, entry)) snackbar.showSnackbar(context.getString(R.string.log_undo_failed))
            }
        }
    }

    val openedId = view.removePrefix(PRACTICE_PREFIX).takeIf { view.startsWith(PRACTICE_PREFIX) }
    val opened = openedId?.let { id -> practices?.firstOrNull { it.id == id } }
    // A practice that was deleted while its screen was open sends the user back.
    LaunchedEffect(openedId, practices) {
        if (openedId != null && practices != null && opened == null) view = TODAY
    }
    BackHandler(enabled = view != TODAY) { view = if (opened != null) origin else TODAY }

    ScreenHeading(stringResource(R.string.tab_log)) {
        if (opened == null && view == TODAY) {
            LogMenu(
                lastExport = ui.logLastExport,
                now = now,
                busy = ui.logBusy,
                onManage = { view = PRACTICES },
                onExport = actions::exportLog,
                onImport = { importHelp = true },
            )
        }
    }

    when {
        opened != null -> PracticeView(
            practice = opened,
            today = today,
            onBack = { view = origin },
            onAdd = { form = FormTarget(opened.id, null) },
            onEdit = { form = FormTarget(opened.id, it) },
            onDelete = ::deleteEntry,
        )
        view == PRACTICES -> PracticesView(
            practices = practices.orEmpty(),
            onBack = { view = TODAY },
            onAdd = { editor = "" },
            onEdit = { editor = it.id },
            onDrop = { id, index -> LogController.scope.launch { LogController.moveTo(context, id, index) } },
            onMove = { p, up -> LogController.scope.launch { LogController.movePractice(context, p.id, up) } },
        )
        else -> TodayView(
            ui = ui,
            now = now,
            today = today,
            practices = practices,
            actions = actions,
            // A logged row opens today's newest entry to edit. An unlogged row opens the form, except a done
            // practice that does not ask for focus, which logs with one tap.
            onTap = { row ->
                val p = row.practice
                when {
                    row.latest != null -> form = FormTarget(p.id, row.latest)
                    p.measure != Measure.Done -> form = FormTarget(p.id, null)
                    p.askFocus -> form = FormTarget(p.id, null)
                    else -> LogController.scope.launch {
                        val entry = LogController.logDone(context, p.id) ?: return@launch
                        showUndo(context.getString(R.string.log_marked_done, p.name)) { LogController.deleteEntry(context, entry.id) }
                    }
                }
            },
            onNotDone = { row ->
                val p = row.practice
                LogController.scope.launch {
                    val entry = LogController.markNotDone(context, p.id) ?: return@launch
                    showUndo(context.getString(R.string.log_marked_not_done, p.name)) { LogController.deleteEntry(context, entry.id) }
                }
            },
            onEntries = { openPractice(it.id, TODAY) },
            onAddPractice = { editor = "" },
        )
    }

    form?.let { target ->
        val practice = practices?.firstOrNull { it.id == target.practiceId }
        if (practice == null) {
            form = null
        } else {
            EntrySheet(
                practice = practice,
                entry = target.entry,
                today = today,
                onSubmit = { day, value, focus, note ->
                    val entry = target.entry
                    if (entry == null) LogController.addEntry(context, practice.id, day, value, focus, note).problem
                    else LogController.editEntry(context, entry, day, value, focus, note)
                },
                onAddAnother = { form = FormTarget(practice.id, null) },
                onDismiss = { form = null },
            )
        }
    }

    editor?.let { id ->
        val initial = practices?.firstOrNull { it.id == id }
        if (id.isNotEmpty() && initial == null) {
            editor = null
        } else {
            PracticeEditor(
                initial = initial,
                today = today,
                onSave = { name, measure, target, askFocus, everyDays, scheduleStart ->
                    if (initial == null) LogController.addPractice(context, name, measure, target, askFocus, everyDays, scheduleStart)
                    else LogController.editPractice(context, initial.id, name, measure, target, askFocus, everyDays, scheduleStart)
                },
                onArchive = { archived ->
                    editor = null
                    if (initial != null) LogController.scope.launch { LogController.setArchived(context, initial.id, archived) }
                },
                onDelete = {
                    editor = null
                    deleting = initial
                },
                onEntries = {
                    editor = null
                    if (initial != null) openPractice(initial.id, PRACTICES)
                },
                onDismiss = { editor = null },
            )
        }
    }

    if (importHelp) {
        AlertDialog(
            onDismissRequest = { importHelp = false },
            title = { Text(stringResource(R.string.log_import)) },
            text = { Text(stringResource(R.string.log_import_help)) },
            confirmButton = {
                TextButton(onClick = { importHelp = false; actions.importLog() }) { Text(stringResource(R.string.log_import_choose)) }
            },
            dismissButton = { TextButton(onClick = { importHelp = false }) { Text(stringResource(R.string.log_cancel)) } },
        )
    }

    deleting?.let { practice ->
        DeletePracticeDialog(
            practice = practice,
            onConfirm = {
                deleting = null
                LogController.scope.launch { LogController.deletePractice(context, practice.id) }
            },
            onDismiss = { deleting = null },
        )
    }

    (ui.logImport as? LogImportState.Asking)?.let { asking ->
        MeasureDialog(asking.names, onImport = actions::finishLogImport, onCancel = actions::dismissLogImport)
    }
}

// ---------- Today ----------

@Composable
private fun TodayView(
    ui: SatiUi,
    now: Long,
    today: Long,
    practices: List<Practice>?,
    actions: SatiActions,
    onTap: (TodayRow) -> Unit,
    onNotDone: (TodayRow) -> Unit,
    onEntries: (Practice) -> Unit,
    onAddPractice: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val entries by remember(today) { LogController.entriesOn(context, today) }.collectAsState(initial = emptyList())
    val total by remember { LogController.entryTotal(context) }.collectAsState(initial = 0)

    ImportCard(ui.logImport, actions)
    // Only the 30-day reminder sits at the top. The first ask is at the bottom, so the rows do not jump after the first entry.
    if (ExportReminder.shouldRemind(ui.logLastExport, now, ZoneId.systemDefault())) {
        Notice(
            R.drawable.lucide_archive,
            pluralStringResource(R.plurals.log_reminder_old, ExportReminder.ASK_AFTER_DAYS, ExportReminder.ASK_AFTER_DAYS),
            warning = true,
            actionLabel = stringResource(R.string.log_export),
            onAction = actions::exportLog,
        )
    }
    if (ui.logBusy) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.log_working), style = MaterialTheme.typography.bodyMedium)
        }
    }

    when {
        practices == null -> Unit
        practices.isEmpty() -> SectionCard(R.drawable.lucide_notebook_pen, stringResource(R.string.log_empty_title)) {
            Text(stringResource(R.string.log_empty_body), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAddPractice, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.lucide_plus), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.log_add_practice))
            }
        }
        else -> {
            val rows = Today.rows(practices, entries, today)
            if (rows.isEmpty()) {
                Notice(R.drawable.lucide_archive, stringResource(R.string.log_all_archived))
            } else {
                DaySummaryCard(Today.summary(rows), today)
                val sections = Today.sections(rows)
                TodaySection(null, sections.todo, today, onTap, onNotDone, onEntries)
                TodaySection(stringResource(R.string.log_section_logged), sections.logged, today, onTap, onNotDone, onEntries)
                TodaySection(stringResource(R.string.log_not_due_heading), sections.notDue, today, onTap, onNotDone, onEntries)
            }
        }
    }

    if (ExportReminder.shouldAskFirst(ui.logLastExport, hasEntries = total > 0)) {
        Notice(
            R.drawable.lucide_triangle_alert,
            stringResource(R.string.log_reminder_first),
            warning = true,
            actionLabel = stringResource(R.string.log_export),
            onAction = actions::exportLog,
        )
    }
}

/** The result or error of the last import, until the user dismisses it. */
@Composable
private fun ImportCard(state: LogImportState, actions: SatiActions) {
    when (state) {
        is LogImportState.Result -> Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.log_import_done_title), style = MaterialTheme.typography.titleMedium)
                Text(pluralStringResource(R.plurals.log_import_added, state.added, state.added), style = MaterialTheme.typography.bodyMedium)
                Text(
                    pluralStringResource(R.plurals.log_import_skipped, state.skipped, state.skipped),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state.practicesAdded > 0) {
                    Text(
                        pluralStringResource(R.plurals.log_import_practices, state.practicesAdded, state.practicesAdded),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.sadhanasAdded > 0) {
                    Text(
                        pluralStringResource(R.plurals.log_import_sadhanas_added, state.sadhanasAdded, state.sadhanasAdded),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.sadhanasSkipped > 0) {
                    Text(
                        pluralStringResource(R.plurals.log_import_sadhanas_skipped, state.sadhanasSkipped, state.sadhanasSkipped),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FilledTonalButton(onClick = actions::dismissLogImport) { Text(stringResource(R.string.log_dismiss)) }
            }
        }
        is LogImportState.Failed -> Notice(
            R.drawable.lucide_triangle_alert,
            importErrorText(state.error),
            warning = true,
            actionLabel = stringResource(R.string.log_dismiss),
            onAction = actions::dismissLogImport,
        )
        else -> Unit
    }
}

// ---------- One practice ----------

@Composable
internal fun SubHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.lucide_arrow_left), stringResource(R.string.log_back), Modifier.size(20.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

/** The entries of one practice, newest first. */
@Composable
private fun PracticeView(
    practice: Practice,
    today: Long,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val entries by remember(practice.id) { LogController.entriesOf(context, practice.id) }.collectAsState(initial = null)
    var shown by rememberSaveable { mutableIntStateOf(PAGE) }

    SubHeader(practice.name, onBack)
    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
        Icon(painterResource(R.drawable.lucide_plus), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.log_add_entry))
    }
    val list = entries
    if (list != null && list.isEmpty()) {
        Text(stringResource(R.string.log_no_entries), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(4.dp))
    }
    list?.take(shown)?.forEach { entry ->
        EntryCard(practice, entry, today, onEdit = { onEdit(entry) }, onDelete = { onDelete(entry) })
    }
    if (list != null && list.size > shown) {
        TextButton(onClick = { shown += PAGE }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.log_show_more)) }
    }
}

@Composable
private fun EntryCard(practice: Practice, entry: Entry, today: Long, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(dayLabel(entry.day, today), style = MaterialTheme.typography.titleSmall)
                Text(amountText(practice.measure, entry.value), style = MaterialTheme.typography.bodyLarge)
                entry.focus?.let { Text(stringResource(R.string.log_focus_value, it), style = MaterialTheme.typography.bodyMedium) }
                entry.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(painterResource(R.drawable.lucide_pencil), stringResource(R.string.log_edit_entry), Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.lucide_trash_2), stringResource(R.string.log_delete_entry), Modifier.size(20.dp))
            }
        }
    }
}

/** Asks before a delete, and says how many entries will go. */
@Composable
private fun DeletePracticeDialog(practice: Practice, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    var count by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(practice.id) { count = LogController.entryCount(context, practice.id) }
    val n = count
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.log_delete_title, practice.name)) },
        text = {
            if (n != null) {
                Text(if (n == 0) stringResource(R.string.log_delete_body_none) else pluralStringResource(R.plurals.log_delete_body, n, n))
            }
        },
        // The button waits for the count, so nobody confirms without seeing it.
        confirmButton = { TextButton(onClick = onConfirm, enabled = n != null) { Text(stringResource(R.string.log_delete_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.log_cancel)) } },
    )
}
