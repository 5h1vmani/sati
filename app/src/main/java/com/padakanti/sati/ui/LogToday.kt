package com.padakanti.sati.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.padakanti.sati.R
import com.padakanti.sati.log.DaySummary
import com.padakanti.sati.log.ExportReminder
import com.padakanti.sati.log.LogDay
import com.padakanti.sati.log.Measure
import com.padakanti.sati.log.NextDueWording
import com.padakanti.sati.log.Practice
import com.padakanti.sati.log.RowStatus
import com.padakanti.sati.log.Analytics
import com.padakanti.sati.log.Today
import com.padakanti.sati.log.TodayRow
import com.padakanti.sati.log.Track
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** The Today part of the Log tab: the header menu, the day summary, and the sections of rows. */

/** The 48 dp button at the right edge of the heading, with its menu. */
@Composable
internal fun LogMenu(lastExport: Long, now: Long, busy: Boolean, onManage: () -> Unit, onExport: () -> Unit, onImport: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.lucide_ellipsis_vertical), stringResource(R.string.log_more_options), Modifier.size(24.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.log_manage)) }, enabled = !busy, onClick = { open = false; onManage() })
            DropdownMenuItem(text = { Text(stringResource(R.string.log_export)) }, enabled = !busy, onClick = { open = false; onExport() })
            DropdownMenuItem(text = { Text(stringResource(R.string.log_import)) }, enabled = !busy, onClick = { open = false; onImport() })
            HorizontalDivider()
            val days = ExportReminder.daysSince(lastExport, now, ZoneId.systemDefault())
            DropdownMenuItem(
                text = {
                    Text(
                        when (days) {
                            null -> stringResource(R.string.log_menu_never_exported)
                            0 -> stringResource(R.string.log_menu_exported_today)
                            else -> pluralStringResource(R.plurals.log_menu_exported_days, days, days)
                        },
                    )
                },
                enabled = false,
                onClick = {},
            )
        }
    }
}

// ---------- the day summary ----------

/** The log day as "Today, Fri 6 Nov". */
@Composable
private fun summaryDay(today: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
    return stringResource(R.string.log_summary_day, LogDay.date(today).format(DateTimeFormatter.ofPattern(pattern, locale)))
}

/**
 * The day card: today's count and bar, then at most two sentences about the last 7 days.
 * The two parts are read by a screen reader as two separate items.
 */
