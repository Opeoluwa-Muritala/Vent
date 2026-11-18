package app.vent

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberAttendeeFilePicker(onRead: (String?) -> Unit): (() -> Unit)? = null
