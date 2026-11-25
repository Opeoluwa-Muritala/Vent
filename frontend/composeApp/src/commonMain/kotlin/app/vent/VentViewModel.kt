package app.vent

import androidx.lifecycle.viewModelScope
import app.vent.architecture.MviViewModel
import app.vent.data.VentLocalState
import app.vent.data.VentLocalStore
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VentScreen {
    EVENT,
    SCHEDULE,
    SEAT,
    QUESTS,
    PROFILE,
    ORGANIZER,
    PEOPLE,
    SCANNER,
    WRAPPED,
    DOOR,
    OCCUPANCY,
    CONTROLS,
    SETUP,
    PROJECTOR,
    CONFIRMED,
    DUPLICATE,
    WRONG,
    EXPIRED,
}

enum class DemoRole { ATTENDEE, ORGANIZER }

private fun Set<String>.toggled(item: String): Set<String> = if (item in this) this - item else this + item

data class VentUiState(
    val sessionDetails: Map<SummitSession, SessionDetails> = emptyMap(),
    val sessionRatings: Map<SummitSession, Int> = emptyMap(),
    val reminders: Map<SummitSession, Long> = emptyMap(),
    val checkIns: Map<SummitSession, Set<String>> = emptyMap(),
    val questDefinitions: List<QuestDefinition> = emptyList(),
    val questResponses: Map<String, Map<String, String>> = emptyMap(),
    val seatAssignments: Map<SummitSession, Map<String, String>> = emptyMap(),
    val customQuests: List<String> = emptyList(),
    val attendees: Set<String> = emptySet(),
    val session: SummitSession = SummitSession.KEYNOTE,
    val seating: Map<SummitSession, SessionSeating> = emptyMap(),
    val eventTitle: String = "TechCon Summit 2025",
    val eventDays: Int = 2,
    val eventAccent: Int? = null,
    val previewAccent: Int? = null,
    val pollPaused: Boolean = false,
    val bookmarks: Set<String> = emptySet(),
    val preferences: Set<String> = emptySet(),
    val activeGate: String = "West Door 2",
    val screen: VentScreen = VentScreen.SCHEDULE,
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
    data class PreviewEventAccent(
        val argb: Int?,
    ) : VentAction

    data class SetQuestPhase(
        val title: String,
        val phase: QuestPhase,
    ) : VentAction

    data class SetSessionPhase(
        val session: SummitSession,
        val phase: SessionPhase,
    ) : VentAction

    data class RateSession(
        val session: SummitSession,
        val rating: Int,
    ) : VentAction

    data class SetReminder(
        val session: SummitSession,
        val timestamp: Long?,
    ) : VentAction

    data class SaveSessionDetails(
        val session: SummitSession,
        val details: SessionDetails,
    ) : VentAction

    data class CreateSession(
        val details: SessionDetails,
    ) : VentAction

    data class PublishQuest(
        val quest: QuestDefinition,
    ) : VentAction

    data class CompleteQuest(
        val title: String,
        val answer: String,
    ) : VentAction

    data object AutoAssignSeats : VentAction

    data class CreateQuest(
        val title: String,
    ) : VentAction

    data class AddAttendees(
        val text: String,
    ) : VentAction

    data class OpenSession(
        val session: SummitSession,
    ) : VentAction

    data class RenameEvent(
        val title: String,
    ) : VentAction

    data class SetEventDays(
        val count: Int,
    ) : VentAction

    data class SetEventAccent(
        val argb: Int?,
    ) : VentAction

    data object TogglePoll : VentAction

    data class Bookmark(
        val title: String,
    ) : VentAction

    data class TogglePreference(
        val name: String,
    ) : VentAction

    data class SelectGate(
        val gate: String,
    ) : VentAction

    data class Navigate(
        val destination: VentScreen,
    ) : VentAction

    data class SwitchRole(
        val role: DemoRole,
    ) : VentAction

    data class SelectSeat(
        val label: String,
    ) : VentAction

    data object LocateConfirmedSeat : VentAction

    data object ConfirmSeat : VentAction

    data object CheckIn : VentAction

    data object ClearMessage : VentAction

    data class SetSeatLayout(
        val mode: SeatLayoutMode,
    ) : VentAction

    data object AddSeatRow : VentAction

    data class SetSeatEditMode(
        val mode: SeatEditMode,
    ) : VentAction

    data class SetSeatEditAvailability(
        val availability: SeatAvailability,
    ) : VentAction

    data class SetSeatEditType(
        val type: SeatType,
    ) : VentAction

    data class ApplySeatEdit(
        val label: String,
    ) : VentAction

    data class SelectEditingRow(
        val label: String,
    ) : VentAction

    data object AddSeatToRow : VentAction

    data object RemoveSeatFromRow : VentAction

    data object ToggleEditingRowTier : VentAction

    data class MoveEditingRow(
        val offset: Int,
    ) : VentAction

    data object RemoveEditingRow : VentAction

    data class SetSeatSelectionOpen(
        val open: Boolean,
    ) : VentAction

    data object SaveLayoutDraft : VentAction

    data object PublishLayout : VentAction
}

