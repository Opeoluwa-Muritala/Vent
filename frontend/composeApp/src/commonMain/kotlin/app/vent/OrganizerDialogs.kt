package app.vent

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
                OutlinedTextField(time, { time = it.take(160) }, label = { Text("Time") }, singleLine = true)
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
                enabled = listOf(title, time, hall, speaker).all { it.isNotBlank() }) { Text(if (creating) "Create" else "Save") }
        },
        dismissButton = { TextButton(close) { Text("Cancel") } },
    )
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
                if (kind == "Quiz") OutlinedTextField(correct, { correct = it.take(300) }, label = { Text("Correct choice, as written above") }, singleLine = true)
                OutlinedTextField(points, { points = it.filter(Char::isDigit).take(4) }, label = { Text("Reward points") }, singleLine = true)
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
