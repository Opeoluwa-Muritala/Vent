package app.vent.domain

import kotlin.math.abs

object SeatAssignment {
    fun assign(seats: List<Seat>, attendees: List<AttendeeRequest>): AssignmentResult {
        val available = seats.filter { it.type != SeatType.BLOCKED }.sortedWith(compareBy<Seat> { it.y }.thenBy { it.x }.thenBy { it.label }).toMutableList()
        val assignments = mutableListOf<Assignment>()
        val unseated = mutableListOf<UserId>()
        val ordered = attendees.sortedWith(compareByDescending<AttendeeRequest> { it.requiresAccessibleSeat }.thenBy { it.group ?: "~" }.thenBy { it.userId.value })
        for (attendee in ordered) {
            val candidate = when {
                attendee.requiresAccessibleSeat -> available.firstOrNull { it.type == SeatType.ACCESSIBLE }
                attendee.exitRowEligible -> available.firstOrNull { it.type == SeatType.EXIT_ROW } ?: available.firstOrNull { it.type == SeatType.STANDARD }
                else -> available.firstOrNull { it.type == SeatType.STANDARD || it.type == SeatType.ACCESSIBLE }
            }
            if (candidate == null) unseated += attendee.userId else {
                assignments += Assignment(attendee.userId, candidate.id)
                available.remove(candidate)
            }
        }
        return AssignmentResult(assignments, unseated)
    }
}

data class LayoutIssue(val code: String, val message: String)

object LayoutValidator {
    fun validate(hall: Hall, requiredCapacity: Int): List<LayoutIssue> = buildList {
        val duplicateLabels = hall.seats.groupingBy { it.label.trim().lowercase() }.eachCount().filterValues { it > 1 }.keys
        if (duplicateLabels.isNotEmpty()) add(LayoutIssue("duplicate_label", "Seat labels must be unique"))
        if (hall.seats.any { it.x !in 0.0..hall.width || it.y !in 0.0..hall.height }) add(LayoutIssue("outside_bounds", "Every seat must be inside the hall"))
        if (hall.seats.groupBy { Pair(it.x, it.y) }.any { it.value.size > 1 }) add(LayoutIssue("duplicate_position", "Seats cannot share a position"))
        if (hall.seats.count { it.type != SeatType.BLOCKED } < requiredCapacity) add(LayoutIssue("capacity", "Seatable capacity is below session capacity"))
        hall.seats.filter { it.type == SeatType.ACCESSIBLE }.forEach { accessible ->
            val hasNeighbour = hall.seats.any { it.id != accessible.id && it.type != SeatType.BLOCKED && abs(it.x - accessible.x) <= 1.5 && abs(it.y - accessible.y) <= 1.0 }
            if (!hasNeighbour) add(LayoutIssue("accessible_neighbour", "Accessible seat ${accessible.label} needs a free neighbour"))
        }
    }
}

interface QuestEvaluator { fun progress(quest: Quest, facts: QuestFacts): QuestProgress }
data class QuestFacts(val sessionsCheckedIn: Int, val pollsAnswered: Int, val checkedInBeforeStart: Int)

object DefaultQuestEvaluator : QuestEvaluator {
    override fun progress(quest: Quest, facts: QuestFacts): QuestProgress {
        val current = when (quest.type) {
            QuestType.SESSIONS_CHECKED_IN -> facts.sessionsCheckedIn
            QuestType.POLLS_ANSWERED -> facts.pollsAnswered
            QuestType.CHECKED_IN_BEFORE_START -> facts.checkedInBeforeStart
        }
        return QuestProgress(quest.id, current.coerceAtMost(quest.target), quest.target, current >= quest.target)
    }
}

object SeedPalette {
    data class Scheme(val primary: ULong, val onPrimary: ULong, val surface: ULong, val onSurface: ULong, val error: ULong)
    fun light(seed: ULong) = Scheme(seed or 0xFF000000u, readableOn(seed), 0xFFFFFBFFu, 0xFF1C1B1Fu, 0xFFBA1A1Au)
    fun dark(seed: ULong) = Scheme(lighten(seed), readableOn(lighten(seed)), 0xFF1C1B1Fu, 0xFFE6E1E5u, 0xFFFFB4ABu)
    private fun readableOn(color: ULong): ULong {
        val r = ((color shr 16) and 255u).toInt(); val g = ((color shr 8) and 255u).toInt(); val b = (color and 255u).toInt()
        return if (r * 299 + g * 587 + b * 114 > 150_000) 0xFF000000u else 0xFFFFFFFFu
    }
    private fun lighten(color: ULong): ULong {
        fun c(shift: Int) = (((color shr shift) and 255u).toInt() + 80).coerceAtMost(255).toULong()
        return 0xFF000000uL or (c(16) shl 16) or (c(8) shl 8) or c(0)
    }
}
