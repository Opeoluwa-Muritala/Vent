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

    @Test fun onlyOrganizerCreatesSessionsAndEachSessionSurvivesReload() = runTest {
        val store = FakeLocalStore()
        val model = VentViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()
        val first = SessionDetails("First talk", "10:00 AM – 11:00 AM", "Hall A", "Speaker A")
        val second = SessionDetails("Second talk", "10:00 AM – 11:30 AM", "Hall B", "Speaker B")
        model.onAction(VentAction.CreateSession(first))
        assertTrue(model.uiState.value.sessionDetails.isEmpty())
        model.onAction(VentAction.SwitchRole(DemoRole.ORGANIZER))
        model.onAction(VentAction.CreateSession(first))
        model.onAction(VentAction.CreateSession(second))
        dispatcher.scheduler.advanceUntilIdle()
        val saved = store.saved.settings.sessionDetails
        assertEquals(first, saved[SummitSession("LOCAL_1")])
        assertEquals(second, saved[SummitSession("LOCAL_2")])
        assertEquals(sessionStartLabel(first.time), sessionStartLabel(second.time))
        val reopened = VentViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(saved, reopened.uiState.value.sessionDetails)
    }

    @Test fun autoSeatKeepsExistingAssignmentsAndPersistsDistinctSeats() = runTest {
        val store = FakeLocalStore()
        val model = VentViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()
        model.onAction(VentAction.SwitchRole(DemoRole.ORGANIZER))
        model.onAction(VentAction.AddAttendees("one@vent.demo two@vent.demo"))
        model.onAction(VentAction.AutoAssignSeats)
        dispatcher.scheduler.advanceUntilIdle()
        val assigned = store.saved.settings.seatAssignments[SummitSession.KEYNOTE].orEmpty()
        assertEquals(3, assigned.size)
        assertEquals(assigned.size, assigned.values.toSet().size)
        model.onAction(VentAction.AutoAssignSeats)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(assigned, store.saved.settings.seatAssignments[SummitSession.KEYNOTE])
    }

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

    @Test fun seatingAndCheckInAreIsolatedBySessionAfterRestart() = runTest {
        val store = FakeLocalStore()
        val model = VentViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()
        model.onAction(VentAction.OpenSession(SummitSession.SECURITY))
        assertEquals("", model.uiState.value.confirmedSeat)
        model.onAction(VentAction.SelectSeat("D5"))
        model.onAction(VentAction.ConfirmSeat)
        model.onAction(VentAction.CheckIn)
        model.onAction(VentAction.OpenSession(SummitSession.PIPELINES))
        assertFalse(model.uiState.value.isCheckedIn)
        model.onAction(VentAction.SelectSeat("E1"))
        model.onAction(VentAction.ConfirmSeat)
        dispatcher.scheduler.advanceUntilIdle()
        val reopened = VentViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(SummitSession.PIPELINES, reopened.uiState.value.session)
        assertEquals("E1", reopened.uiState.value.confirmedSeat)
        reopened.onAction(VentAction.OpenSession(SummitSession.SECURITY))
        assertEquals(VentScreen.EVENT, reopened.uiState.value.screen)
        assertEquals("D5", reopened.uiState.value.confirmedSeat)
        assertTrue(reopened.uiState.value.isCheckedIn)
    }

    @Test fun staffChangesSurviveReturningToAttendeeHome() = runTest {
        val store = FakeLocalStore()
        val model = VentViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()
        model.onAction(VentAction.SwitchRole(DemoRole.ORGANIZER))
        model.onAction(VentAction.RenameEvent("Engineering summit"))
        model.onAction(VentAction.SelectGate("East Door 1"))
        model.onAction(VentAction.SwitchRole(DemoRole.ATTENDEE))
        dispatcher.scheduler.advanceUntilIdle()
        val reopened = VentViewModel(store)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(VentScreen.SCHEDULE, reopened.uiState.value.screen)
        assertEquals("Engineering summit", reopened.uiState.value.eventTitle)
        assertEquals("East Door 1", reopened.uiState.value.activeGate)
    }

    @Test fun roleControlsAvailableDestinations() = runTest {
        val viewModel = VentViewModel(FakeLocalStore())
        dispatcher.scheduler.advanceUntilIdle()
        for (destination in staffScreens.keys) {
            viewModel.onAction(VentAction.Navigate(destination))
            assertEquals(VentScreen.SCHEDULE, viewModel.uiState.value.screen)
        }
        viewModel.onAction(VentAction.Navigate(VentScreen.ORGANIZER))
        assertEquals(VentScreen.SCHEDULE, viewModel.uiState.value.screen)
        viewModel.onAction(VentAction.SwitchRole(DemoRole.ORGANIZER))
        viewModel.onAction(VentAction.Navigate(VentScreen.QUESTS))
        assertEquals(VentScreen.QUESTS, viewModel.uiState.value.screen)
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
