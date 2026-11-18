package app.vent.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

private class IosVentLocalStore : VentLocalStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun load(): VentLocalState = VentLocalState(
        confirmedSeat = defaults.stringForKey("vent.confirmedSeat") ?: "B4",
        checkedIn = defaults.boolForKey("vent.checkedIn"),
        organizerMode = defaults.boolForKey("vent.organizerMode"),
    )

    override suspend fun save(state: VentLocalState) {
        defaults.setObject(state.confirmedSeat, forKey = "vent.confirmedSeat")
        defaults.setBool(state.checkedIn, forKey = "vent.checkedIn")
        defaults.setBool(state.organizerMode, forKey = "vent.organizerMode")
    }
}

@Composable
actual fun rememberVentLocalStore(): VentLocalStore = remember { IosVentLocalStore() }
