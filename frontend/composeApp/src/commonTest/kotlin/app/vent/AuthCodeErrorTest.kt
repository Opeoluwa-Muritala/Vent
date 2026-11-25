package app.vent

import androidx.lifecycle.viewModelScope
import app.vent.api.*
import app.vent.data.SignInCodeRejected
import app.vent.data.VentApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class AuthCodeErrorTest {
    @Test fun rejectedCodesShowActionableErrorsInsteadOfConnectionErrors() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                mapOf(
                    "wrong_code" to "That code didn't match. Try again or request a new one.",
                    "expired_code" to "That code expired. Request a new one.",
                    "code_locked" to "Too many attempts. Request a new code.",
                ).forEach { (reason, message) ->
                    val model = AuthViewModel(RejectingCodeApi(reason))
                    try {
                        model.updateEmail("attendee@example.com")
                        model.requestCode()
                        runCurrent()
                        model.updateCode("123456")
                        runCurrent()
                        assertEquals(AuthStep.CODE, model.state.value.step)
                        assertEquals("", model.state.value.code)
                        assertEquals(message, model.state.value.error)
                        assertFalse(model.state.value.loading)
                    } finally {
                        model.viewModelScope.cancel()
                    }
                }
            } finally {
                Dispatchers.resetMain()
            }
        }
}

private class RejectingCodeApi(
    private val reason: String,
) : VentApi {
    override suspend fun requestOtp(email: String) = OtpRequestResponse()

    override suspend fun verifyOtp(
        email: String,
        code: String,
    ): AuthResponse = throw SignInCodeRejected(reason)

    override suspend fun events(): List<EventDto> = error("unused")

    override suspend fun hall(id: String): HallDto = error("unused")

    override suspend fun seats(
        sessionId: String,
        since: Long?,
    ): SeatSnapshotDto = error("unused")

    override suspend fun checkIn(
        sessionId: String,
        request: CheckInRequest,
    ): CheckInResponse = error("unused")

    override suspend fun quests(eventId: String): List<QuestDto> = error("unused")

    override suspend fun wrapped(eventId: String): WrappedCardDto = error("unused")

    override fun clearSession() = Unit
}
