package app.vent.event

data class EventState(val isCheckedIn: Boolean = false)

sealed interface EventIntent {
    data class SyncCheckIn(val checkedIn: Boolean) : EventIntent
}

sealed interface EventEffect
