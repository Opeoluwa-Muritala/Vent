package app.vent

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberAttendeeFilePicker(onRead: (String?) -> Unit): (() -> Unit)? {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val content = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readNBytes(1_048_577)
                        if (bytes.size <= 1_048_576) bytes.decodeToString() else null
                    }
                }.getOrNull()
            }
            onRead(content)
        }
    }
    return { launcher.launch(arrayOf("text/csv", "text/plain", "application/vnd.ms-excel")) }
}
