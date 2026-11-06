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
            showUndo(context.getString(R.string.log_entry_deleted)) { LogController.undoDelete(context, entry) }
        }
    }

    val openedId = view.removePrefix(PRACTICE_PREFIX).takeIf { view.startsWith(PRACTICE_PREFIX) }
    val opened = openedId?.let { id -> practices?.firstOrNull { it.id == id } }
    // A practice that was deleted while its screen was open sends the user back.
    LaunchedEffect(openedId, practices) {
        if (openedId != null && practices != null && opened == null) view = TODAY
    }
    BackHandler(enabled = view != TODAY) { view = if (opened != null) origin else TODAY }

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
            onOpen = { openPractice(it.id, PRACTICES) },
            onAdd = { editor = "" },
            onEdit = { editor = it.id },
            onArchive = { p, archived -> LogController.scope.launch { LogController.setArchived(context, p.id, archived) } },
            onDelete = { deleting = it },
        )
        else -> TodayView(
            ui = ui,
            now = now,
            today = today,
            practices = practices,
            actions = actions,
            // Every row except a done one opens the form. A done row opens it when the practice asks for focus.
            // Otherwise it logs with one tap, or opens its entries once it is logged, as done or not done.
            onTap = { row ->
                val p = row.practice
                when {
                    p.measure != Measure.Done -> form = FormTarget(p.id, null)
                    row.logged -> openPractice(p.id, TODAY)
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
                    val entry = LogController.logNotDone(context, p.id) ?: return@launch
                    showUndo(context.getString(R.string.log_marked_not_done, p.name)) { LogController.deleteEntry(context, entry.id) }
                }
            },
            onEntries = { openPractice(it.id, TODAY) },
            onManage = { view = PRACTICES },
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
                onSave = { name, measure, target, askFocus ->
                    if (initial == null) LogController.addPractice(context, name, measure, target, askFocus)
                    else LogController.editPractice(context, initial.id, name, measure, target, askFocus)
                },
                onDismiss = { editor = null },
            )
        }
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
    onManage: () -> Unit,
    onAddPractice: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val entries by remember(today) { LogController.entriesOn(context, today) }.collectAsState(initial = emptyList())
    val total by remember { LogController.entryTotal(context) }.collectAsState(initial = 0)

    ImportCard(ui.logImport, actions)
    // Only the 30-day reminder sits at the top. The first ask is in the Backup card, so the rows do not jump after the first entry.
    if (ExportReminder.shouldRemind(ui.logLastExport, now, ZoneId.systemDefault())) {
        Notice(
            R.drawable.lucide_archive,
            pluralStringResource(R.plurals.log_reminder_old, ExportReminder.ASK_AFTER_DAYS, ExportReminder.ASK_AFTER_DAYS),
            warning = true,
            actionLabel = stringResource(R.string.log_export),
            onAction = actions::exportLog,
        )
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
            if (rows.isEmpty()) Notice(R.drawable.lucide_archive, stringResource(R.string.log_all_archived))
            rows.forEach { TodayCard(it, onClick = { onTap(it) }, onNotDone = { onNotDone(it) }, onEntries = { onEntries(it.practice) }) }
            OutlinedButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.lucide_list_checks), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.log_manage))
            }
        }
    }

    BackupCard(ui, now, actions, askFirst = ExportReminder.shouldAskFirst(ui.logLastExport, hasEntries = total > 0))
}

