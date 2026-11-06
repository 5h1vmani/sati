@file:OptIn(ExperimentalMaterial3Api::class)

package com.padakanti.sati.ui

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.padakanti.sati.R
import com.padakanti.sati.Store
import kotlin.math.roundToInt

/** Pieces both tabs use. */

@Composable
internal fun SectionCard(@DrawableRes icon: Int, title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

/** A note with an icon. With [actionLabel], it also has a button that runs [onAction]. */
@Composable
internal fun Notice(
    @DrawableRes icon: Int,
    text: String,
    warning: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (warning) colors.primaryContainer else colors.surfaceContainer,
            contentColor = if (warning) colors.onPrimaryContainer else colors.onSurface,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), null, Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
            if (actionLabel != null) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
internal fun SetupItem(@DrawableRes icon: Int, title: String, body: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onClick) { Text(action) }
    }
}

/** A play or stop button for a test of the bell. */
@Composable
internal fun PreviewButton(playing: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            painterResource(if (playing) R.drawable.lucide_square else R.drawable.lucide_play),
            contentDescription = if (playing) "Stop sound" else "Play sound",
            modifier = Modifier.size(20.dp),
        )
    }
}

/** A volume slider. Local while dragging; [onSet] runs when the finger lifts. */
@Composable
internal fun VolumeRow(volume: Int, description: String, onSet: (Int) -> Unit) {
    var value by remember(volume) { mutableFloatStateOf(volume.toFloat()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.lucide_volume_2), null, Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onSet(value.roundToInt()) },
            valueRange = Store.MIN_VOLUME.toFloat()..Store.MAX_VOLUME.toFloat(),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = description },
        )
        Spacer(Modifier.width(12.dp))
        Text("${value.roundToInt()}%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(40.dp))
    }
}

@Composable
internal fun TimeDialog(title: String, initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initial / 60,
        initialMinute = initial % 60,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
