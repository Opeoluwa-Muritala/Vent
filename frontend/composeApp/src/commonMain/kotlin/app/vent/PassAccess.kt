package app.vent

import androidx.compose.runtime.*
import app.vent.api.AttendeePassDto
import app.vent.api.EventDto
import app.vent.data.VentApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

internal data class PassState(val pass: AttendeePassDto? = null, val loading: Boolean = false, val error: String? = null)
internal val LocalAttendeePass = staticCompositionLocalOf { PassState() }
internal val LocalPassRetry = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
internal fun ProvideAttendeePass(api: VentApi, event: EventDto?, content: @Composable () -> Unit) {
    var state by remember(event?.id) { mutableStateOf(PassState()) }
    var attempt by remember(event?.id) { mutableStateOf(0) }
    LaunchedEffect(api, event?.id, attempt) {
        if (event == null) return@LaunchedEffect
        while (true) {
            state = state.copy(loading = state.pass == null, error = null)
            try {
                state = PassState(pass = api.attendeePass(event.id))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = PassState(error = "Could not issue your pass. Check your connection and try again.")
                break
            }
            delay(300_000)
        }
    }
    CompositionLocalProvider(LocalAttendeePass provides state, LocalPassRetry provides { attempt++ }, content = content)
}

@Composable
internal expect fun PassBrightnessBoost(enabled: Boolean)
