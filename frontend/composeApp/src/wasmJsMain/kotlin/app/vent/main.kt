package app.vent

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import androidx.compose.runtime.SideEffect

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        VentApp()
        SideEffect { document.getElementById("events-splash")?.remove() }
    }
}
