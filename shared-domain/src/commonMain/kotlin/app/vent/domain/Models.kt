package app.vent.domain

import kotlin.jvm.JvmInline
import kotlin.time.Instant

@JvmInline value class UserId(
    val value: String,
)

@JvmInline value class EventId(
    val value: String,
)

@JvmInline value class HallId(
    val value: String,
)

@JvmInline value class SessionId(
    val value: String,
)

@JvmInline value class SeatId(
    val value: String,
)

enum class Role { ATTENDEE, ORGANIZER, STAFF }

enum class SeatType { STANDARD, ACCESSIBLE, EXIT_ROW, BLOCKED }

enum class SeatState { FREE, YOUR_SEAT, TAKEN, CHECKED_IN, ACCESSIBLE_FREE, EXIT_ROW, BLOCKED }

enum class QuestType { SESSIONS_CHECKED_IN, POLLS_ANSWERED, CHECKED_IN_BEFORE_START }

data class User(
    val id: UserId,
    val name: String,
    val email: String,
)

data class Event(
    val id: EventId,
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val seedArgb: ULong,
)

data class Hall(
    val id: HallId,
    val name: String,
    val width: Double,
    val height: Double,
    val seats: List<Seat>,
    val elements: List<HallElement>,
)

data class Seat(
    val id: SeatId,
    val label: String,
    val x: Double,
    val y: Double,
    val rotation: Double = 0.0,
    val type: SeatType = SeatType.STANDARD,
)

data class HallElement(
    val kind: String,
    val label: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

data class Session(
    val id: SessionId,
    val eventId: EventId,
    val hallId: HallId,
    val title: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val checkInOpensAt: Instant,
    val checkInClosesAt: Instant,
)

data class Booking(
    val sessionId: SessionId,
    val userId: UserId,
    val seatId: SeatId,
    val checkedInAt: Instant? = null,
)

data class AttendeeRequest(
    val userId: UserId,
    val group: String? = null,
    val requiresAccessibleSeat: Boolean = false,
    val exitRowEligible: Boolean = false,
)

data class Assignment(
    val userId: UserId,
    val seatId: SeatId,
)

data class AssignmentResult(
    val assignments: List<Assignment>,
    val unseated: List<UserId>,
)

data class Poll(
    val id: String,
    val sessionId: SessionId,
    val question: String,
    val options: List<String>,
    val open: Boolean,
)

data class Quest(
    val id: String,
    val eventId: EventId,
    val type: QuestType,
    val target: Int,
    val points: Int,
    val badgeId: String,
)

data class QuestProgress(
    val questId: String,
    val current: Int,
    val target: Int,
    val completed: Boolean,
)

data class Badge(
    val id: String,
    val name: String,
    val description: String,
)

data class PointsEntry(
    val eventId: EventId,
    val userId: UserId,
    val source: String,
    val amount: Int,
    val createdAt: Instant,
)

data class WrappedCard(
    val eventId: EventId,
    val userId: UserId,
    val sessionsAttended: Int,
    val pollsAnswered: Int,
    val points: Int,
    val badges: List<String>,
    val rank: Int,
)
