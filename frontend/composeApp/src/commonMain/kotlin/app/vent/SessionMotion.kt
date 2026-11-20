package app.vent

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

@Stable
internal class SessionMotion {
    var origin by mutableStateOf(TransformOrigin.Center)
    fun select(bounds: Rect, window: Size) {
        origin = TransformOrigin(
            (bounds.center.x / window.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
            (bounds.center.y / window.height.coerceAtLeast(1f)).coerceIn(0f, 1f),
        )
    }
}

internal val LocalSessionMotion = staticCompositionLocalOf<SessionMotion?> { null }
