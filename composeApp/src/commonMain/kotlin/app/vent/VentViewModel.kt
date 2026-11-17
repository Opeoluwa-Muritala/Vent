package app.vent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vent.data.VentLocalState
import app.vent.data.VentLocalStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VentScreen { EVENT, SCHEDULE, SEAT, QUESTS, PROFILE, ORGANIZER }
enum class DemoRole { ATTENDEE, ORGANIZER }

data class VentUiState(
    val screen: VentScreen = VentScreen.EVENT,
    val role: DemoRole = DemoRole.ATTENDEE,
    val confirmedSeat: String = "B4",
    val selectedSeat: String = "B4",
    val isCheckedIn: Boolean = false,
    val message: String? = null,
    val isLoading: Boolean = true,
    val seatLayoutMode: SeatLayoutMode = SeatLayoutMode.FLAT,
    val venueRows: List<VenueRow> = initialVenueRows(),
    val editingRowLabel: String = "A",
    val seatEditMode: SeatEditMode = SeatEditMode.TYPE,
    val seatEditAvailability: SeatAvailability = SeatAvailability.AVAILABLE,
    val seatEditType: SeatType = SeatType.STANDARD,
    val seatSelectionOpen: Boolean = true,
    val layoutPublication: LayoutPublication = LayoutPublication.DRAFT,
)

sealed interface VentAction {
    data class Navigate(val destination: VentScreen) : VentAction
    data class SwitchRole(val role: DemoRole) : VentAction
    data class SelectSeat(val label: String) : VentAction
    data object LocateConfirmedSeat : VentAction
    data object ConfirmSeat : VentAction
    data object CheckIn : VentAction
    data object ClearMessage : VentAction
    data class SetSeatLayout(val mode: SeatLayoutMode) : VentAction
    data object AddSeatRow : VentAction
    data class SetSeatEditMode(val mode: SeatEditMode) : VentAction
    data class SetSeatEditAvailability(val availability: SeatAvailability) : VentAction
    data class SetSeatEditType(val type: SeatType) : VentAction
    data class ApplySeatEdit(val label: String) : VentAction
    data class SelectEditingRow(val label: String) : VentAction
    data object AddSeatToRow : VentAction
    data object RemoveSeatFromRow : VentAction
    data object ToggleEditingRowTier : VentAction
    data class MoveEditingRow(val offset: Int) : VentAction
    data object RemoveEditingRow : VentAction
    data class SetSeatSelectionOpen(val open: Boolean) : VentAction
    data object SaveLayoutDraft : VentAction
    data object PublishLayout : VentAction
}

