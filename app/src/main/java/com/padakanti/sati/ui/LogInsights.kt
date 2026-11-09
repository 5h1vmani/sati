package com.padakanti.sati.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.padakanti.sati.R
import com.padakanti.sati.log.Analytics
import com.padakanti.sati.log.DayMark
import com.padakanti.sati.log.Entry
import com.padakanti.sati.log.LogController
import com.padakanti.sati.log.LogDay
import com.padakanti.sati.log.Measure
import com.padakanti.sati.log.Practice
import com.padakanti.sati.log.Tally
import com.padakanti.sati.log.Track
import com.padakanti.sati.log.TrendSummary
import com.padakanti.sati.log.WeekSummary
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * The analytics: the week dots on Today, the 12-week view, and the history on a practice's screen.
 * The counting rules live in [Analytics]. This file only draws.
 */

/** One dot and the space around it. 7 of them sit under a practice name on Today. */
private val DOT_PITCH = 12.dp
private val DOT = 8.dp

/** With large text the dots have their own line, so they spread out to leave room for larger weekday letters. */
private val DOT_PITCH_LARGE = 20.dp

@Composable
private fun dotPitch(): Dp = if (largeFont()) DOT_PITCH_LARGE else DOT_PITCH

/** Text from this font scale up puts the week dots on their own line. */
internal const val LARGE_FONT_SCALE = 1.5f

@Composable
internal fun locale(): Locale = LocalConfiguration.current.locales[0]

/** The first day of the week in the phone's language. */
@Composable
internal fun firstDayOfWeek(): DayOfWeek = WeekFields.of(locale()).firstDayOfWeek

@Composable
internal fun largeFont(): Boolean = LocalDensity.current.fontScale >= LARGE_FONT_SCALE

/** "2 Nov" in the phone's language. */
@Composable
private fun shortDate(day: Long): String {
    val l = locale()
    return LogDay.date(day).format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(l, "dMMM"), l))
}

// ---------- Today: the week dots ----------

/**
 * The last 7 days as dots, oldest first, ending with today. Filled is done, a grey ring is a missed due day,
 * a very small dot is a day that was not due, and nothing is a day the practice was not active. Today, while
 * it is still open, is a ring in the brand colour. Screen readers get [description] instead, which gives the count.
 */
@Composable
internal fun WeekDots(marks: List<DayMark>, description: String, modifier: Modifier = Modifier) {
    val done = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outline
    Row(modifier.clearAndSetSemantics { contentDescription = description }) {
        marks.forEach { mark ->
            Canvas(Modifier.size(dotPitch())) {
                val r = DOT.toPx() / 2
                when (mark) {
                    DayMark.Done -> drawCircle(done, r)
                    DayMark.Missed, DayMark.Open -> {
                        val stroke = 1.5.dp.toPx()
                        drawCircle(if (mark == DayMark.Open) done else line, r - stroke / 2, style = Stroke(stroke))
                    }
                    DayMark.NotDue -> drawCircle(line, 2.dp.toPx())
                    DayMark.Inactive -> Unit
                }
            }
        }
    }
}

/**
 * The narrow weekday letters over the dots, such as "M" for Monday. Today's letter is bold, in the brand colour.
 * They only label the dots, so screen readers skip them.
 */
@Composable
internal fun WeekLetters(today: Long, modifier: Modifier = Modifier) {
    val l = locale()
    Row(modifier.clearAndSetSemantics {}) {
        for (day in Analytics.weekFrom(today)..today) {
            Box(Modifier.width(dotPitch()), contentAlignment = Alignment.Center) {
                Text(
                    LogDay.date(day).dayOfWeek.getDisplayName(TextStyle.NARROW, l),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (day == today) FontWeight.Bold else null,
                    softWrap = false,
                    modifier = Modifier.wrapContentWidth(unbounded = true),
                )
            }
        }
    }
}

/** What a screen reader says for the dots of one row. */
@Composable
internal fun weekDotsDescription(tally: Tally): String =
    if (tally.due == 0) stringResource(R.string.log_week_dots_none)
    else pluralStringResource(R.plurals.log_week_dots, tally.due, tally.done, tally.due)

/** At most two sentences about the last 7 days. Empty when nothing was due. */
@Composable
internal fun weekSentences(summary: WeekSummary): List<String> = buildList {
    if (summary.days == 0) return@buildList
    add(pluralStringResource(R.plurals.log_week_all_done, summary.days, summary.allDone, summary.days))
    summary.mostMissed?.let { add(stringResource(R.string.log_week_most_missed, it.practice.name, it.missed, it.due)) }
}

