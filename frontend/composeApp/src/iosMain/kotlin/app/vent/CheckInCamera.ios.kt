@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package app.vent

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import platform.UIKit.UIViewController

object IosCameraBridge {
    var factory: (((String) -> Unit) -> UIViewController)? = null
}

@Composable
internal actual fun CheckInCamera(modifier: Modifier, onCode: (String) -> Unit) {
    val factory = IosCameraBridge.factory
    if (factory != null) UIKitViewController(factory = { factory(onCode) }, modifier = modifier)
    else Box(modifier, contentAlignment = Alignment.Center) { Text("Camera unavailable. Enter the check-in code manually.") }
}
