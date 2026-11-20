package app.vent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.vent.ui.theme.spacing
import kotlinx.coroutines.launch

internal expect fun localMinuteOfDay(): Int

internal fun nextReminderTime(now: Long, currentMinute: Int, selectedMinute: Int): Long =
    now + ((selectedMinute - currentMinute + 1440) % 1440).coerceAtLeast(1) * 60_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderSheet(close: () -> Unit, save: (Long) -> Unit) {
    val spacing = MaterialTheme.spacing
    val initial = remember { localMinuteOfDay() }
    var afternoon by remember { mutableStateOf(initial >= 720) }
    var hour by remember { mutableStateOf((initial / 60 + 11) % 12 + 1) }
    var minute by remember { mutableStateOf(initial % 60) }
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().padding(spacing.xxl), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Text("Personal reminder", style = MaterialTheme.typography.headlineSmall)
            Text("Choose the next time to remind you on this device.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                ReminderWheel((1..12).toList(), (initial / 60 + 11) % 12, Modifier.weight(1f)) { hour = it }
                ReminderWheel((0..59).toList(), initial % 60, Modifier.weight(1f)) { minute = it }
                Column(Modifier.weight(1f)) {
                    FilterChip(!afternoon, { afternoon = false }, { Text("AM") })
                    FilterChip(afternoon, { afternoon = true }, { Text("PM") })
                }
            }
            Button({
                    val selected = (hour % 12 + if (afternoon) 12 else 0) * 60 + minute
                    save(nextReminderTime(kotlin.time.Clock.System.now().toEpochMilliseconds(), localMinuteOfDay(), selected))
                    close()
                }, Modifier.fillMaxWidth().height(spacing.action)) { Text("Set reminder") }
        }
    }
}

@Composable
private fun ReminderWheel(values: List<Int>, initialIndex: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    val spacing = MaterialTheme.spacing
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val selected by remember {
        derivedStateOf {
            val center = (state.layoutInfo.viewportStartOffset + state.layoutInfo.viewportEndOffset) / 2
            state.layoutInfo.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }?.index ?: initialIndex
        }
    }
    LazyColumn(modifier.height(spacing.touch * 3), state = state,
        contentPadding = PaddingValues(vertical = spacing.touch),
        flingBehavior = rememberSnapFlingBehavior(state)) {
        items(values) { value ->
            Surface(Modifier.fillMaxWidth().height(spacing.touch), shape = MaterialTheme.shapes.small,
                color = if (values.indexOf(value) == selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                Box(contentAlignment = Alignment.Center) { Text(value.toString().padStart(2, '0')) }
            }
        }
    }
    LaunchedEffect(selected) { onChange(values[selected.coerceIn(values.indices)]) }
}

@Composable
internal fun SessionReminderHost(ui: VentUiState, dispatch: (VentAction) -> Unit) {
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(ui.reminders, ui.role) {
        if (ui.role != DemoRole.ATTENDEE) return@LaunchedEffect
        val reminder = ui.reminders.minByOrNull { it.value } ?: return@LaunchedEffect
        kotlinx.coroutines.delay((reminder.value - kotlin.time.Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0))
        val title = ui.sessionDetails[reminder.key]?.title ?: reminder.key.title
        scope.launch {
            if (host.showSnackbar("Reminder: $title", "View session", withDismissAction = true) == SnackbarResult.ActionPerformed) {
                dispatch(VentAction.OpenSession(reminder.key))
            }
        }
        dispatch(VentAction.SetReminder(reminder.key, null))
    }
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.BottomCenter) {
        SnackbarHost(host)
    }
}
