package app.vent.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

private class IosVentLocalStore(
    private val account: String,
) : VentLocalStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    private val key = "vent.state.$account"

    override suspend fun load(): VentLocalState =
        defaults.stringForKey(key)?.let {
            Json.decodeFromString<VentLocalState>(it)
        } ?: if (account.isNotEmpty()) {
            VentLocalState()
        } else {
            VentLocalState(
                confirmedSeat = defaults.stringForKey("vent.confirmedSeat") ?: "B4",
                checkedIn = defaults.boolForKey("vent.checkedIn"),
                organizerMode = defaults.boolForKey("vent.organizerMode"),
            )
        }

    override suspend fun save(state: VentLocalState) {
        defaults.setObject(Json.encodeToString(state), forKey = key)
    }
}

@Composable
actual fun rememberVentLocalStore(account: String): VentLocalStore = remember(account) { IosVentLocalStore(account) }
