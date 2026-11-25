package app.vent

import app.vent.data.VentApiClient
import app.vent.data.VentLocalState
import app.vent.data.VentLocalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineDemoTest {
    @Test
    fun registeredAttendeeCanSignInOnDevice() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val store =
                    object : VentLocalStore {
                        override suspend fun load() = VentLocalState(organizerMode = true)

                        override suspend fun save(state: VentLocalState) = Unit
                    }
                val auth = AuthViewModel(VentApiClient("https://unhosted.invalid"), store)
                auth.updateEmail("attendee@vent.demo")
                auth.signInLocally()
                runCurrent()
                assertEquals(AuthStep.CODE, auth.state.value.step)
                auth.updateCode("123456")
                assertEquals(AuthStep.CODE, auth.state.value.step)
                auth.updateCode("000000")
                assertEquals(AuthStep.AUTHENTICATED, auth.state.value.step)
                assertEquals("attendee@vent.demo", auth.state.value.email)
                val vent = VentViewModel(store, attendeeOnly = auth.state.value.localAttendee)
                runCurrent()
                vent.onAction(VentAction.SwitchRole(DemoRole.ORGANIZER))
                vent.onAction(VentAction.Navigate(VentScreen.ORGANIZER))
                vent.onAction(VentAction.Navigate(VentScreen.PEOPLE))
                assertEquals(DemoRole.ATTENDEE, vent.uiState.value.role)
                assertEquals(VentScreen.SCHEDULE, vent.uiState.value.screen)
                auth.signOut()
                assertEquals(AuthUiState(), auth.state.value)

                auth.updateEmail("organizer@vent.demo")
                auth.signInLocally()
                runCurrent()
                assertEquals(AuthStep.EMAIL, auth.state.value.step)

                val debugAuth = AuthViewModel(VentApiClient("https://unhosted.invalid"), store, allowLocalOrganizer = true)
                debugAuth.updateEmail("organizer@vent.demo")
                debugAuth.signInLocally()
                runCurrent()
                debugAuth.updateCode("000000")
                assertEquals(AuthStep.CODE, debugAuth.state.value.step)
                debugAuth.updateCode("123456")
                assertEquals(AuthStep.AUTHENTICATED, debugAuth.state.value.step)
                assertEquals(true, debugAuth.state.value.organizer)
            } finally {
                Dispatchers.resetMain()
            }
        }
}
