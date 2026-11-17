package app.vent.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RulesTest {
    private val seats = listOf(
        Seat(SeatId("accessible"), "A1", 1.0, 1.0, type = SeatType.ACCESSIBLE),
        Seat(SeatId("standard"), "A2", 2.0, 1.0),
        Seat(SeatId("blocked"), "A3", 3.0, 1.0, type = SeatType.BLOCKED),
    )

    @Test fun accessibleRequestsAreAssignedFirst() {
        val result = SeatAssignment.assign(seats, listOf(AttendeeRequest(UserId("standard")), AttendeeRequest(UserId("accessible"), requiresAccessibleSeat = true)))
        assertEquals(SeatId("accessible"), result.assignments.first { it.userId.value == "accessible" }.seatId)
        assertTrue(result.assignments.none { it.seatId.value == "blocked" })
    }

    @Test fun assignmentIsDeterministic() {
        val attendees = listOf(AttendeeRequest(UserId("b")), AttendeeRequest(UserId("a")))
        assertEquals(SeatAssignment.assign(seats, attendees), SeatAssignment.assign(seats, attendees.reversed()))
    }
}