@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package app.vent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@kotlin.js.JsFun(
    "() => { if (document.fullscreenElement || !document.documentElement.requestFullscreen) return false; document.documentElement.requestFullscreen().catch(() => {}); return true; }",
)
private external fun enterPassFullscreen(): Boolean

@kotlin.js.JsFun("() => { if (document.fullscreenElement && document.exitFullscreen) document.exitFullscreen().catch(() => {}); }")
private external fun leavePassFullscreen()

@Composable
internal actual fun PassBrightnessBoost(enabled: Boolean) {
    DisposableEffect(enabled) {
        val entered = enabled && enterPassFullscreen()
        onDispose { if (entered) leavePassFullscreen() }
    }
}
