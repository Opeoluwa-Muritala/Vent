package app.vent.event

import app.vent.architecture.MviViewModel

class EventViewModel : MviViewModel<EventState, EventIntent, EventEffect>(EventState()) {
    override fun onIntent(intent: EventIntent) {
        when (intent) {
            is EventIntent.SyncCheckIn -> reduce { copy(isCheckedIn = intent.checkedIn) }
        }
    }
}