sealed interface VentEffect

private fun VentUiState.sessionSeating() =
    SessionSeating(
        confirmedSeat,
        isCheckedIn,
        venueRows,
        seatLayoutMode,
        seatSelectionOpen,
        layoutPublication,
    )

class VentViewModel(
    private val localStore: VentLocalStore,
    private val attendeeOnly: Boolean = false,
    private val startAsOrganizer: Boolean = false,
    private val accountEmail: String = "",
) : MviViewModel<VentUiState, VentAction, VentEffect>(VentUiState()) {
    internal suspend fun syncPreferences() {
        (localStore as? app.vent.data.SyncingVentLocalStore)?.syncPreferences()
    }

    private val writes = kotlinx.coroutines.channels.Channel<VentLocalState>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private var expectedWrite: VentLocalState? = null

    init {
        viewModelScope.launch {
            for (snapshot in writes) {
                runCatching { localStore.save(snapshot) }.onFailure {
                    if (expectedWrite == snapshot) expectedWrite = null
                    mutableState.update { state -> state.copy(message = "Changes could not be saved on this device.") }
                }
            }
        }
        viewModelScope.launch {
            runCatching { localStore.load() }
                .onSuccess { saved ->
                    val rows = saved.settings.rows
                    val seat =
                        if (attendeeOnly) {
                            saved.settings.seatAssignments[saved.settings.session]?.get(accountEmail)
                                ?: saved.confirmedSeat
                        } else {
                            saved.confirmedSeat
                        }
                    val role = if ((startAsOrganizer || saved.organizerMode) && !attendeeOnly) DemoRole.ORGANIZER else DemoRole.ATTENDEE
                    mutableState.value =
                        VentUiState(
                            session = saved.settings.session,
                            seating = saved.settings.seating,
                            screen = if (role == DemoRole.ORGANIZER) VentScreen.ORGANIZER else VentScreen.SCHEDULE,
                            role = role,
                            confirmedSeat = seat,
                            selectedSeat = seat,
                            isCheckedIn = saved.checkedIn,
                            isLoading = false,
                            venueRows = rows,
                            eventTitle = saved.settings.title,
                            eventDays = saved.settings.eventDays,
                            eventAccent = saved.settings.eventAccent,
                            bookmarks = saved.settings.bookmarks,
                            preferences = saved.settings.preferences,
                            activeGate = saved.settings.gate,
                            customQuests = saved.settings.customQuests,
                            attendees = saved.settings.registeredAttendees,
                            sessionDetails = saved.settings.sessionDetails,
                            sessionRatings = saved.settings.sessionRatings,
                            reminders = saved.settings.reminders,
                            checkIns = saved.settings.checkIns,
                            questDefinitions = saved.settings.questDefinitions,
                            questResponses = saved.settings.questResponses,
                            seatAssignments = saved.settings.seatAssignments,
                            pollPaused = saved.settings.paused,
                            layoutPublication = saved.settings.publication,
                            seatLayoutMode = saved.settings.layout,
                            seatSelectionOpen = saved.settings.selectionOpen,
                        )
                }.onFailure {
                    mutableState.update { state ->
                        state.copy(isLoading = false, message = "Local data could not be loaded. Using demo defaults.")
                    }
                }
            localStore.changes().collect { saved ->
                if (expectedWrite != null && expectedWrite != saved) return@collect
                expectedWrite = null
                mutableState.update { current ->
                    val confirmed =
                        if (current.role == DemoRole.ATTENDEE) {
                            saved.settings.seatAssignments[saved.settings.session]?.get(accountEmail) ?: saved.confirmedSeat
                        } else {
                            saved.confirmedSeat
                        }
                    current.copy(
                        session = saved.settings.session,
                        seating = saved.settings.seating,
                        confirmedSeat = confirmed,
                        selectedSeat =
                            if (current.session != saved.settings.session ||
                                current.selectedSeat == current.confirmedSeat
                            ) {
                                confirmed
                            } else {
                                current.selectedSeat
                            },
                        isCheckedIn = saved.checkedIn,
                        eventTitle = saved.settings.title,
                        eventDays = saved.settings.eventDays,
                        eventAccent = saved.settings.eventAccent,
                        bookmarks = saved.settings.bookmarks,
                        preferences = saved.settings.preferences,
                        activeGate = saved.settings.gate,
                        customQuests = saved.settings.customQuests,
                        attendees = saved.settings.registeredAttendees,
                        sessionDetails = saved.settings.sessionDetails,
                        sessionRatings = saved.settings.sessionRatings,
                        reminders = saved.settings.reminders,
                        checkIns = saved.settings.checkIns,
                        questDefinitions = saved.settings.questDefinitions,
                        questResponses = saved.settings.questResponses,
                        seatAssignments = saved.settings.seatAssignments,
                        pollPaused = saved.settings.paused,
                        venueRows = saved.settings.rows,
                        seatLayoutMode = saved.settings.layout,
                        layoutPublication = saved.settings.publication,
                        seatSelectionOpen = saved.settings.selectionOpen,
                    )
                }
            }
        }
    }

    override fun onIntent(action: VentAction) {
        val before = mutableState.value
        when (action) {
            is VentAction.PreviewEventAccent ->
                if (before.role == DemoRole.ORGANIZER) {
                    mutableState.update { it.copy(previewAccent = action.argb) }
                }
            is VentAction.SetQuestPhase ->
                if (before.role == DemoRole.ORGANIZER) {
                    mutableState.update {
                        it.copy(
                            questDefinitions =
                                it.questDefinitions.map { quest ->
                                    if (quest.title == action.title) quest.copy(phase = action.phase) else quest
                                },
                        )
                    }
                    persist()
                }
            is VentAction.SetSessionPhase ->
                if (before.role == DemoRole.ORGANIZER &&
                    action.session in (SummitSession.entries + before.sessionDetails.keys)
                ) {
                    val details =
                        before.sessionDetails[action.session] ?: SessionDetails(
                            action.session.title,
                            action.session.time,
                            action.session.room,
                            action.session.speaker,
                        )
                    val updated =
                        if (action.phase == SessionPhase.LIVE) {
                            before.sessionDetails.mapValues { (_, entry) ->
                                if (entry.phase == SessionPhase.LIVE) entry.copy(phase = SessionPhase.ENDED) else entry
                            }
                        } else {
                            before.sessionDetails
                        }
                    mutableState.update { it.copy(sessionDetails = updated + (action.session to details.copy(phase = action.phase))) }
                    persist()
                }
            is VentAction.RateSession ->
                if (before.role == DemoRole.ATTENDEE &&
                    action.rating in 1..5 &&
                    before.sessionDetails[action.session]?.phase == SessionPhase.ENDED
                ) {
                    mutableState.update {
                        it.copy(
                            sessionRatings = it.sessionRatings + (action.session to action.rating),
                            message = "Rating saved: ${action.rating} of 5",
                        )
                    }
                    persist()
                }
            is VentAction.SetReminder ->
                if (before.role == DemoRole.ATTENDEE &&
                    action.session in (SummitSession.entries + before.sessionDetails.keys) &&
                    (
                        action.timestamp == null ||
                            action.timestamp >
                            kotlin.time.Clock.System
                                .now()
                                .toEpochMilliseconds()
                    )
                ) {
                    mutableState.update {
                        it.copy(
                            reminders =
                                if (action.timestamp == null) {
                                    it.reminders - action.session
                                } else {
                                    it.reminders + (action.session to action.timestamp)
                                },
                            preferences = if (action.timestamp == null) it.preferences else it.preferences + "Session reminders",
                            message = if (action.timestamp == null) "Reminder removed" else "Reminder saved",
                        )
                    }
                    persist()
                }
            is VentAction.CreateSession ->
                if (before.role == DemoRole.ORGANIZER && action.details.isValid(before.eventDays)) {
                    val index =
                        before.sessionDetails.keys
                            .mapNotNull { it.name.removePrefix("LOCAL_").toIntOrNull() }
                            .maxOrNull() ?: 0
                    if (index < 1000) {
                        val session = SummitSession("LOCAL_${index + 1}")
                        mutableState.update {
                            it.copy(
                                sessionDetails =
                                    it.sessionDetails + (session to action.details.copy(phase = SessionPhase.UPCOMING)),
                                screen = VentScreen.SCHEDULE,
                            )
                        }
                        persist()
                    }
                }
            VentAction.AutoAssignSeats ->
                if (before.role == DemoRole.ORGANIZER) {
                    val openSeats = before.venueRows.selectableSeatLabels()
                    val existing =
                        before.seatAssignments[before.session]
                            .orEmpty()
                            .filter { (email, seat) -> email in before.attendees && seat in openSeats }
                    val remaining = before.attendees.sorted().filterNot(existing::containsKey)
                    val available = openSeats.filter { it != before.confirmedSeat && it !in existing.values }
                    val added = remaining.zip(available).toMap()
                    mutableState.update {
                        it.copy(
                            seatAssignments = it.seatAssignments + (it.session to (existing + added)),
                            message = "${added.size} assigned · ${remaining.size - added.size} still need seats",
                        )
                    }
                    persist()
                }
            is VentAction.SaveSessionDetails ->
                if (before.role == DemoRole.ORGANIZER) {
                    val details = action.details
                    if (details.isValid(before.eventDays) &&
                        (action.session in SummitSession.entries || action.session in before.sessionDetails)
                    ) {
                        val phase = before.sessionDetails[action.session]?.phase ?: SessionPhase.UPCOMING
                        mutableState.update {
                            it.copy(
                                sessionDetails = it.sessionDetails + (action.session to details.copy(phase = phase)),
                            )
                        }
                        persist()
                    }
                }
            is VentAction.PublishQuest ->
                if (before.role == DemoRole.ORGANIZER) {
                    val quest = action.quest
                    if (quest.title.isNotBlank() &&
                        quest.title.length <= 100 &&
                        quest.prompt.isNotBlank() &&
                        quest.prompt.length <= 300 &&
                        quest.points in 1..1000 &&
                        quest.kind in setOf("Action", "Poll", "Quiz") &&
                        (quest.kind == "Action" || quest.options.size in 2..8) &&
                        (quest.kind != "Quiz" || quest.correctAnswer?.let { it in quest.options } == true) &&
                        before.questDefinitions.none { it.title.equals(quest.title, true) }
                    ) {
                        mutableState.update { it.copy(questDefinitions = it.questDefinitions + quest) }
                        persist()
                    }
                }
            is VentAction.CompleteQuest ->
                if (before.role == DemoRole.ATTENDEE && accountEmail.isNotBlank()) {
                    val quest = before.questDefinitions.firstOrNull { it.title == action.title }
                    val responses = before.questResponses[accountEmail].orEmpty()
                    if (quest != null &&
                        quest.phase == QuestPhase.LIVE &&
                        quest.title !in responses &&
                        (quest.kind != "Poll" || !before.pollPaused)
                    ) {
                        val accepted =
                            when (quest.kind) {
                                "Action" -> action.answer == "Completed"
                                "Poll" -> action.answer in quest.options
                                "Quiz" -> action.answer == quest.correctAnswer
                                else -> false
                            }
                        if (accepted) {
                            mutableState.update {
                                it.copy(
                                    questResponses =
                                        it.questResponses + (accountEmail to (responses + (quest.title to action.answer))),
                                    message = "Earned ${quest.points} points",
                                )
                            }
                            persist()
                        } else {
                            mutableState.update { it.copy(message = "Try another answer.") }
                        }
                    }
                }
            is VentAction.AddAttendees ->
                if (before.role == DemoRole.ORGANIZER) {
                    val emails = attendeeEmails(action.text)
                    mutableState.update { it.copy(attendees = (it.attendees + emails).take(5000).toSet()) }
                    persist()
                }
            is VentAction.CreateQuest -> {
                val title = action.title.trim().take(100)
                if (before.role == DemoRole.ORGANIZER && title.isNotEmpty() && before.customQuests.none { it.equals(title, true) }) {
                    mutableState.update { it.copy(customQuests = it.customQuests + title) }
                    persist()
                }
            }
            is VentAction.OpenSession -> {
                val sessions = before.seating + (before.session to before.sessionSeating())
                val stored = sessions[action.session] ?: SessionSeating()
                val assignedSeat = if (before.role == DemoRole.ATTENDEE) before.seatAssignments[action.session]?.get(accountEmail) else null
                val selected = if (assignedSeat == null) stored else stored.copy(seat = assignedSeat)
                mutableState.update {
                    it.copy(
                        session = action.session,
                        seating = sessions,
                        selectedSeat = selected.seat,
                        confirmedSeat = selected.seat,
                        isCheckedIn = selected.checkedIn,
                        venueRows = selected.rows,
                        seatLayoutMode = selected.layout,
                        seatSelectionOpen = selected.open,
                        layoutPublication = selected.publication,
                        editingRowLabel = selected.rows.first().label,
                        screen = VentScreen.EVENT,
                        message = null,
                    )
                }
                persist()
            }
            is VentAction.Bookmark -> {
                mutableState.update { it.copy(bookmarks = it.bookmarks.toggled(action.title)) }
                persist()
            }
            is VentAction.TogglePreference -> {
                mutableState.update { it.copy(preferences = it.preferences.toggled(action.name)) }
                persist()
            }
            is VentAction.SelectGate ->
                if (before.role == DemoRole.ORGANIZER) {
                    mutableState.update { it.copy(activeGate = action.gate) }
                    persist()
                }
            is VentAction.RenameEvent ->
                if (mutableState.value.role == DemoRole.ORGANIZER && action.title.isNotBlank()) {
                    mutableState.update { it.copy(eventTitle = action.title.trim()) }
                    persist()
                }
            is VentAction.SetEventDays ->
                if (before.role == DemoRole.ORGANIZER && action.count in 1..MAX_EVENT_DAYS) {
                    if (before.sessionDetails.values.any { it.day > action.count }) {
                        mutableState.update { it.copy(message = "Move sessions from the removed days first.") }
                    } else {
                        mutableState.update { it.copy(eventDays = action.count, message = null) }
                        persist()
                    }
                }
            is VentAction.SetEventAccent ->
                if (before.role == DemoRole.ORGANIZER) {
                    mutableState.update { it.copy(eventAccent = action.argb, previewAccent = null) }
                    persist()
                }
            VentAction.TogglePoll ->
                if (mutableState.value.role == DemoRole.ORGANIZER) {
                    mutableState.update { it.copy(pollPaused = !it.pollPaused) }
                    persist()
                }
            is VentAction.Navigate -> navigate(action.destination)
            is VentAction.SwitchRole -> switchRole(action.role)
            is VentAction.SelectSeat -> selectSeat(action.label)
            VentAction.LocateConfirmedSeat ->
                mutableState.update {
                    it.copy(selectedSeat = it.confirmedSeat, message = "Showing seat ${it.confirmedSeat}")
                }
            VentAction.ConfirmSeat -> confirmSeat()
            VentAction.CheckIn -> checkIn()
            VentAction.ClearMessage -> mutableState.update { it.copy(message = null) }
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
        val after = mutableState.value
        if (action !is VentAction.OpenSession &&
            (
                before.venueRows != after.venueRows ||
                    before.seatLayoutMode != after.seatLayoutMode ||
                    before.layoutPublication != after.layoutPublication ||
                    before.seatSelectionOpen != after.seatSelectionOpen
            )
        ) {
            persist()
        }
    }

    fun onAction(action: VentAction) = onIntent(action)

    private fun navigate(destination: VentScreen) {
        mutableState.update { state ->
            val allowed =
                when (state.role) {
                    DemoRole.ATTENDEE ->
                        destination !in staffScreens &&
                            destination !in setOf(VentScreen.ORGANIZER, VentScreen.PEOPLE, VentScreen.PROJECTOR)
                    DemoRole.ORGANIZER -> destination != VentScreen.SCANNER
                }
            if (allowed) state.copy(screen = destination) else state
        }
    }

    private fun switchRole(role: DemoRole) {
        if (attendeeOnly && role != DemoRole.ATTENDEE) return
        mutableState.update {
            it.copy(
                role = role,
                screen = if (role == DemoRole.ORGANIZER) VentScreen.ORGANIZER else VentScreen.SCHEDULE,
                message = if (role == DemoRole.ORGANIZER) "Organizer workspace active" else "Attendee experience active",
            )
        }
        persist()
    }

    private fun selectSeat(label: String) {
        mutableState.update { state ->
            if (
                state.role == DemoRole.ATTENDEE &&
                state.seatSelectionOpen &&
                state.seatAssignments[state.session].orEmpty().none { (email, seat) -> email != accountEmail && seat == label } &&
                label in state.venueRows.selectableSeatLabels()
            ) {
                state.copy(selectedSeat = label, message = null)
            } else {
                state
            }
        }
    }

    private fun confirmSeat() {
        val state = mutableState.value
        if (
            state.role != DemoRole.ATTENDEE ||
            !state.seatSelectionOpen ||
            state.selectedSeat !in state.venueRows.selectableSeatLabels() ||
            state.seatAssignments[state.session].orEmpty().any { (email, seat) -> email != accountEmail && seat == state.selectedSeat } ||
            state.selectedSeat == state.confirmedSeat
        ) {
            return
        }
        mutableState.update {
            val assignments =
                if (accountEmail.isBlank()) {
                    it.seatAssignments
                } else {
                    it.seatAssignments +
                        (it.session to (it.seatAssignments[it.session].orEmpty() + (accountEmail to it.selectedSeat)))
                }
            it.copy(confirmedSeat = it.selectedSeat, seatAssignments = assignments, message = "Seat changed to ${it.selectedSeat}")
        }
        persist()
    }

    private fun checkIn() {
        val state = mutableState.value
        if (state.role != DemoRole.ATTENDEE || state.isCheckedIn) return
        mutableState.update {
            it.copy(
                isCheckedIn = true,
                checkIns =
                    if (accountEmail.isBlank()) {
                        it.checkIns
                    } else {
                        it.checkIns +
                            (it.session to (it.checkIns[it.session].orEmpty() + accountEmail))
                    },
                message = "Checked in for ${it.session.title}",
            )
        }
        persist()
    }

    private fun persist() {
        val state = mutableState.value
        val localState =
            VentLocalState(
                state.confirmedSeat,
                state.isCheckedIn,
                state.role == DemoRole.ORGANIZER,
                app.vent.data.SummitSettings(
                    state.eventTitle,
                    state.pollPaused,
                    state.venueRows,
                    state.seatLayoutMode,
                    state.layoutPublication,
                    state.seatSelectionOpen,
                    state.bookmarks,
                    state.preferences,
                    state.activeGate,
                    state.session,
                    state.seating + (state.session to state.sessionSeating()),
                    state.customQuests,
                    state.attendees,
                    state.sessionDetails,
                    state.questDefinitions,
                    state.questResponses,
                    state.seatAssignments,
                    eventDays = state.eventDays,
                    eventAccent = state.eventAccent,
                    sessionRatings = state.sessionRatings,
                    reminders = state.reminders,
                    checkIns = state.checkIns,
                ),
            )
        expectedWrite = localState
        writes.trySend(localState)
    }

    private fun setSeatLayout(mode: SeatLayoutMode) {
        mutableState.update { state ->
            if (state.role == DemoRole.ORGANIZER) {
                state.copy(
                    seatLayoutMode = mode,
                    layoutPublication = LayoutPublication.DRAFT,
                    message = "${mode.name.lowercase().replaceFirstChar { it.uppercase() }} layout active",
                )
            } else {
                state
            }
        }
    }

    private fun addSeatRow() {
        mutableState.update { state ->
            if (state.role != DemoRole.ORGANIZER || state.venueRows.size >= MAX_VENUE_ROWS) return@update state
            val rowLabel =
                ('A'..'Z')
                    .firstOrNull { candidate -> state.venueRows.none { it.label == candidate.toString() } }
                    ?.toString()
                    ?: return@update state
            val seats =
                (1..10).map {
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
        mutableState.update { state ->
            if (state.role == DemoRole.ORGANIZER) state.copy(seatEditMode = mode, message = null) else state
        }
    }

    private fun setSeatEditAvailability(availability: SeatAvailability) {
        mutableState.update { state ->
            if (state.role == DemoRole.ORGANIZER) state.copy(seatEditAvailability = availability, message = null) else state
        }
    }

    private fun setSeatEditType(type: SeatType) {
        mutableState.update { state ->
            if (state.role == DemoRole.ORGANIZER) state.copy(seatEditType = type, message = null) else state
        }
    }

    private fun applySeatEdit(label: String) {
        mutableState.update { state ->
            if (state.role != DemoRole.ORGANIZER || state.venueRows.none { row -> row.seats.any { it.label == label } }) {
                return@update state
            }
            if (state.seatEditMode == SeatEditMode.AVAILABILITY &&
                state.seatEditAvailability != SeatAvailability.AVAILABLE &&
                label in state.seatAssignments[state.session].orEmpty().values
            ) {
                return@update state.copy(message = "This seat has an attendee assignment.")
            }
            val rows =
                state.venueRows.map { row ->
                    row.copy(
                        seats =
                            row.seats.map { seat ->
                                if (seat.label != label) {
                                    seat
                                } else {
                                    when (state.seatEditMode) {
                                        SeatEditMode.TYPE -> seat.copy(type = state.seatEditType)
                                        SeatEditMode.AVAILABILITY -> seat.copy(availability = state.seatEditAvailability)
                                    }
                                }
                            },
                    )
                }
            state.copy(venueRows = rows, layoutPublication = LayoutPublication.DRAFT, message = "Seat $label updated")
        }
    }

    private fun selectEditingRow(label: String) {
        mutableState.update { state ->
            if (state.role == DemoRole.ORGANIZER && state.venueRows.any { it.label == label }) {
                state.copy(editingRowLabel = label, message = null)
            } else {
                state
            }
        }
    }

    private fun resizeEditingRow(delta: Int) {
        mutableState.update { state ->
            if (state.role != DemoRole.ORGANIZER || delta !in setOf(-1, 1)) return@update state
            val target = state.venueRows.firstOrNull { it.label == state.editingRowLabel } ?: return@update state
            val newSize = target.seats.size + delta
            if (newSize !in MIN_SEATS_PER_ROW..MAX_SEATS_PER_ROW) return@update state
            if (delta < 0) {
                val label = target.seats.last().label
                if (label == state.confirmedSeat || label in state.seatAssignments[state.session].orEmpty().values) return@update state
            }
            val resized =
                if (delta > 0) {
                    target.seats +
                        VenueSeat(
                            label = "${target.label}$newSize",
                            tier = (target.seats.firstOrNull()?.tier ?: SeatTier.STANDARD),
                            availability = SeatAvailability.AVAILABLE,
                            type = target.seats.firstOrNull()?.type ?: SeatType.STANDARD,
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
        mutableState.update { state ->
            if (state.role != DemoRole.ORGANIZER) return@update state
            val target = state.venueRows.firstOrNull { it.label == state.editingRowLabel } ?: return@update state
            val tier = if ((target.seats.firstOrNull()?.tier ?: SeatTier.STANDARD) == SeatTier.VIP) SeatTier.STANDARD else SeatTier.VIP
            state.copy(
                venueRows =
                    state.venueRows.map { row ->
                        if (row.label == target.label) row.copy(seats = row.seats.map { it.copy(tier = tier) }) else row
                    },
                layoutPublication = LayoutPublication.DRAFT,
                message = "Row ${target.label} changed to ${tier.name.lowercase()}",
            )
        }
    }

    private fun moveEditingRow(offset: Int) {
        mutableState.update { state ->
            if (state.role != DemoRole.ORGANIZER || offset !in setOf(-1, 1)) return@update state
            val from = state.venueRows.indexOfFirst { it.label == state.editingRowLabel }
            val to = from + offset
            if (from < 0 || to !in state.venueRows.indices) return@update state
            val reordered =
                state.venueRows.toMutableList().apply {
                    val row = removeAt(from)
                    add(to, row)
                }
            state.copy(venueRows = reordered, layoutPublication = LayoutPublication.DRAFT, message = "Row ${state.editingRowLabel} moved")
        }
    }

    private fun removeEditingRow() {
        mutableState.update { state ->
            if (state.role != DemoRole.ORGANIZER || state.venueRows.size <= 1) return@update state
            val target = state.venueRows.firstOrNull { it.label == state.editingRowLabel } ?: return@update state
            val assigned = state.seatAssignments[state.session].orEmpty().values
            if (target.seats.any { it.label == state.confirmedSeat || it.label in assigned }) return@update state
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
        mutableState.update { state ->
            if (state.role == DemoRole.ORGANIZER) {
                state.copy(
                    seatSelectionOpen = open,
                    layoutPublication = LayoutPublication.DRAFT,
                    message = if (open) "Attendee seat selection opened" else "Attendee seat selection closed",
                )
            } else {
                state
            }
        }
    }

    private fun setLayoutPublication(publication: LayoutPublication) {
        mutableState.update { state ->
            if (state.role != DemoRole.ORGANIZER) return@update state
            state.copy(
                layoutPublication = publication,
                message = if (publication == LayoutPublication.PUBLISHED) "Seat layout published" else "Seat layout saved as draft",
            )
        }
    }
}
