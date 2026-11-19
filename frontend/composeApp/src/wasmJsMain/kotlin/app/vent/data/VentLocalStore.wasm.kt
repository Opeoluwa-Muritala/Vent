package app.vent.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window
import kotlinx.serialization.json.Json

private class WebVentLocalStore(private val account: String) : VentLocalStore {
    private val key = "vent.state.$account"

    override suspend fun load(): VentLocalState = window.localStorage.getItem(key)?.let {
        Json.decodeFromString<VentLocalState>(it)
    } ?: if (account.isNotEmpty()) VentLocalState() else VentLocalState(
        confirmedSeat = window.localStorage.getItem("vent.confirmedSeat") ?: "B4",
        checkedIn = window.localStorage.getItem("vent.checkedIn") == "true",
        organizerMode = window.localStorage.getItem("vent.organizerMode") == "true",
    )

    override suspend fun save(state: VentLocalState) {
        window.localStorage.setItem(key, Json.encodeToString(state))
    }
}

@Composable
actual fun rememberVentLocalStore(account: String): VentLocalStore = remember(account) { WebVentLocalStore(account) }