@Composable
private fun TodayCard(row: TodayRow, onClick: () -> Unit, onNotDone: () -> Unit, onEntries: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // Three states: done, marked not done, and nothing logged.
            Icon(
                painterResource(
                    when {
                        row.done -> R.drawable.lucide_circle_check
                        row.notDone -> R.drawable.lucide_circle_x
                        else -> R.drawable.lucide_circle
                    },
                ),
                stringResource(
                    when {
                        row.done -> R.string.log_done_mark
                        row.notDone -> R.string.log_not_done_mark
                        else -> R.string.log_nothing_logged_mark
                    },
                ),
                Modifier.size(28.dp),
                tint = when {
                    row.done -> MaterialTheme.colorScheme.primary
                    row.notDone -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                },
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.practice.name, style = MaterialTheme.typography.titleMedium)
                Text(progressText(row), style = MaterialTheme.typography.bodyMedium)
                val target = row.target
                if (target != null) {
                    LinearProgressIndicator(
                        progress = { (row.total.toFloat() / target).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                row.focusAverage?.let {
                    Text(
                        stringResource(R.string.log_focus_average, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // A done practice with nothing logged has a button to mark it not done in one tap.
            if (row.practice.measure == Measure.Done && !row.logged) {
                IconButton(onClick = onNotDone) {
                    Icon(
                        painterResource(R.drawable.lucide_circle_x),
                        stringResource(R.string.log_mark_not_done, row.practice.name),
                        Modifier.size(20.dp),
                    )
                }
            }
            // A separate 48 dp button, so the entries are always one tap away, whatever a tap on the row does.
            IconButton(onClick = onEntries) {
                Icon(
                    painterResource(R.drawable.lucide_list_checks),
                    stringResource(R.string.log_open_entries, row.practice.name),
                    Modifier.size(20.dp),
                )
            }
        }
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

/**
 * When the last export was, and the two buttons for the Storage Access Framework.
 * With [askFirst], a warning says the log has no copy yet.
 */
@Composable
private fun BackupCard(ui: SatiUi, now: Long, actions: SatiActions, askFirst: Boolean) {
    SectionCard(R.drawable.lucide_download, stringResource(R.string.log_backup_title)) {
        val days = ExportReminder.daysSince(ui.logLastExport, now, ZoneId.systemDefault())
        if (askFirst) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.lucide_triangle_alert), null, Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.log_reminder_first), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            Text(
                when (days) {
                    null -> stringResource(R.string.log_never_exported)
                    0 -> stringResource(R.string.log_exported_today)
                    else -> pluralStringResource(R.plurals.log_exported_days, days, days)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Text(stringResource(R.string.log_backup_body), style = MaterialTheme.typography.bodySmall)
        if (ui.logBusy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.log_working), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The first export is the one that matters most, so its button is the strong one.
            val exportContent: @Composable () -> Unit = {
                Icon(painterResource(R.drawable.lucide_download), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.log_export))
            }
            if (askFirst) {
                Button(onClick = actions::exportLog, enabled = !ui.logBusy) { exportContent() }
            } else {
                FilledTonalButton(onClick = actions::exportLog, enabled = !ui.logBusy) { exportContent() }
            }
            OutlinedButton(onClick = actions::importLog, enabled = !ui.logBusy) {
                Icon(painterResource(R.drawable.lucide_upload), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.log_import))
            }
        }
    }
}

// ---------- One practice ----------

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
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

// ---------- Practices list ----------

@Composable
private fun PracticesView(
    practices: List<Practice>,
    onBack: () -> Unit,
    onOpen: (Practice) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Practice) -> Unit,
    onArchive: (Practice, Boolean) -> Unit,
    onDelete: (Practice) -> Unit,
) {
    SubHeader(stringResource(R.string.log_practices_title), onBack)
    // At the limit, every export can still be imported back, so no more can be added.
    val full = practices.size >= PracticeRules.MAX_PRACTICES
    Button(onClick = onAdd, enabled = !full, modifier = Modifier.fillMaxWidth()) {
        Icon(painterResource(R.drawable.lucide_plus), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.log_add_practice))
    }
    if (full) {
        Text(
            pluralStringResource(R.plurals.log_practice_limit, PracticeRules.MAX_PRACTICES, PracticeRules.MAX_PRACTICES),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
    if (practices.isEmpty()) {
        Text(stringResource(R.string.log_no_practices), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(4.dp))
    }
    practices.forEach { p ->
        Card(
            onClick = { onOpen(p) },
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    val target = p.target
                    val line = if (target != null && PracticeRules.hasTarget(p.measure)) {
                        stringResource(R.string.log_measure_line_target, measureName(p.measure), amountText(p.measure, target))
                    } else {
                        measureName(p.measure)
                    }
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                    if (p.archived) {
                        Text(
                            stringResource(R.string.log_archived),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { onEdit(p) }) {
                    Icon(painterResource(R.drawable.lucide_pencil), stringResource(R.string.log_edit_practice, p.name), Modifier.size(20.dp))
                }
                IconButton(onClick = { onArchive(p, !p.archived) }) {
                    Icon(
                        painterResource(if (p.archived) R.drawable.lucide_archive_restore else R.drawable.lucide_archive),
                        stringResource(if (p.archived) R.string.log_return_practice else R.string.log_archive_practice, p.name),
                        Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { onDelete(p) }) {
                    Icon(painterResource(R.drawable.lucide_trash_2), stringResource(R.string.log_delete_practice, p.name), Modifier.size(20.dp))
                }
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
