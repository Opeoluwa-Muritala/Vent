package app.vent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIScreen

@Composable
internal actual fun PassBrightnessBoost(enabled: Boolean) {
    DisposableEffect(enabled) {
        val screen = UIScreen.mainScreen
        val previous = screen.brightness
        if (enabled) screen.brightness = 1.0
        onDispose { if (enabled) screen.brightness = previous }
    }
}
