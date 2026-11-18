package app.vent.imports

enum class ImportStage { LIST, MAPPING, REVIEW }

data class AttendeeImportState(
    val stage: ImportStage = ImportStage.LIST,
    val query: String = "",
    val selectedFilter: AttendeeFilter = AttendeeFilter.ALL,
    val validRows: Int = 136,
    val issueRows: Int = 6,
)

enum class AttendeeFilter { ALL, UNSEATED, ACCESS }

sealed interface AttendeeImportIntent {
    data class Search(val query: String) : AttendeeImportIntent
    data class SelectFilter(val filter: AttendeeFilter) : AttendeeImportIntent
    data object StartImport : AttendeeImportIntent
    data object ReviewMapping : AttendeeImportIntent
    data object Back : AttendeeImportIntent
    data object ConfirmImport : AttendeeImportIntent
}

sealed interface AttendeeImportEffect {
    data class ImportCompleted(val count: Int) : AttendeeImportEffect
}
