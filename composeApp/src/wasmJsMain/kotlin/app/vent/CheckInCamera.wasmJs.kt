package app.vent

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal actual fun CheckInCamera(modifier: Modifier, onCode: (String) -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) { Text("Enter the check-in code manually.") }
}
