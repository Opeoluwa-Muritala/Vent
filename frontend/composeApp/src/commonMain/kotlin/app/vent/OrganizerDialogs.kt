package app.vent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import app.vent.ui.theme.spacing

@Composable
internal fun EditSessionDialog(initial: SessionDetails, close: () -> Unit, creating: Boolean = false, save: (SessionDetails) -> Unit) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var time by rememberSaveable { mutableStateOf(initial.time) }
    var hall by rememberSaveable { mutableStateOf(initial.room) }
    var speaker by rememberSaveable { mutableStateOf(initial.speaker) }
    var day by rememberSaveable { mutableStateOf(initial.day) }
    var headerImage by remember(initial.headerImage) { mutableStateOf(initial.headerImage) }
    var imageError by remember { mutableStateOf(false) }
    val chooseImage = rememberSessionImagePicker { image ->
        imageError = image == null
        if (image != null) headerImage = image
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (creating) "Create session" else "Edit session") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                OutlinedTextField(title, { title = it.take(160) }, label = { Text("Session title") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    (1..2).forEach { option -> FilterChip(day == option, { day = option }, { Text("Day $option") }) }
                }
                val times = time.split(Regex("\\s*[–—]\\s*|\\s+-\\s+"), limit = 2)
                val start = times.getOrElse(0) { "" }
                val end = times.getOrElse(1) { "" }
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                    SessionClock("Start", start, Modifier.weight(1f)) { time = "$it – $end" }
                    SessionClock("End", end, Modifier.weight(1f)) { time = "$start – $it" }
                }
                if (sessionClockMinutes(start) != null && sessionClockMinutes(end) != null && !validSessionTimeRange(time)) {
                    Text("End time must be after start time.", color = MaterialTheme.colorScheme.error)
                }
                OutlinedTextField(hall, { hall = it.take(160) }, label = { Text("Hall") }, singleLine = true)
                OutlinedTextField(speaker, { speaker = it.take(160) }, label = { Text("Speakers") }, singleLine = true)
                SessionHeaderImage(headerImage, Modifier.fillMaxWidth().aspectRatio(2f))
                if (chooseImage != null) OutlinedButton(chooseImage) { Text("Choose header image") }
                if (headerImage != null) TextButton({ headerImage = null; imageError = false }) { Text("Restore default image") }
                if (imageError) Text("Could not open this image. Choose a smaller photo.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button({ save(SessionDetails(title.trim(), time.trim(), hall.trim(), speaker.trim(), headerImage, day)) },
                enabled = listOf(title, hall, speaker).all { it.isNotBlank() } && validSessionTimeRange(time)) { Text(if (creating) "Create" else "Save") }
        },
        dismissButton = { TextButton(close) { Text("Cancel") } },
    )
}

internal fun sessionClockMinutes(value: String): Int? {
    val parts = Regex("(1[0-2]|[1-9]):([0-5][0-9])\\s*(AM|PM)", RegexOption.IGNORE_CASE)
        .matchEntire(value.trim())?.groupValues ?: return null
    return (parts[1].toInt() % 12 + if (parts[3].equals("PM", true)) 12 else 0) * 60 + parts[2].toInt()
}

internal fun validSessionTimeRange(value: String): Boolean {
    val times = value.split(Regex("\\s*[–—]\\s*|\\s+-\\s+"), limit = 2)
    val start = sessionClockMinutes(times.first()) ?: return false
    val end = times.getOrNull(1)?.let(::sessionClockMinutes) ?: return false
    return end > start
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionClock(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton({ open = true }, modifier) {
        Icon(Icons.Outlined.Schedule, null)
        Spacer(Modifier.width(MaterialTheme.spacing.xs))
        Text(value.trim().ifBlank { label }, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
    if (open) {
        val minutes = sessionClockMinutes(value) ?: if (label == "Start") 9 * 60 else 10 * 60
        val clock = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = false)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("$label time") },
            text = { TimePicker(clock) },
            confirmButton = {
                TextButton({
                    val hour = (clock.hour + 11) % 12 + 1
                    val minute = clock.minute.toString().padStart(2, '0')
                    val period = if (clock.hour < 12) "AM" else "PM"
                    onChange("$hour:$minute $period")
                    open = false
                }) { Text("Set time") }
            },
            dismissButton = { TextButton({ open = false }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun CreateQuestDialog(close: () -> Unit, save: (QuestDefinition) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("Action") }
    var prompt by rememberSaveable { mutableStateOf("") }
    var points by rememberSaveable { mutableStateOf("") }
    var choices by rememberSaveable { mutableStateOf("") }
    var correct by rememberSaveable { mutableStateOf("") }
    val options = if (kind == "Action") emptyList() else choices.lineSequence().map(String::trim).filter(String::isNotEmpty).distinct().toList()
    val value = points.toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Create quest") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
                    listOf("Action", "Poll", "Quiz").forEach { type -> FilterChip(kind == type, { kind = type }, { Text(type) }) }
                }
                OutlinedTextField(title, { title = it.take(100) }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(prompt, { prompt = it.take(300) }, label = { Text(if (kind == "Action") "Action to complete" else "Question") })
                if (kind != "Action") OutlinedTextField(choices, { choices = it.take(800) }, label = { Text("Choices, one per line") })
                if (kind == "Quiz" && options.isNotEmpty()) {
                    Text("Correct answer", style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
                    ) {
                        options.forEach { option ->
                            FilterChip(correct == option, { correct = option }, {
                                Text(option, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }, modifier = Modifier.widthIn(max = MaterialTheme.spacing.huge * 4))
                        }
                    }
                }
                OutlinedTextField(points, { points = it.filter { digit -> digit in '0'..'9' }.take(4) },
                    label = { Text("Reward points") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ))
            }
        },
        confirmButton = {
            Button({ save(QuestDefinition(title.trim(), kind, prompt.trim(), value, options, correct.trim().takeIf { kind == "Quiz" })) },
                enabled = title.isNotBlank() && prompt.isNotBlank() && value in 1..1000 &&
                    (kind == "Action" || options.size in 2..8) && (kind != "Quiz" || correct.trim() in options)) {
                Text("Publish quest")
            }
        },
        dismissButton = { TextButton(close) { Text("Cancel") } },
    )
}
