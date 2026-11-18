package app.vent

@kotlinx.serialization.Serializable
data class SessionDetails(
    val title: String, val time: String, val room: String, val speaker: String,
    val headerImage: String? = null,
)

@kotlinx.serialization.Serializable
data class QuestDefinition(
    val title: String, val kind: String, val prompt: String, val points: Int,
    val options: List<String> = emptyList(), val correctAnswer: String? = null,
)

enum class SummitSession(val title: String, val track: String, val time: String, val room: String, val speaker: String) {
    KEYNOTE("Keynote: The AI-Native Enterprise Architecture", "Keynote", "10:00 AM – 11:15 AM", "Main Auditorium A", "Dr. Elena Rostova & Marcus Vance"),
    SECURITY("Zero-Trust Infrastructure for Multi-Agent AI", "AI Systems", "11:30 AM – 12:30 PM", "Hall B", "Kiran Patel & Sarah Chen"),
    PIPELINES("Autonomous Data Pipelines at Scale", "Cloud Arch", "1:30 PM – 2:30 PM", "Auditorium A", "Alex Rivera"),
    TOOLING("Developer Velocity with Modern LLM Tooling", "Workshops", "3:00 PM – 4:00 PM", "Auditorium C", "Priya Sharma"),
    ;
    val passcode: String get() = when (this) {
        KEYNOTE -> "894205"
        SECURITY -> "894206"
        PIPELINES -> "894207"
        TOOLING -> "894208"
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
