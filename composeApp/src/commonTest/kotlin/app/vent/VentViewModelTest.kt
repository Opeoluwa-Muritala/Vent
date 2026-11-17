package app.vent

import app.vent.data.VentLocalState
import app.vent.data.VentLocalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VentViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() { Dispatchers.setMain(dispatcher) }
    @AfterTest fun tearDown() { Dispatchers.resetMain() }

    @Test fun blockedAndOccupiedSeatsCannotBeSelected() = runTest {
        val viewModel = VentViewModel(FakeLocalStore())
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(VentAction.SelectSeat("A2"))
        viewModel.onAction(VentAction.SelectSeat("C8"))
        assertEquals("B4", viewModel.uiState.value.selectedSeat)
    }

    @Test fun availableSeatCanBeConfirmedAndPersisted() = runTest {
        val store = FakeLocalStore()
        val viewModel = VentViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(VentAction.SelectSeat("D5"))
        viewModel.onAction(VentAction.ConfirmSeat)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals("D5", viewModel.uiState.value.confirmedSeat)
        assertEquals("D5", store.saved.confirmedSeat)
    }

    @Test fun roleControlsAvailableDestinations() = runTest {
        val viewModel = VentViewModel(FakeLocalStore())
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(VentAction.Navigate(VentScreen.ORGANIZER))
        assertEquals(VentScreen.EVENT, viewModel.uiState.value.screen)
        viewModel.onAction(VentAction.SwitchRole(DemoRole.ORGANIZER))
        viewModel.onAction(VentAction.Navigate(VentScreen.QUESTS))
        assertEquals(VentScreen.ORGANIZER, viewModel.uiState.value.screen)
    }

    @Test fun checkInIsIdempotentAndPersisted() = runTest {
        val store = FakeLocalStore()
        val viewModel = VentViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isCheckedIn)
        viewModel.onAction(VentAction.CheckIn)
        viewModel.onAction(VentAction.CheckIn)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCheckedIn)
        assertTrue(store.saved.checkedIn)
    }

    @Test fun organizerCanChangeLayoutAddRowsAndApplySeatAvailability() = runTest {
        val viewModel = VentViewModel(FakeLocalStore())
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(VentAction.SwitchRole(DemoRole.ORGANIZER))
        val originalRows = viewModel.uiState.value.venueRows.size

        viewModel.onAction(VentAction.SetSeatLayout(SeatLayoutMode.CURVED))
        viewModel.onAction(VentAction.AddSeatRow)
        viewModel.onAction(VentAction.SetSeatEditMode(SeatEditMode.AVAILABILITY))
        viewModel.onAction(VentAction.SetSeatEditAvailability(SeatAvailability.BLOCKED))
        viewModel.onAction(VentAction.ApplySeatEdit("A1"))

        val state = viewModel.uiState.value
        assertEquals(SeatLayoutMode.CURVED, state.seatLayoutMode)
        assertEquals(originalRows + 1, state.venueRows.size)
        assertFalse("A1" in state.venueRows.selectableSeatLabels())
        assertEquals(
            SeatAvailability.BLOCKED,
            state.venueRows.first { it.label == "A" }.seats.first { it.label == "A1" }.availability,
        )
    }

    @Test fun organizerCanEditSelectedRowAndPublishLayout() = runTest {
        val viewModel = VentViewModel(FakeLocalStore())
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(VentAction.SwitchRole(DemoRole.ORGANIZER))
        viewModel.onAction(VentAction.SelectEditingRow("C"))
        val originalSeatCount = viewModel.uiState.value.venueRows.first { it.label == "C" }.seats.size

        viewModel.onAction(VentAction.AddSeatToRow)
        viewModel.onAction(VentAction.ToggleEditingRowTier)
        viewModel.onAction(VentAction.SetSeatEditMode(SeatEditMode.TYPE))
        viewModel.onAction(VentAction.SetSeatEditType(SeatType.ACCESSIBLE))
        viewModel.onAction(VentAction.ApplySeatEdit("C1"))
        viewModel.onAction(VentAction.PublishLayout)

        val state = viewModel.uiState.value
        val row = state.venueRows.first { it.label == "C" }
        assertEquals(originalSeatCount + 1, row.seats.size)
        assertTrue(row.seats.all { it.tier == SeatTier.VIP })
        assertEquals(SeatType.ACCESSIBLE, row.seats.first { it.label == "C1" }.type)
        assertEquals(LayoutPublication.PUBLISHED, state.layoutPublication)
    }

    @Test fun closedSelectionRejectsAttendeeSeatChanges() = runTest {
        val viewModel = VentViewModel(FakeLocalStore())
        dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAction(VentAction.SwitchRole(DemoRole.ORGANIZER))
        viewModel.onAction(VentAction.SetSeatSelectionOpen(false))
        viewModel.onAction(VentAction.SwitchRole(DemoRole.ATTENDEE))

        viewModel.onAction(VentAction.SelectSeat("D5"))
        viewModel.onAction(VentAction.ConfirmSeat)

        assertEquals("B4", viewModel.uiState.value.confirmedSeat)
        assertEquals("B4", viewModel.uiState.value.selectedSeat)
    }

    @Test fun attendeeCannotMutateOrganizerLayout() = runTest {
        val viewModel = VentViewModel(FakeLocalStore())
        dispatcher.scheduler.advanceUntilIdle()
        val original = viewModel.uiState.value

        viewModel.onAction(VentAction.AddSeatRow)
        viewModel.onAction(VentAction.ApplySeatEdit("A1"))
        viewModel.onAction(VentAction.PublishLayout)

        val state = viewModel.uiState.value
        assertEquals(original.venueRows, state.venueRows)
        assertEquals(LayoutPublication.DRAFT, state.layoutPublication)
    }
}

private class FakeLocalStore : VentLocalStore {
    var saved = VentLocalState()
    override suspend fun load(): VentLocalState = saved
    override suspend fun save(state: VentLocalState) { saved = state }
}