// ---------- 12 weeks ----------

/** The 12-week view: one row of week squares for each practice that is not archived. A tap on a row opens the practice. */
@Composable
internal fun WeeksView(today: Long, practices: List<Practice>?, onOpen: (Practice) -> Unit) {
    val context = LocalContext.current.applicationContext
    val firstDay = firstDayOfWeek()
    val starts = remember(today, firstDay) { Analytics.weekStarts(today, firstDay, Analytics.TREND_WEEKS) }
    val from = starts.first()
    val entries by remember(from, today) { LogController.entriesBetween(context, from, today) }.collectAsState(initial = null)
    val periods by remember { LogController.archivePeriods(context) }.collectAsState(initial = null)
    val firsts by remember { LogController.firstEntries(context) }.collectAsState(initial = null)

    val list = practices ?: return
    val loaded = entries ?: return
    val archive = periods ?: return
    val firstDays = firsts?.associate { it.practiceId to it.day } ?: return

    val tracks = list.map { Track(it, firstDays[it.id], archive, loaded) }
    val live = tracks.filterNot { it.practice.archived }.sortedBy { it.practice.position }
    if (live.isEmpty()) {
        Notice(R.drawable.lucide_archive, stringResource(if (list.isEmpty()) R.string.log_trend_empty else R.string.log_all_archived))
        return
    }
    SentenceCard(trendSentences(Analytics.trend(tracks, starts, today)).ifEmpty { listOf(stringResource(R.string.log_trend_empty)) })

    ListSurface {
        WeeksHeader(starts.first())
        live.forEachIndexed { index, track ->
            if (index > 0) RowDivider(16.dp)
            WeeksRow(
                track,
                Analytics.weekTallies(track, starts, today),
                starts.map { Analytics.activeWeek(track, it, today) },
            ) { onOpen(track.practice) }
        }
        RowDivider(16.dp)
        WeeksLegend()
    }
}

/** What each fill of a square means. The rows also tell screen readers their counts, so the legend is for sight only. */
@Composable
private fun WeeksLegend() {
    // Two columns of two, so no label is left alone on a line.
    val items = listOf(
        3 to R.string.log_legend_all,
        2 to R.string.log_legend_most,
        1 to R.string.log_legend_some,
        0 to R.string.log_legend_none,
    )
    Column(Modifier.fillMaxWidth().padding(16.dp).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                pair.forEach { (level, label) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Square(level)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** At most two sentences about the 12 weeks. Empty when no practice had a due day. */
@Composable
private fun trendSentences(summary: TrendSummary): List<String> = buildList {
    if (summary.counted == 0) return@buildList
    add(pluralStringResource(R.plurals.log_trend_steady, summary.counted, summary.steady, summary.counted))
    summary.change?.let {
        add(stringResource(if (it.rose) R.string.log_trend_rose else R.string.log_trend_dropped, it.practice.name))
    }
}

/** The squares of 12 weeks, with 4 dp between them. */
private val SQUARE = 14.dp
private val SQUARE_GAP = 4.dp
private val SQUARES_WIDTH = SQUARE * Analytics.TREND_WEEKS + SQUARE_GAP * (Analytics.TREND_WEEKS - 1)

/** The date of the first week on the left, and "This week" on the right, over the squares. */
@Composable
private fun WeeksHeader(firstStart: Long) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp).clearAndSetSemantics {}) {
        if (!largeFont()) Spacer(Modifier.weight(1f))
        Row(if (largeFont()) Modifier.fillMaxWidth() else Modifier.width(SQUARES_WIDTH)) {
            val style = MaterialTheme.typography.labelSmall
            val color = MaterialTheme.colorScheme.onSurfaceVariant
            Text(shortDate(firstStart), style = style, color = color, modifier = Modifier.weight(1f), maxLines = 1)
            Text(stringResource(R.string.log_this_week), style = style, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun WeeksRow(track: Track, weeks: List<Tally>, active: List<Boolean>, onClick: () -> Unit) {
    val total = weeks.fold(Tally.ZERO, Tally::plus)
    val name = track.practice.name
    val description = if (total.due == 0) stringResource(R.string.log_trend_row_none, name)
    else pluralStringResource(R.plurals.log_trend_row, total.due, name, total.done, total.due)
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
        .clickable(role = Role.Button, onClick = onClick)
        .clearAndSetSemantics { contentDescription = description }
        .padding(horizontal = 16.dp, vertical = 10.dp)
    val nameText = @Composable { m: Modifier ->
        Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = m)
    }
    if (largeFont()) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            nameText(Modifier)
            WeekSquares(weeks, active)
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            nameText(Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            WeekSquares(weeks, active)
        }
    }
}

/**
 * Each square fills from the bottom: fully for every due day done, two thirds for half or more, one third
 * for less than half. None is an empty grey outline. A week with nothing due is a very small dot.
 * A week when the practice was not active, such as before it started, is blank, as on Today.
 * The height carries the meaning, so colour is not needed to read it.
 */
@Composable
private fun WeekSquares(weeks: List<Tally>, active: List<Boolean>) {
    Row(horizontalArrangement = Arrangement.spacedBy(SQUARE_GAP)) {
        weeks.forEachIndexed { i, tally ->
            if (active[i]) Square(Analytics.level(tally)) else Spacer(Modifier.size(SQUARE))
        }
    }
}

/** One week's square at [level], as [Analytics.level] gives it. */
@Composable
private fun Square(level: Int?) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(3.dp)
    val base = Modifier.size(SQUARE)
    when (level) {
        null -> Box(base, contentAlignment = Alignment.Center) { Box(Modifier.size(4.dp).clip(CircleShape).background(colors.outline)) }
        0 -> Box(base.border(1.5.dp, colors.outline, shape))
        1, 2 -> Box(base.clip(shape).border(1.5.dp, colors.primary, shape), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(if (level == 1) 1f / 3 else 2f / 3).background(colors.primary))
        }
        else -> Box(base.clip(shape).background(colors.primary))
    }
}