class VentViewModel(private val localStore: VentLocalStore) : ViewModel() {
    private val _uiState = MutableStateFlow(VentUiState())
    val uiState: StateFlow<VentUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { localStore.load() }
                .onSuccess { saved ->
                    val rows = initialVenueRows()
                    val seat = saved.confirmedSeat.takeIf { it in rows.selectableSeatLabels() } ?: "B4"
                    val role = if (saved.organizerMode) DemoRole.ORGANIZER else DemoRole.ATTENDEE
                    _uiState.value = VentUiState(
                        screen = if (role == DemoRole.ORGANIZER) VentScreen.ORGANIZER else VentScreen.EVENT,
                        role = role,
                        confirmedSeat = seat,
                        selectedSeat = seat,
                        isCheckedIn = saved.checkedIn,
                        isLoading = false,
                        venueRows = rows,
                    )
                }
                .onFailure {
                    _uiState.update { state -> state.copy(isLoading = false, message = "Local data could not be loaded. Using demo defaults.") }
                }
        }
    }

    fun onAction(action: VentAction) {
        when (action) {
            is VentAction.Navigate -> navigate(action.destination)
            is VentAction.SwitchRole -> switchRole(action.role)
            is VentAction.SelectSeat -> selectSeat(action.label)
            VentAction.LocateConfirmedSeat -> _uiState.update { it.copy(selectedSeat = it.confirmedSeat, message = "Showing seat ${it.confirmedSeat}") }
            VentAction.ConfirmSeat -> confirmSeat()
            VentAction.CheckIn -> checkIn()
            VentAction.ClearMessage -> _uiState.update { it.copy(message = null) }
            is VentAction.SetSeatLayout -> setSeatLayout(action.mode)
            VentAction.AddSeatRow -> addSeatRow()
            is VentAction.SetSeatEditMode -> setSeatEditMode(action.mode)
            is VentAction.SetSeatEditAvailability -> setSeatEditAvailability(action.availability)
            is VentAction.SetSeatEditType -> setSeatEditType(action.type)
            is VentAction.ApplySeatEdit -> applySeatEdit(action.label)
            is VentAction.SelectEditingRow -> selectEditingRow(action.label)
            VentAction.AddSeatToRow -> resizeEditingRow(1)
            VentAction.RemoveSeatFromRow -> resizeEditingRow(-1)
            VentAction.ToggleEditingRowTier -> toggleEditingRowTier()
            is VentAction.MoveEditingRow -> moveEditingRow(action.offset)
            VentAction.RemoveEditingRow -> removeEditingRow()
            is VentAction.SetSeatSelectionOpen -> setSeatSelectionOpen(action.open)
            VentAction.SaveLayoutDraft -> setLayoutPublication(LayoutPublication.DRAFT)
            VentAction.PublishLayout -> setLayoutPublication(LayoutPublication.PUBLISHED)
        }
    }

    private fun navigate(destination: VentScreen) {
        _uiState.update { state ->
            val allowed = when (state.role) {
                DemoRole.ATTENDEE -> destination != VentScreen.ORGANIZER
                DemoRole.ORGANIZER -> destination != VentScreen.EVENT && destination != VentScreen.QUESTS
            }
            if (allowed) state.copy(screen = destination) else state
        }
    }

    private fun switchRole(role: DemoRole) {
        _uiState.update {
            it.copy(
                role = role,
                screen = if (role == DemoRole.ORGANIZER) VentScreen.ORGANIZER else VentScreen.EVENT,
                message = if (role == DemoRole.ORGANIZER) "Organizer workspace active" else "Attendee experience active",
            )
        }
        persist()
    }

    private fun selectSeat(label: String) {
        _uiState.update { state ->
            if (
                state.role == DemoRole.ATTENDEE &&
                state.seatSelectionOpen &&
                label in state.venueRows.selectableSeatLabels()
            ) state.copy(selectedSeat = label, message = null) else state
        }
    }

    private fun confirmSeat() {
        val state = _uiState.value
        if (
            state.role != DemoRole.ATTENDEE ||
            !state.seatSelectionOpen ||
            state.selectedSeat !in state.venueRows.selectableSeatLabels() ||
            state.selectedSeat == state.confirmedSeat
        ) return
        _uiState.update { it.copy(confirmedSeat = it.selectedSeat, message = "Seat changed to ${it.selectedSeat}") }
        persist()
    }

    private fun checkIn() {
        val state = _uiState.value
        if (state.role != DemoRole.ATTENDEE || state.isCheckedIn) return
        _uiState.update { it.copy(isCheckedIn = true, message = "Checked in for Opening keynote · Seat ${it.confirmedSeat}") }
        persist()
    }

    private fun persist() {
        val state = _uiState.value
        val localState = VentLocalState(state.confirmedSeat, state.isCheckedIn, state.role == DemoRole.ORGANIZER)
        viewModelScope.launch {
            runCatching { localStore.save(localState) }
                .onFailure { _uiState.update { it.copy(message = "Changes could not be saved locally. Try again.") } }
        }
    }

    private fun setSeatLayout(mode: SeatLayoutMode) {
        _uiState.update { state ->
            if (state.role == DemoRole.ORGANIZER) state.copy(
                seatLayoutMode = mode,
                layoutPublication = LayoutPublication.DRAFT,
                message = "${mode.name.lowercase().replaceFirstChar { it.uppercase() }} layout active",
            ) else state
        }
    }

    private fun addSeatRow() {
        _uiState.update { state ->
            if (state.role != DemoRole.ORGANIZER || state.venueRows.size >= MAX_VENUE_ROWS) return@update state
            val rowLabel = ('A'..'Z')
                .firstOrNull { candidate -> state.venueRows.none { it.label == candidate.toString() } }
                ?.toString()
                ?: return@update state
            val seats = (1..10).map {
                VenueSeat("$rowLabel$it", SeatTier.STANDARD, SeatAvailability.AVAILABLE, SeatType.STANDARD)
            }
            state.copy(
                venueRows = state.venueRows + VenueRow(rowLabel, seats),
                editingRowLabel = rowLabel,
                layoutPublication = LayoutPublication.DRAFT,
                message = "Row $rowLabel added",
            )
        }
    }

    private fun setSeatEditMode(mode: SeatEditMode) {
        _uiState.update { state ->
            if (state.role == DemoRole.ORGANIZER) state.copy(seatEditMode = mode, message = null) else state
        }
    }

    private fun setSeatEditAvailability(availability: SeatAvailability) {
        _uiState.update { state ->
            if (state.role == DemoRole.ORGANIZER) state.copy(seatEditAvailability = availability, message = null) else state
        }
    }

    private fun setSeatEditType(type: SeatType) {
        _uiState.update { state ->
            if (state.role == DemoRole.ORGANIZER) state.copy(seatEditType = type, message = null) else state
        }
    }

    private fun applySeatEdit(label: String) {
        _uiState.update { state ->
            if (state.role != DemoRole.ORGANIZER || state.venueRows.none { row -> row.seats.any { it.label == label } }) {
                return@update state
            }
            val rows = state.venueRows.map { row ->
                row.copy(seats = row.seats.map { seat ->
                    if (seat.label != label) seat else when (state.seatEditMode) {
                        SeatEditMode.TYPE -> seat.copy(type = state.seatEditType)
                        SeatEditMode.AVAILABILITY -> seat.copy(availability = state.seatEditAvailability)
                    }
                })
            }
            state.copy(venueRows = rows, layoutPublication = LayoutPublication.DRAFT, message = "Seat $label updated")
        }
    }

    private fun selectEditingRow(label: String) {
        _uiState.update { state ->
            if (state.role == DemoRole.ORGANIZER && state.venueRows.any { it.label == label }) {
                state.copy(editingRowLabel = label, message = null)
            } else state
        }
    }

    private fun resizeEditingRow(delta: Int) {
        _uiState.update { state ->
            if (state.role != DemoRole.ORGANIZER || delta !in setOf(-1, 1)) return@update state
            val target = state.venueRows.firstOrNull { it.label == state.editingRowLabel } ?: return@update state
            val newSize = target.seats.size + delta
            if (newSize !in MIN_SEATS_PER_ROW..MAX_SEATS_PER_ROW) return@update state
            if (delta < 0 && target.seats.last().label == state.confirmedSeat) return@update state
            val resized = if (delta > 0) {
                target.seats + VenueSeat(
                    label = "${target.label}$newSize",
                    tier = target.seats.first().tier,
                    availability = SeatAvailability.AVAILABLE,
                    type = target.seats.first().type,
                )
            } else {
                target.seats.dropLast(1)
            }
            state.copy(
                venueRows = state.venueRows.map { if (it.label == target.label) it.copy(seats = resized) else it },
                layoutPublication = LayoutPublication.DRAFT,
                message = "Row ${target.label} now has $newSize seats",
            )
        }
    }

    private fun toggleEditingRowTier() {
        _uiState.update { state ->
            if (state.role != DemoRole.ORGANIZER) return@update state
            val target = state.venueRows.firstOrNull { it.label == state.editingRowLabel } ?: return@update state
            val tier = if (target.seats.first().tier == SeatTier.VIP) SeatTier.STANDARD else SeatTier.VIP
            state.copy(
                venueRows = state.venueRows.map { row ->
                    if (row.label == target.label) row.copy(seats = row.seats.map { it.copy(tier = tier) }) else row
                },
                layoutPublication = LayoutPublication.DRAFT,
                message = "Row ${target.label} changed to ${tier.name.lowercase()}",
            )
        }
    }

    private fun moveEditingRow(offset: Int) {
        _uiState.update { state ->
            if (state.role != DemoRole.ORGANIZER || offset !in setOf(-1, 1)) return@update state
            val from = state.venueRows.indexOfFirst { it.label == state.editingRowLabel }
            val to = from + offset
            if (from < 0 || to !in state.venueRows.indices) return@update state
            val reordered = state.venueRows.toMutableList().apply {
                val row = removeAt(from)
                add(to, row)
            }
            state.copy(venueRows = reordered, layoutPublication = LayoutPublication.DRAFT, message = "Row ${state.editingRowLabel} moved")
        }
    }

    private fun removeEditingRow() {
        _uiState.update { state ->
            if (state.role != DemoRole.ORGANIZER || state.venueRows.size <= 1) return@update state
            val target = state.venueRows.firstOrNull { it.label == state.editingRowLabel } ?: return@update state
            if (target.seats.any { it.label == state.confirmedSeat }) return@update state
            val rows = state.venueRows.filterNot { it.label == target.label }
            state.copy(
                venueRows = rows,
                editingRowLabel = rows.first().label,
                layoutPublication = LayoutPublication.DRAFT,
                message = "Row ${target.label} removed",
            )
        }
    }

    private fun setSeatSelectionOpen(open: Boolean) {
        _uiState.update { state ->
            if (state.role == DemoRole.ORGANIZER) state.copy(
                seatSelectionOpen = open,
                layoutPublication = LayoutPublication.DRAFT,
                message = if (open) "Attendee seat selection opened" else "Attendee seat selection closed",
            ) else state
        }
    }

    private fun setLayoutPublication(publication: LayoutPublication) {
        _uiState.update { state ->
            if (state.role != DemoRole.ORGANIZER) return@update state
            state.copy(
                layoutPublication = publication,
                message = if (publication == LayoutPublication.PUBLISHED) "Seat layout published" else "Seat layout saved as draft",
            )
        }
    }
}
