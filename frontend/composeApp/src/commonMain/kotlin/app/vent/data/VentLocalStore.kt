package app.vent.data

import androidx.compose.runtime.Composable
import app.vent.*
import kotlinx.serialization.Serializable

@Serializable
data class SummitSettings(
    val title: String = "TechCon Summit 2025",
    val paused: Boolean = false,
    val rows: List<VenueRow> = initialVenueRows(),
    val layout: SeatLayoutMode = SeatLayoutMode.FLAT,
    val publication: LayoutPublication = LayoutPublication.DRAFT,
    val selectionOpen: Boolean = true,
    val bookmarks: Set<String> = emptySet(),
    val preferences: Set<String> = emptySet(),
    val gate: String = "West Door 2",
    val session: SummitSession = SummitSession.KEYNOTE,
    val seating: Map<SummitSession, SessionSeating> = emptyMap(),
    val customQuests: List<String> = emptyList(),
    val attendees: Set<String> = setOf("attendee@vent.demo"),
    val sessionDetails: Map<SummitSession, SessionDetails> = emptyMap(),
    val questDefinitions: List<QuestDefinition> = emptyList(),
    val questResponses: Map<String, Map<String, String>> = emptyMap(),
    val seatAssignments: Map<SummitSession, Map<String, String>> = emptyMap(),
    val preferencesPendingSync: Boolean = false,
    val eventDays: Int = 2,
    val eventAccent: Int? = null,
) {
    // Older snapshots may have saved the original seeded address explicitly.
    val registeredAttendees: Set<String>
        get() = if ("attendee@example.com" in attendees) {
            attendees - "attendee@example.com" + "attendee@vent.demo"
        } else attendees
}

@Serializable
data class VentLocalState(
    val confirmedSeat: String = "B4",
    val checkedIn: Boolean = false,
    val organizerMode: Boolean = false,
    val settings: SummitSettings = SummitSettings(),
)

interface VentLocalStore {
    fun changes(): kotlinx.coroutines.flow.Flow<VentLocalState> = kotlinx.coroutines.flow.emptyFlow()
    suspend fun load(): VentLocalState
    suspend fun save(state: VentLocalState)
}

@Composable
expect fun rememberVentLocalStore(account: String = ""): VentLocalStore
