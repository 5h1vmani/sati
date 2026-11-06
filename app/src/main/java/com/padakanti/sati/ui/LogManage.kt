package com.padakanti.sati.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.padakanti.sati.R
import com.padakanti.sati.log.Practice
import com.padakanti.sati.log.PracticeRules

/**
 * The practices list. A tap on a row opens the practice editor, where archive and delete are. A press and hold
 * then a drag changes the order, and [onDrop] gets the new place once.
 */
@Composable
internal fun PracticesView(
    practices: List<Practice>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Practice) -> Unit,
    onDrop: (id: String, index: Int) -> Unit,
    onMove: (Practice, up: Boolean) -> Unit,
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
    } else {
        ReorderablePractices(practices, secondary = { manageLine(it) }, onOpen = onEdit, onDrop = onDrop, onMove = onMove)
    }
}

/** The line under a name: the measure, the target, the repeat and "Archived", such as "Duration · Target 21 min · Every 2 days". */
@Composable
private fun manageLine(p: Practice): String {
    val target = p.target
    val parts = buildList {
        add(measureName(p.measure))
        if (target != null && PracticeRules.hasTarget(p.measure)) add(stringResource(R.string.log_target_short, amountText(p.measure, target)))
        if (p.everyDays > 1) add(pluralStringResource(R.plurals.log_every_n_days, p.everyDays, p.everyDays))
        if (p.archived) add(stringResource(R.string.log_archived))
    }
    return parts.joinToString(stringResource(R.string.log_separator))
}
