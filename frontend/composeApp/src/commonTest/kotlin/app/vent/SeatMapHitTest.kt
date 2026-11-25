package app.vent

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SeatMapHitTest {
    private val seats =
        initialVenueRows().first().seats.take(2).mapIndexed { index, seat ->
            MapSeat(seat, Offset(index * 48f, 0f))
        }

    @Test fun closestSeatWinsWithinTheMinimumTouchRadius() {
        assertEquals(seats.first(), nearestMapSeat(seats, Offset(20f, 0f), 22f))
        assertEquals(seats.last(), nearestMapSeat(seats, Offset(30f, 0f), 22f))
    }

    @Test fun tappingOutsideSeatTargetsDoesNotSelectASeat() {
        assertNull(nearestMapSeat(seats, Offset(24f, 40f), 22f))
        assertNull(nearestMapSeat(emptyList(), Offset.Zero, 22f))
    }
}
