package app.vent.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window

private class WebVentLocalStore : VentLocalStore {
    override suspend fun load(): VentLocalState = VentLocalState(
        confirmedSeat = window.localStorage.getItem("vent.confirmedSeat") ?: "B4",
        checkedIn = window.localStorage.getItem("vent.checkedIn") == "true",
        organizerMode = window.localStorage.getItem("vent.organizerMode") == "true",
    )

    override suspend fun save(state: VentLocalState) {
        window.localStorage.setItem("vent.confirmedSeat", state.confirmedSeat)
        window.localStorage.setItem("vent.checkedIn", state.checkedIn.toString())
        window.localStorage.setItem("vent.organizerMode", state.organizerMode.toString())
    }
}

@Composable
actual fun rememberVentLocalStore(): VentLocalStore = remember { WebVentLocalStore() }
