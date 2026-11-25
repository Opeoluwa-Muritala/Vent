package app.vent

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HowToVote
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import app.vent.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditSessionDialog(
    initial: SessionDetails,
    close: () -> Unit,
    creating: Boolean = false,
    eventDays: Int = 2,
    halls: List<String> = emptyList(),
    save: (SessionDetails) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var time by rememberSaveable { mutableStateOf(initial.time) }
    var hall by rememberSaveable { mutableStateOf(initial.room) }
    var speaker by rememberSaveable { mutableStateOf(initial.speaker) }
    var day by rememberSaveable { mutableStateOf(initial.day) }
    var headerImage by remember(initial.headerImage) { mutableStateOf(initial.headerImage) }
    var imageError by remember { mutableStateOf(false) }
    var hallMenu by rememberSaveable { mutableStateOf(false) }
    val space = MaterialTheme.spacing
    val chooseImage =
        rememberSessionImagePicker { image ->
            imageError = image == null
            if (image != null) headerImage = image
        }
    val valid = listOf(title, hall, speaker).all { it.isNotBlank() } && validSessionTimeRange(time)
    ModalBottomSheet(onDismissRequest = close, shape = MaterialTheme.shapes.extraLarge) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(space.xxl),
            verticalArrangement = Arrangement.spacedBy(space.lg),
        ) {
            Text(if (creating) "New session" else "Edit session", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                title,
                { title = it.take(160) },
                Modifier.fillMaxWidth().heightIn(min = space.action),
                label = { Text("Title") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
            )
            VentPillTabs((1..eventDays).map { "Day $it" }, day - 1) { day = it + 1 }
            val times = time.split(Regex("\\s*[–—]\\s*|\\s+-\\s+"), limit = 2)
            val start = times.getOrElse(0) { "" }
            val end = times.getOrElse(1) { "" }
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                SessionClock("Start", start, Modifier.weight(1f)) { time = "$it – $end" }
                SessionClock("End", end, Modifier.weight(1f)) { time = "$start – $it" }
            }
            if (sessionClockMinutes(start) != null && sessionClockMinutes(end) != null && !validSessionTimeRange(time)) {
                Text("End time must be after start time.", color = MaterialTheme.colorScheme.error)
            }
            Box {
                OutlinedTextField(
                    hall,
                    { hall = it.take(160) },
                    Modifier.fillMaxWidth().heightIn(min = space.action),
                    label = { Text("Hall") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    trailingIcon = {
                        IconButton({ hallMenu = true }, enabled = halls.isNotEmpty()) {
                            Icon(Icons.Outlined.ExpandMore, "Choose hall")
                        }
                    },
                )
                DropdownMenu(expanded = hallMenu, onDismissRequest = { hallMenu = false }) {
                    halls.filter(String::isNotBlank).distinct().forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = {
                            hall = option
                            hallMenu = false
                        })
                    }
                }
            }
            OutlinedTextField(
                speaker,
                { speaker = it.take(160) },
                Modifier.fillMaxWidth().heightIn(min = space.action),
                label = { Text("Speakers") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                speaker.split(",", "&").map(String::trim).filter(String::isNotBlank).forEach { name ->
                    InputChip(selected = false, onClick = {}, label = { Text(name) })
                }
            }
            SessionHeaderImage(headerImage, Modifier.fillMaxWidth().height(space.huge * 4))
            if (chooseImage != null) OutlinedButton(chooseImage, Modifier.fillMaxWidth()) { Text("Add image") }
            if (headerImage != null) {
                TextButton({
                    headerImage = null
                    imageError = false
                }) { Text("Remove image") }
            }
            if (imageError) Text("Could not open this image. Choose a smaller photo.", color = MaterialTheme.colorScheme.error)
            if (!valid) {
                Text(
                    if (title.isBlank() || !validSessionTimeRange(time)) {
                        "Add a title and time to continue."
                    } else {
                        "Add a hall and speakers to continue."
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                {
                    save(
                        initial.copy(
                            title = title.trim(),
                            time = time.trim(),
                            room = hall.trim(),
                            speaker = speaker.trim(),
                            headerImage = headerImage,
                            day = day,
                        ),
                    )
                },
                Modifier.fillMaxWidth().height(space.action),
                enabled = valid,
            ) {
                Text(if (creating) "Create session" else "Save")
            }
            TextButton(close, Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

internal fun sessionClockMinutes(value: String): Int? {
    val parts =
        Regex("(1[0-2]|[1-9]):([0-5][0-9])\\s*(AM|PM)", RegexOption.IGNORE_CASE)
            .matchEntire(value.trim())
            ?.groupValues ?: return null
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
private fun SessionClock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateQuestDialog(
    close: () -> Unit,
    initialKind: String = "Action",
    save: (QuestDefinition) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    var prompt by rememberSaveable { mutableStateOf("") }
    var points by rememberSaveable { mutableStateOf("10") }
    var choices by rememberSaveable { mutableStateOf("") }
    var optionDraft by rememberSaveable { mutableStateOf("") }
    var correct by rememberSaveable { mutableStateOf("") }
    val options =
        if (kind ==
            "Action"
        ) {
            emptyList()
        } else {
            choices
                .lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .toList()
        }
    val value = points.toIntOrNull() ?: 0
    AlertDialog(
        onDismissRequest = close,
        title = { Text("Create quest") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Action", "Poll", "Quiz").forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = kind == type,
                            onClick = { kind = type },
                            shape = SegmentedButtonDefaults.itemShape(index, 3),
                            icon = {
                                Icon(
                                    when (type) {
                                        "Poll" -> Icons.Outlined.HowToVote
                                        "Quiz" -> Icons.Outlined.Quiz
                                        else -> Icons.Outlined.Bolt
                                    },
                                    null,
                                )
                            },
                        ) { Text(type) }
                    }
                }
                OutlinedTextField(title, { title = it.take(100) }, label = { Text("Title") }, singleLine = true)
                OutlinedTextField(prompt, { prompt = it.take(300) }, label = {
                    Text(
                        if (kind ==
                            "Action"
                        ) {
                            "Action to complete"
                        } else {
                            "Question"
                        },
                    )
                })
                if (kind != "Action") {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        OutlinedTextField(
                            optionDraft,
                            { optionDraft = it.take(100) },
                            Modifier.weight(1f),
                            label = { Text("Option") },
                            singleLine = true,
                        )
                        TextButton({
                            choices = (options + optionDraft.trim()).joinToString("\n")
                            optionDraft = ""
                        }, enabled = optionDraft.isNotBlank() && optionDraft.trim() !in options && options.size < 8) {
                            Text("Add option")
                        }
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                    ) {
                        options.forEach { option ->
                            InputChip(
                                selected = kind == "Quiz" && correct == option,
                                onClick = { if (kind == "Quiz") correct = option },
                                label = { Text(option) },
                                trailingIcon = {
                                    IconButton({
                                        choices = options.filterNot { it == option }.joinToString("\n")
                                        if (correct == option) correct = ""
                                    }) { Icon(Icons.Outlined.Remove, "Remove option $option") }
                                },
                            )
                        }
                    }
                }
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
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Points", Modifier.weight(1f))
                    IconButton({ points = (value - 1).coerceAtLeast(1).toString() }, enabled = value > 1) {
                        Icon(Icons.Outlined.Remove, "Fewer points")
                    }
                    Text(points)
                    IconButton({ points = (value + 1).coerceAtMost(1000).toString() }, enabled = value < 1000) {
                        Icon(Icons.Outlined.Add, "More points")
                    }
                }
                if (title.isBlank() || prompt.isBlank()) {
                    Text(
                        "Add a title and complete the action or question.",
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else if (kind != "Action" && options.size !in 2..8) {
                    Text(
                        "Add between two and eight options.",
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else if (kind == "Quiz" && correct !in options) {
                    Text(
                        "Mark the correct answer.",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                { save(QuestDefinition(title.trim(), kind, prompt.trim(), value, options, correct.trim().takeIf { kind == "Quiz" })) },
                enabled =
                    title.isNotBlank() &&
                        prompt.isNotBlank() &&
                        value in 1..1000 &&
                        (kind == "Action" || options.size in 2..8) &&
                        (kind != "Quiz" || correct.trim() in options),
            ) {
                Text("Publish quest")
            }
        },
        dismissButton = { TextButton(close) { Text("Cancel") } },
    )
}
