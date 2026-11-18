package app.vent

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun rememberSessionImagePicker(onRead: (String?) -> Unit): (() -> Unit)? = null

@Composable
internal actual fun SessionHeaderImage(image: String?, modifier: Modifier) = DefaultSessionHeader(modifier)
