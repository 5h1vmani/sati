package com.padakanti.sati.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.padakanti.sati.R
import com.padakanti.sati.log.Practice
import com.padakanti.sati.log.Reorder
import kotlinx.coroutines.isActive

/**
 * The scroll state of the screen, and where its scrolling area is in the window. The drag of a row
 * scrolls the screen when the finger nears the top or the bottom edge. The insets are the parts of
 * the area that bars cover.
 */
class ScrollHost(val state: ScrollState) {
    var bounds: Rect = Rect.Zero
    var topInset: Float = 0f
    var bottomInset: Float = 0f
}

internal val LocalScrollHost = compositionLocalOf<ScrollHost?> { null }

/**
 * The practices in one rounded surface. A tap opens one, and a press and hold lifts a row so it can be
 * dragged to a new place. The new place is reported once, when the finger lifts, through [onDrop].
 * Screen reader users get "Move up" and "Move down" actions on each row instead of the drag.
 *
 * The screen is one scrolling column, not a lazy list, so a library for lazy lists does not fit.
 * This code keeps the order in memory while the row moves, and scrolls the column near its edges.
 */
@Composable
internal fun ReorderablePractices(
    practices: List<Practice>,
    secondary: @Composable (Practice) -> String,
    onOpen: (Practice) -> Unit,
    onDrop: (id: String, index: Int) -> Unit,
    onMove: (Practice, up: Boolean) -> Unit,
) {
    val host = LocalScrollHost.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    // The drag callbacks are made once per row, so they read the newest list and actions through these.
    val latest by rememberUpdatedState(practices)
    val drop by rememberUpdatedState(onDrop)
    val move by rememberUpdatedState(onMove)
    var order by remember { mutableStateOf(practices) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    val heights = remember { HashMap<String, Int>() }
    val tops = remember { HashMap<String, Float>() }

    /** Swaps the dragged row with its neighbours for as long as it has passed their middle. */
    fun settle() {
        val id = draggingId ?: return
        while (true) {
            val index = order.indexOfFirst { it.id == id }
            val step = Reorder.step(order.map { heights[it.id] ?: 0 }, index, offset) ?: break
            order = order.toMutableList().also { list -> list.add(step.index, list.removeAt(index)) }
            offset = step.offset
        }
    }

    fun finish() {
        val id = draggingId ?: return
        val index = order.indexOfFirst { it.id == id }
        val moved = index != latest.indexOfFirst { it.id == id }
        draggingId = null
        offset = 0f
        if (moved) drop(id, index) else order = latest
    }

    // A new list from the database replaces the shown order, except while a row is lifted.
    LaunchedEffect(practices) { if (draggingId == null) order = practices }

    LaunchedEffect(draggingId) {
        if (draggingId == null || host == null) return@LaunchedEffect
        val edge = with(density) { 72.dp.toPx() }
        val fastest = with(density) { 20.dp.toPx() }
        while (isActive) {
            val top = host.bounds.top + host.topInset
            val bottom = host.bounds.bottom - host.bottomInset
            val delta = when {
                pointerY < top + edge -> -fastest * ((top + edge - pointerY) / edge).coerceIn(0f, 1f)
                pointerY > bottom - edge -> fastest * ((pointerY - (bottom - edge)) / edge).coerceIn(0f, 1f)
                else -> 0f
            }
            if (delta != 0f) {
                // The row follows the finger, so what the screen scrolled is added to its offset.
                offset += host.state.scrollBy(delta)
                settle()
            }
            androidx.compose.runtime.withFrameNanos { }
        }
    }

    // While a row is lifted, the surface does not clip it, so its shadow shows.
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (draggingId == null) Modifier.clip(shape) else Modifier)
            .background(MaterialTheme.colorScheme.surfaceContainer, shape),
    ) {
        order.forEachIndexed { index, p ->
            androidx.compose.runtime.key(p.id) {
                val lifted = draggingId == p.id
                val up = stringResource(R.string.log_action_move_up)
                val down = stringResource(R.string.log_action_move_down)
                Column(
                    Modifier
                        .onSizeChanged { heights[p.id] = it.height }
                        .onGloballyPositioned { tops[p.id] = it.positionInWindow().y }
                        .zIndex(if (lifted) 1f else 0f)
                        .graphicsLayer { translationY = if (lifted) offset else 0f },
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shadowElevation = if (lifted) 8.dp else 0.dp,
                        shape = if (lifted) shape else RoundedCornerShape(0.dp),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .semantics {
                                    customActions = listOfNotNull(
                                        if (index > 0) CustomAccessibilityAction(up) { move(p, true); true } else null,
                                        if (index < order.lastIndex) CustomAccessibilityAction(down) { move(p, false); true } else null,
                                    )
                                }
                                // A long press belongs to the drag, so it must not also open the practice on release.
                                // The drag is inside the click, so it sees the gesture first and keeps the long press.
                                .combinedClickable(role = Role.Button, onLongClick = {}, onClick = { onOpen(p) })
                                .pointerInput(p.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { start ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            draggingId = p.id
                                            offset = 0f
                                            pointerY = (tops[p.id] ?: 0f) + start.y
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            offset += amount.y
                                            pointerY += amount.y
                                            settle()
                                        },
                                        onDragEnd = { finish() },
                                        onDragCancel = { draggingId = null; offset = 0f; order = latest },
                                    )
                                }
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(secondary(p), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.width(12.dp))
                            // Only a sign that the row can be dragged. A screen reader uses the actions instead.
                            Icon(painterResource(R.drawable.lucide_grip_vertical), null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (index < order.lastIndex) RowDivider(16.dp)
                }
            }
        }
    }
}
