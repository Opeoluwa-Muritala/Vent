package app.vent.imports

import app.vent.architecture.MviViewModel

class AttendeeImportViewModel : MviViewModel<AttendeeImportState, AttendeeImportIntent, AttendeeImportEffect>(AttendeeImportState()) {
    override fun onIntent(intent: AttendeeImportIntent) {
        when (intent) {
            is AttendeeImportIntent.Search -> reduce { copy(query = intent.query) }
            is AttendeeImportIntent.SelectFilter -> reduce { copy(selectedFilter = intent.filter) }
            AttendeeImportIntent.StartImport -> reduce { copy(stage = ImportStage.MAPPING) }
            AttendeeImportIntent.ReviewMapping -> reduce { copy(stage = ImportStage.REVIEW) }
            AttendeeImportIntent.Back -> reduce { copy(stage = if (stage == ImportStage.REVIEW) ImportStage.MAPPING else ImportStage.LIST) }
            AttendeeImportIntent.ConfirmImport -> {
                emitEffect(AttendeeImportEffect.ImportCompleted(mutableState.value.validRows))
                reduce { AttendeeImportState() }
            }
        }
    }
}
