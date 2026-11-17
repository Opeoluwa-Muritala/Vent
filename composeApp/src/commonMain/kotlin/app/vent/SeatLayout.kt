package app.vent

enum class SeatLayoutMode { FLAT, CURVED }
enum class SeatTier { VIP, STANDARD }
enum class SeatAvailability { AVAILABLE, RESERVED, BOOKED, BLOCKED }
enum class SeatType { STANDARD, VIP, ACCESSIBLE, COMPANION }
enum class SeatEditMode { TYPE, AVAILABILITY }
enum class LayoutPublication { DRAFT, PUBLISHED }

const val MIN_SEATS_PER_ROW = 2
const val MAX_SEATS_PER_ROW = 30
const val MAX_VENUE_ROWS = 26

data class VenueSeat(
    val label: String,
    val tier: SeatTier,
    val availability: SeatAvailability,
    val type: SeatType,
)
data class VenueRow(val label: String, val seats: List<VenueSeat>)

private val initiallyBookedSeats = setOf(
    "A2", "A3", "B3", "B6", "C3", "C8", "D4", "D10", "E6", "E7",
)

fun initialVenueRows(): List<VenueRow> = ('A'..'F').map { rowLabel ->
    val seatCount = if (rowLabel <= 'B') 8 else 10
    val tier = if (rowLabel <= 'B') SeatTier.VIP else SeatTier.STANDARD
    VenueRow(
        label = rowLabel.toString(),
        seats = (1..seatCount).map { seatNumber ->
            val label = "$rowLabel$seatNumber"
            VenueSeat(
                label = label,
                tier = tier,
                availability = if (label in initiallyBookedSeats) SeatAvailability.BOOKED else SeatAvailability.AVAILABLE,
                type = if (tier == SeatTier.VIP) SeatType.VIP else SeatType.STANDARD,
            )
        },
    )
}

fun List<VenueRow>.selectableSeatLabels(): Set<String> = flatMap(VenueRow::seats)
    .filter { it.availability == SeatAvailability.AVAILABLE }
    .mapTo(mutableSetOf(), VenueSeat::label)

