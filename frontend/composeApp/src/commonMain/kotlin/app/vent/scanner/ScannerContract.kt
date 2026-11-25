package app.vent.scanner

data class ScannerState(
    val torchEnabled: Boolean = false,
)

sealed interface ScannerIntent {
    data object ToggleTorch : ScannerIntent

    data object CodeDetected : ScannerIntent
}

sealed interface ScannerEffect {
    data object CheckInCompleted : ScannerEffect
}