// ---------- One practice ----------

/**
 * The history at the top of a practice's screen: the sentences, the month calendar, and the charts.
 * [entries] are all the entries of the practice. [onDay] opens a day of the calendar.
 */
@Composable
internal fun PracticeHistory(practice: Practice, entries: List<Entry>, today: Long, onDay: (Long) -> Unit) {
    val context = LocalContext.current.applicationContext
    val periods by remember { LogController.archivePeriods(context) }.collectAsState(initial = null)
    val archive = periods ?: return
    val track = remember(practice, entries, archive) { Track(practice, entries.minOfOrNull { it.day }, archive, entries) }
    val firstDay = firstDayOfWeek()

    val sentences = buildList {
        val last = Analytics.lastDays(track, today)
        if (last.due > 0) add(pluralStringResource(R.plurals.log_last_days, last.due, last.done, last.due))
        val from = today - Analytics.TREND_WEEKS * 7L + 1
        Analytics.weakestWeekday(track, from, today)?.let {
            add(stringResource(R.string.log_weakest_day, it.getDisplayName(TextStyle.FULL, locale())))
        }
    }
    if (sentences.isNotEmpty()) SentenceCard(sentences)

    MonthCalendar(track, today, firstDay, onDay)

    // A chart shows only when it has something to draw. The calendar already shows an empty history.
    val amountStarts = Analytics.weekStarts(today, firstDay, Analytics.AMOUNT_WEEKS)
    val hasAmount = Analytics.weeklyTotals(track.entries, amountStarts).any { it > 0 }
    if ((practice.measure == Measure.Duration || practice.measure == Measure.Count) && hasAmount) {
        AmountChart(practice.measure, track.entries, today, firstDay)
    }
    val focusStarts = Analytics.weekStarts(today, firstDay, Analytics.TREND_WEEKS)
    if (Analytics.weeklyFocus(track.entries, focusStarts).any { it != null }) {
        FocusChart(track.entries, today, firstDay)
    }
}

