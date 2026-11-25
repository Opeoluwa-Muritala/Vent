package app.vent.architecture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

abstract class MviViewModel<State, Intent, Effect>(
    initialState: State,
) : ViewModel() {
    protected val mutableState = MutableStateFlow(initialState)
    val uiState = mutableState.asStateFlow()

    private val effectChannel = Channel<Effect>(Channel.BUFFERED)
    val effects = effectChannel.receiveAsFlow()

    abstract fun onIntent(intent: Intent)

    protected fun reduce(reducer: State.() -> State) {
        mutableState.value = mutableState.value.reducer()
    }

    protected fun emitEffect(effect: Effect) {
        viewModelScope.launch { effectChannel.send(effect) }
    }
}
