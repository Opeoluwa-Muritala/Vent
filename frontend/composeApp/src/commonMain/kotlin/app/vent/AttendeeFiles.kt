package app.vent

import androidx.compose.runtime.Composable

@Composable
internal expect fun rememberAttendeeFilePicker(onRead: (String?) -> Unit): (() -> Unit)?

internal fun attendeeEmails(text: String): Set<String> =
    Regex("""[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+""")
        .findAll(text.take(1_048_576))
        .map { it.value.lowercase() }
        .filter { it.length <= 254 }
        .take(5000)
        .toSet()
