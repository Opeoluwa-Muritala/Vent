package app.vent.data

import androidx.compose.runtime.Composable

data class VentLocalState(
    val confirmedSeat: String = "B4",
    val checkedIn: Boolean = false,
    val organizerMode: Boolean = false,
)

interface VentLocalStore {
    suspend fun load(): VentLocalState
    suspend fun save(state: VentLocalState)
}

@Composable
expect fun rememberVentLocalStore(): VentLocalStore
