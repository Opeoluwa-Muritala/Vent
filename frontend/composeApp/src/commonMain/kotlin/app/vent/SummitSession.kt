package app.vent

@kotlinx.serialization.Serializable
enum class SessionPhase { UPCOMING, LIVE, ENDED }

@kotlinx.serialization.Serializable
enum class QuestPhase { DRAFT, LIVE, ENDED }

@kotlinx.serialization.Serializable
data class SessionDetails(
    val title: String, val time: String, val room: String, val speaker: String,
    val headerImage: String? = null,
    val day: Int = 1,
    val phase: SessionPhase = SessionPhase.UPCOMING,
)

internal const val MAX_EVENT_DAYS = 30

internal fun SessionDetails.isValid(eventDays: Int = 2): Boolean =
    listOf(title, time, room, speaker).all { it.isNotBlank() && it.length <= 160 } &&
        day in 1..eventDays && (headerImage?.length ?: 0) <= 180_000

@kotlinx.serialization.Serializable
data class QuestDefinition(
    val title: String, val kind: String, val prompt: String, val points: Int,
    val options: List<String> = emptyList(), val correctAnswer: String? = null,
    val phase: QuestPhase = QuestPhase.LIVE,
)

@kotlinx.serialization.Serializable
@kotlin.jvm.JvmInline
value class SummitSession(val name: String) {
    val title: String get() = defaults.title
    val track: String get() = if (this == KEYNOTE) "Keynote" else "Session"
    val time: String get() = defaults.time
    val room: String get() = defaults.room
    val speaker: String get() = defaults.speaker

    private val defaults: SessionDetails get() = when (this) {
        KEYNOTE -> SessionDetails("Keynote: The AI-Native Enterprise Architecture", "10:00 AM – 11:15 AM", "Main Auditorium A", "Dr. Elena Rostova & Marcus Vance")
        SECURITY -> SessionDetails("Zero-Trust Infrastructure for Multi-Agent AI", "11:30 AM – 12:30 PM", "Hall B", "Kiran Patel & Sarah Chen")
        PIPELINES -> SessionDetails("Autonomous Data Pipelines at Scale", "1:30 PM – 2:30 PM", "Auditorium A", "Alex Rivera")
        TOOLING -> SessionDetails("Developer Velocity with Modern LLM Tooling", "3:00 PM – 4:00 PM", "Auditorium C", "Priya Sharma")
        else -> SessionDetails("", "", "", "")
    }

    val passcode: String get() = when (this) {
        KEYNOTE -> "894205"
        SECURITY -> "894206"
        PIPELINES -> "894207"
        TOOLING -> "894208"
        else -> (900000 + (name.removePrefix("LOCAL_").toIntOrNull() ?: 0)).toString()
    }

    companion object {
        val KEYNOTE = SummitSession("KEYNOTE")
        val SECURITY = SummitSession("SECURITY")
        val PIPELINES = SummitSession("PIPELINES")
        val TOOLING = SummitSession("TOOLING")
        val entries = listOf(KEYNOTE, SECURITY, PIPELINES, TOOLING)
    }
}

@kotlinx.serialization.Serializable
data class SessionSeating(
    val seat: String = "",
    val checkedIn: Boolean = false,
    val rows: List<VenueRow> = initialVenueRows(),
    val layout: SeatLayoutMode = SeatLayoutMode.FLAT,
    val open: Boolean = true,
    val publication: LayoutPublication = LayoutPublication.DRAFT,
)
