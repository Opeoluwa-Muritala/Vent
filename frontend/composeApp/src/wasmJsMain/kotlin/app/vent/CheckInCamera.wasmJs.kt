@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package app.vent

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay

@kotlin.js.JsFun("() => window.eventsCamera.start()")
private external fun startBrowserCamera()

@kotlin.js.JsFun("() => window.eventsCamera.stop()")
private external fun stopBrowserCamera()

@kotlin.js.JsFun("() => window.eventsCamera.read()")
private external fun readBrowserCamera(): String

@kotlin.js.JsFun("(left, top, width, height) => window.eventsCamera.bounds(left, top, width, height)")
private external fun positionBrowserCamera(
    left: Double,
    top: Double,
    width: Double,
    height: Double,
)

@Composable
internal actual fun CheckInCamera(
    modifier: Modifier,
    onCode: (String) -> Unit,
    onClose: () -> Unit,
    onManual: () -> Unit,
) {
    val codeCallback by rememberUpdatedState(onCode)
    val closeCallback by rememberUpdatedState(onClose)
    val manualCallback by rememberUpdatedState(onManual)
    val density = LocalDensity.current.density
    DisposableEffect(Unit) {
        startBrowserCamera()
        onDispose { stopBrowserCamera() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            when (val value = readBrowserCamera()) {
                "__close__" -> closeCallback()
                "__manual__" -> manualCallback()
                "" -> Unit
                else -> codeCallback(value)
            }
            delay(100)
        }
    }
    Box(
        modifier.onGloballyPositioned {
            val bounds = it.boundsInWindow()
            positionBrowserCamera(
                (bounds.left / density).toDouble(),
                (bounds.top / density).toDouble(),
                (bounds.width / density).toDouble(),
                (bounds.height / density).toDouble(),
            )
        },
    )
}