@Composable
internal fun DaySummaryCard(summary: DaySummary, today: Long, week: List<String>) {
    val day = summaryDay(today)
    val title = when {
        summary.nothingDue -> stringResource(R.string.log_summary_none)
        summary.allLogged -> stringResource(R.string.log_summary_all)
        else -> stringResource(R.string.log_summary_title, summary.done, summary.due)
    }
    // Only the parts above 0 are shown, and none when everything is logged.
    val parts = buildList {
        if (summary.notDone > 0) add(stringResource(R.string.log_summary_not_done, summary.notDone))
        if (summary.left > 0) add(stringResource(R.string.log_summary_left, summary.left))
    }
    val side = parts.joinToString(stringResource(R.string.log_separator))
    val description = stringResource(
        R.string.log_summary_description,
        day,
        if (side.isEmpty()) title else stringResource(R.string.log_summary_description, title, side),
    )
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            Modifier.semantics(mergeDescendants = true) { contentDescription = description }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (summary.allLogged) {
                    Icon(painterResource(R.drawable.lucide_circle_check), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (side.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Text(side, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SummaryBar(summary, Modifier.padding(top = 4.dp))
        }
        if (week.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                Modifier.semantics(mergeDescendants = true) {}.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(stringResource(R.string.log_last_7_days), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                week.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

/** 8 dp tall, with rounded ends. Done, not done and left are sized by their counts. */
@Composable
private fun SummaryBar(summary: DaySummary, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(colors.surfaceVariant)) {
        if (summary.done > 0) Box(Modifier.weight(summary.done.toFloat()).height(8.dp).background(colors.primary))
        if (summary.notDone > 0) Box(Modifier.weight(summary.notDone.toFloat()).height(8.dp).background(colors.error.copy(alpha = 0.6f)))
        if (summary.left > 0) Box(Modifier.weight(summary.left.toFloat()).height(8.dp))
    }
}

// ---------- sections and rows ----------

/** A rounded surface that holds a list of rows. Rows are separated by thin dividers that start at [dividerInset]. */
@Composable
internal fun ListSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) { content() }
}

@Composable
internal fun RowDivider(inset: androidx.compose.ui.unit.Dp) {
    HorizontalDivider(Modifier.padding(start = inset), color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * One section of Today. It shows nothing when [rows] is empty. The [label] is small text above the surface.
 * [tracks] give each row its week dots. With [letters], the weekday letters stand over the dots.
 */
@Composable
internal fun TodaySection(
    label: String?,
    rows: List<TodayRow>,
    today: Long,
    tracks: Map<String, Track>,
    letters: Boolean,
    onTap: (TodayRow) -> Unit,
    onNotDone: (TodayRow) -> Unit,
    onEntries: (Practice) -> Unit,
) {
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        }
        ListSurface {
            if (letters) WeekLetters(today, Modifier.padding(start = ROW_TEXT_INSET, top = 10.dp))
            rows.forEachIndexed { index, row ->
                if (index > 0) RowDivider(ROW_TEXT_INSET)
                TodayRowItem(row, today, tracks[row.practice.id], { onTap(row) }, { onNotDone(row) }, { onEntries(row.practice) })
            }
        }
    }
}

/** Where the text of a row starts: the start padding, the 28 dp indicator and the gap. */
private val ROW_TEXT_INSET = 60.dp

@Composable
private fun TodayRowItem(row: TodayRow, today: Long, track: Track?, onClick: () -> Unit, onNotDone: () -> Unit, onEntries: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusIndicator(row)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(row.practice.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val secondary = @Composable { m: Modifier ->
                Text(secondaryText(row, today), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = m)
            }
            if (track == null) {
                secondary(Modifier)
            } else {
                // The dots start where the name starts, so they line up from row to row under the weekday letters.
                val marks = Analytics.weekMarks(track, today)
                val description = weekDotsDescription(track.tally(Analytics.weekFrom(today), today, today))
                if (largeFont()) {
                    secondary(Modifier)
                    WeekDots(marks, description, Modifier.padding(top = 4.dp))
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WeekDots(marks, description)
                        Spacer(Modifier.width(8.dp))
                        secondary(Modifier.weight(1f))
                    }
                }
            }
        }
        // A practice that is due and has nothing logged has a button to mark it not done in one tap.
        if (row.due && !row.logged) {
            IconButton(onClick = onNotDone) {
                Icon(painterResource(R.drawable.lucide_circle_x), stringResource(R.string.log_mark_not_done, row.practice.name), Modifier.size(20.dp))
            }
        }
        // A separate 48 dp button, so the entries are always one tap away, whatever a tap on the row does.
        IconButton(onClick = onEntries) {
            Icon(painterResource(R.drawable.lucide_calendar), stringResource(R.string.log_open_entries, row.practice.name), Modifier.size(20.dp))
        }
    }
}

/** The line under the name: the progress, then the focus when the practice asks for it, and for a practice that is not due, its next due day. */
@Composable
private fun secondaryText(row: TodayRow, today: Long): String {
    val p = row.practice
    val progress = when {
        // Nothing logged for a duration says its target, instead of "0 s of 21 min".
        p.measure == Measure.Duration && !row.logged ->
            row.target?.let { stringResource(R.string.log_target_short, durationText(it)) } ?: stringResource(R.string.log_not_logged_yet)
        else -> progressText(row)
    }
    val withFocus = row.focusAverage?.let { stringResource(R.string.log_row_focus, progress, it) } ?: progress
    return if (row.due) {
        withFocus
    } else {
        val next = stringResource(R.string.log_next_due, nextDueText(row.nextDue, today))
        if (row.logged) stringResource(R.string.log_row_two, withFocus, next) else next
    }
}

/** "Tomorrow", the weekday name for the next days, or a short date. */
@Composable
internal fun nextDueText(nextDue: Long, today: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (Today.nextDueWording(nextDue, today)) {
        NextDueWording.Tomorrow -> stringResource(R.string.log_tomorrow)
        NextDueWording.Weekday -> LogDay.date(nextDue).dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        NextDueWording.Date -> LogDay.date(nextDue).format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMM"), locale))
    }
}

/** 28 dp: an empty ring, a ring filled by progress, a filled check, or a red cross. */
@Composable
private fun StatusIndicator(row: TodayRow) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(
        when (row.status) {
            RowStatus.Done -> R.string.log_done_mark
            RowStatus.Partial -> R.string.log_partial_mark
            RowStatus.NotDone -> R.string.log_not_done_mark
            RowStatus.Empty -> R.string.log_nothing_logged_mark
        },
    )
    Box(Modifier.size(28.dp).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        when (row.status) {
            RowStatus.Done -> {
                Box(Modifier.size(28.dp).clip(CircleShape).background(colors.primary), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.lucide_check), null, Modifier.size(18.dp), tint = colors.onPrimary)
                }
            }
            RowStatus.NotDone -> Icon(painterResource(R.drawable.lucide_circle_x), null, Modifier.size(28.dp), tint = colors.error)
            RowStatus.Empty, RowStatus.Partial -> {
                val track = colors.outline
                val fill = colors.primary
                val fraction = row.fraction
                Canvas(Modifier.size(28.dp)) {
                    val stroke = 3.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                    if (fraction > 0f) {
                        drawArc(fill, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
            }
        }
    }
}