/** A card with one to two short sentences. The first one is the main one. */
@Composable
internal fun SentenceCard(sentences: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        sentences.forEachIndexed { i, s ->
            Text(
                s,
                style = if (i == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                color = if (i == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A rounded surface with a small heading, for the calendar and the charts. */
@Composable
private fun ChartSurface(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

// ---------- the month calendar ----------

/** One month with the same marks as the dots on Today. The arrows stop at the first active month and at this month. */
@Composable
private fun MonthCalendar(track: Track, today: Long, firstDay: DayOfWeek, onDay: (Long) -> Unit) {
    val l = locale()
    val thisMonth = YearMonth.from(LogDay.date(today))
    val firstMonth = minOf(YearMonth.from(LogDay.date(track.start)), thisMonth)
    var index by rememberSaveable(track.practice.id) { mutableIntStateOf(monthIndex(thisMonth)) }
    val month = monthOf(index.coerceIn(monthIndex(firstMonth), monthIndex(thisMonth)))
    val title = month.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(l, "MMMMyyyy"), l))

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { index = monthIndex(month) - 1 }, enabled = month > firstMonth) {
                Icon(painterResource(R.drawable.lucide_chevron_left), stringResource(R.string.log_previous_month), Modifier.size(20.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { index = monthIndex(month) + 1 }, enabled = month < thisMonth) {
                Icon(painterResource(R.drawable.lucide_chevron_right), stringResource(R.string.log_next_month), Modifier.size(20.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clearAndSetSemantics {}) {
            Analytics.weekdays(firstDay).forEach {
                Text(
                    it.getDisplayName(TextStyle.NARROW, l),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Analytics.monthGrid(month, firstDay).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    DayCell(day, day?.let { track.mark(it, today) }, day == today, today, onDay, Modifier.weight(1f))
                }
            }
        }
    }
}

private fun monthIndex(month: YearMonth): Int = month.year * 12 + month.monthValue - 1

private fun monthOf(index: Int): YearMonth = YearMonth.of(Math.floorDiv(index, 12), Math.floorMod(index, 12) + 1)

/** One day of the calendar. Today and past days open with a tap. A future day is dimmed and does nothing. */
@Composable
private fun DayCell(day: Long?, mark: DayMark?, isToday: Boolean, today: Long, onDay: (Long) -> Unit, modifier: Modifier) {
    if (day == null || mark == null) {
        Spacer(modifier.height(44.dp))
        return
    }
    val colors = MaterialTheme.colorScheme
    val date = LogDay.date(day)
    val l = locale()
    val markText = stringResource(
        when (mark) {
            DayMark.Done -> R.string.log_mark_done
            DayMark.Missed -> R.string.log_mark_missed
            DayMark.NotDue -> R.string.log_mark_not_due
            DayMark.Open -> R.string.log_mark_open
            DayMark.Inactive -> R.string.log_mark_inactive
        },
    )
    val dateText = date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(l, "EEEEdMMMM"), l))
    val past = day <= today
    // A future day has no mark to read, only its date.
    val description = if (past) stringResource(R.string.log_calendar_day, dateText, markText) else dateText
    Box(
        modifier
            .height(44.dp)
            .clickable(enabled = past, role = Role.Button) { onDay(day) }
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val circle = Modifier.size(34.dp)
        val inner = when (mark) {
            DayMark.Done -> circle.clip(CircleShape).background(colors.primary)
            DayMark.Missed -> circle.border(1.5.dp, colors.outline, CircleShape)
            DayMark.Open -> circle.border(2.dp, colors.primary, CircleShape)
            else -> circle
        }
        val textColor = when {
            mark == DayMark.Done -> colors.onPrimary
            mark == DayMark.Inactive -> colors.onSurface.copy(alpha = 0.38f)
            mark == DayMark.NotDue -> colors.onSurfaceVariant
            else -> colors.onSurface
        }
        Box(inner, contentAlignment = Alignment.Center) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = if (isToday) FontWeight.Bold else null,
            )
        }
    }
}

// ---------- the charts ----------

/** A week's total: hours and minutes for a duration, cycles for a count. */
@Composable
private fun weekAmountText(measure: Measure, value: Long): String {
    if (measure == Measure.Count) {
        val n = value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return pluralStringResource(R.plurals.log_cycles, n, n)
    }
    val minutes = (value / 60).toInt()
    return when {
        minutes < 60 -> if (minutes == 0 && value > 0) durationText(value.toInt()) else stringResource(R.string.log_duration_min, minutes)
        minutes % 60 == 0 -> stringResource(R.string.log_duration_h, minutes / 60)
        else -> stringResource(R.string.log_duration_h_min, minutes / 60, minutes % 60)
    }
}

/** The total of each of the last 8 weeks as bars. This week is the full colour, the weeks before are lighter. */
@Composable
private fun AmountChart(measure: Measure, entries: List<Entry>, today: Long, firstDay: DayOfWeek) {
    val starts = remember(today, firstDay) { Analytics.weekStarts(today, firstDay, Analytics.AMOUNT_WEEKS) }
    val totals = remember(entries, starts) { Analytics.weeklyTotals(entries, starts) }
    val title = stringResource(if (measure == Measure.Duration) R.string.log_chart_time else R.string.log_chart_cycles)
    val values = totals.map { weekAmountText(measure, it) }
    val description = pluralStringResource(R.plurals.log_chart_description, totals.size, totals.size, values.joinToString(stringResource(R.string.log_list_separator)))

    ChartSurface(title) {
        Text(stringResource(R.string.log_amount_week, values.last()), style = MaterialTheme.typography.titleMedium)
        val past = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        val now = MaterialTheme.colorScheme.primary
        val base = MaterialTheme.colorScheme.outlineVariant
        val max = (totals.maxOrNull() ?: 0L).coerceAtLeast(1L)
        Canvas(Modifier.fillMaxWidth().height(88.dp).clearAndSetSemantics { contentDescription = description }) {
            val gap = 8.dp.toPx()
            val barWidth = (size.width - gap * (totals.size - 1)) / totals.size
            val radius = CornerRadius(3.dp.toPx())
            drawLine(base, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            totals.forEachIndexed { i, v ->
                if (v <= 0) return@forEachIndexed
                val h = (v.toFloat() / max * size.height).coerceAtLeast(2.dp.toPx())
                val x = i * (barWidth + gap)
                drawRoundRect(if (i == totals.lastIndex) now else past, Offset(x, size.height - h), Size(barWidth, h), radius)
            }
        }
        AxisLabels(shortDate(starts.first()), stringResource(R.string.log_this_week))
    }
}

/** The average focus of each of the last 12 weeks as a line on a scale from 1 to 10. A week with no score leaves a gap. */
@Composable
private fun FocusChart(entries: List<Entry>, today: Long, firstDay: DayOfWeek) {
    val starts = remember(today, firstDay) { Analytics.weekStarts(today, firstDay, Analytics.TREND_WEEKS) }
    val weekly = remember(entries, starts) { Analytics.weeklyFocus(entries, starts) }
    val recentFrom = starts[Analytics.TREND_WEEKS - Analytics.RECENT_WEEKS]
    val recent = remember(entries, recentFrom, today) { Analytics.focusAverage(entries, recentFrom, today) }
    val title = stringResource(R.string.log_chart_focus)
    val noScore = stringResource(R.string.log_chart_no_score)
    val values = weekly.map { v -> v?.let { String.format(locale(), "%.1f", it) } ?: noScore }
    val description = pluralStringResource(R.plurals.log_chart_description, weekly.size, weekly.size, values.joinToString(stringResource(R.string.log_list_separator)))

    ChartSurface(title) {
        Text(
            if (recent != null) stringResource(R.string.log_focus_four_weeks, recent) else stringResource(R.string.log_focus_none_recent),
            style = MaterialTheme.typography.titleMedium,
        )
        val line = MaterialTheme.colorScheme.primary
        val grid = MaterialTheme.colorScheme.outlineVariant
        val labelStyle = MaterialTheme.typography.labelSmall
        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        Row(Modifier.fillMaxWidth().height(88.dp)) {
            Column(Modifier.width(FOCUS_LABELS).fillMaxHeight().clearAndSetSemantics {}, verticalArrangement = Arrangement.SpaceBetween) {
                Text("10", style = labelStyle, color = labelColor)
                Text("1", style = labelStyle, color = labelColor)
            }
            Canvas(Modifier.weight(1f).height(88.dp).clearAndSetSemantics { contentDescription = description }) {
                // The same as half the height of a label, so "10" and "1" sit on their lines.
                val pad = 8.dp.toPx()
                val step = (size.width - 2 * pad) / (weekly.size - 1)
                fun y(v: Double) = (pad + (1 - (v - 1) / 9) * (size.height - 2 * pad)).toFloat()
                drawLine(grid, Offset(0f, y(10.0)), Offset(size.width, y(10.0)), 1.dp.toPx())
                drawLine(grid, Offset(0f, y(1.0)), Offset(size.width, y(1.0)), 1.dp.toPx())
                weekly.forEachIndexed { i, v ->
                    if (v == null) return@forEachIndexed
                    val p = Offset(pad + i * step, y(v))
                    val prev = weekly.getOrNull(i - 1)
                    if (prev != null) drawLine(line, Offset(pad + (i - 1) * step, y(prev)), p, 2.dp.toPx())
                    drawCircle(line, 3.5.dp.toPx(), p)
                }
            }
        }
        Row {
            Spacer(Modifier.width(FOCUS_LABELS))
            Box(Modifier.weight(1f)) { AxisLabels(shortDate(starts.first()), stringResource(R.string.log_this_week)) }
        }
    }
}

/** The width of the "10" and "1" labels on the left of the focus chart. The dates under it start after the same width. */
private val FOCUS_LABELS = 24.dp

/** The first week's date on the left and "This week" on the right, under a chart. */
@Composable
private fun AxisLabels(start: String, end: String) {
    Row(Modifier.fillMaxWidth().clearAndSetSemantics {}) {
        val style = MaterialTheme.typography.labelSmall
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Text(start, style = style, color = color, modifier = Modifier.weight(1f), maxLines = 1)
        Text(end, style = style, color = color, maxLines = 1)
    }
}
