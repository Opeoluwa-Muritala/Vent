package app.vent

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun CheckInCamera(
    modifier: Modifier,
    onCode: (String) -> Unit,
    onClose: () -> Unit = {},
    onManual: () -> Unit = {},
)
