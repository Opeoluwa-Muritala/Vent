package app.vent

import app.vent.api.CheckInRequest
import app.vent.api.UserPreferencesDto
import app.vent.data.SyncingVentLocalStore
import app.vent.data.VentApi
import app.vent.data.VentLocalState
import app.vent.data.VentLocalStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreferencesSyncTest {
    @Test
    fun pendingOfflineEditsSurviveReopeningAndUploadOnReconnect() =
        runTest {
            val disk = MemoryStore()
            val api = PreferencesApi()
            val store = SyncingVentLocalStore(disk, api)
            val state = store.load()
            store.save(state.copy(settings = state.settings.copy(bookmarks = setOf("Opening keynote"))))

            api.offline = true
            assertFailsWith<IllegalStateException> { store.syncPreferences() }
            assertTrue(disk.state.settings.preferencesPendingSync)

            api.offline = false
            SyncingVentLocalStore(disk, api).syncPreferences()
            assertEquals(setOf("Opening keynote"), api.remote.bookmarks)
            assertFalse(disk.state.settings.preferencesPendingSync)
        }

    @Test
    fun cleanCachePullsRemotePreferencesWithoutReplacingLocalSeat() =
        runTest {
            val disk = MemoryStore()
            disk.state = disk.state.copy(confirmedSeat = "A7")
            val api = PreferencesApi()
            api.remote = UserPreferencesDto(preferences = setOf("Networking"))

            SyncingVentLocalStore(disk, api).syncPreferences()

            assertEquals(setOf("Networking"), disk.state.settings.preferences)
            assertEquals("A7", disk.state.confirmedSeat)
            assertFalse(disk.state.settings.preferencesPendingSync)
        }

    private class MemoryStore : VentLocalStore {
        var state = VentLocalState()

        override suspend fun load() = state

        override suspend fun save(state: VentLocalState) {
            this.state = state
        }
    }

    private class PreferencesApi : VentApi {
        var remote = UserPreferencesDto()
        var offline = false

        override suspend fun loadPreferences() = remote

        override suspend fun savePreferences(preferences: UserPreferencesDto) {
            check(!offline)
            remote = preferences
        }

        override suspend fun requestOtp(email: String) = error("Unexpected OTP request")

        override suspend fun verifyOtp(
            email: String,
            code: String,
        ) = error("Unexpected OTP verification")

        override suspend fun events() = error("Unexpected events request")

        override suspend fun hall(id: String) = error("Unexpected hall request")

        override suspend fun seats(
            sessionId: String,
            since: Long?,
        ) = error("Unexpected seats request")

        override suspend fun checkIn(
            sessionId: String,
            request: CheckInRequest,
        ) = error("Unexpected check-in")

        override suspend fun quests(eventId: String) = error("Unexpected quests request")

        override suspend fun wrapped(eventId: String) = error("Unexpected wrapped request")

        override fun clearSession() = Unit
    }
}
