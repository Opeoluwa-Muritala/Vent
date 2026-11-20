package app.vent

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

private tailrec fun passActivity(context: Context): Activity? = when (context) {
    is Activity -> context
    is ContextWrapper -> if (context.baseContext !== context) passActivity(context.baseContext) else null
    else -> null
}

@Composable
internal actual fun PassBrightnessBoost(enabled: Boolean) {
    val window = passActivity(LocalContext.current)?.window
    DisposableEffect(window, enabled) {
        val previous = window?.attributes?.screenBrightness
        if (enabled && window != null) window.attributes = window.attributes.apply { screenBrightness = 1f }
        onDispose {
            if (enabled && window != null && previous != null) {
                window.attributes = window.attributes.apply { screenBrightness = previous }
            }
        }
    }
}
