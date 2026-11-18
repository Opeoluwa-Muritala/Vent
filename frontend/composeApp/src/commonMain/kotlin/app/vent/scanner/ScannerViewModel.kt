package app.vent.scanner

import app.vent.architecture.MviViewModel

class ScannerViewModel : MviViewModel<ScannerState, ScannerIntent, ScannerEffect>(ScannerState()) {
    override fun onIntent(intent: ScannerIntent) {
        when (intent) {
            ScannerIntent.ToggleTorch -> reduce { copy(torchEnabled = !torchEnabled) }
            ScannerIntent.CodeDetected -> emitEffect(ScannerEffect.CheckInCompleted)
        }
    }
}
