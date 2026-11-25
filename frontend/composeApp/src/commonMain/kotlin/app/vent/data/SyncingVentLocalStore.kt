package app.vent.data

import app.vent.api.UserPreferencesDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SyncingVentLocalStore(
    private val local: VentLocalStore,
    private val api: VentApi,
) : VentLocalStore {
    private val lock = Mutex()
    private val syncLock = Mutex()
    private val updates = MutableSharedFlow<VentLocalState>(replay = 1)
    private var current: VentLocalState? = null

    override fun changes(): SharedFlow<VentLocalState> = updates

    override suspend fun load(): VentLocalState = lock.withLock { loadLocked() }

    override suspend fun save(state: VentLocalState) =
        lock.withLock {
            val previous = loadLocked()
            val pending = previous.settings.preferencesPendingSync || previous.preferences() != state.preferences()
            store(state.copy(settings = state.settings.copy(preferencesPendingSync = pending)))
        }

    suspend fun syncPreferences() =
        syncLock.withLock {
            val before = load()
            val pending = before.settings.preferencesPendingSync
            val preferences = before.preferences()
            val remote =
                if (pending) {
                    api.savePreferences(preferences)
                    preferences
                } else {
                    api.loadPreferences()
                }
            lock.withLock applyResult@{
                val latest = loadLocked()
                if (latest.preferences() != preferences || latest.settings.preferencesPendingSync != pending) return@applyResult
                if (!pending && remote == preferences) return@applyResult
                store(
                    latest.copy(
                        settings =
                            latest.settings.copy(
                                bookmarks = remote.bookmarks,
                                preferences = remote.preferences,
                                preferencesPendingSync = false,
                            ),
                    ),
                )
            }
        }

    private suspend fun loadLocked(): VentLocalState = current ?: local.load().also { current = it }

    private suspend fun store(state: VentLocalState) {
        local.save(state)
        current = state
        updates.emit(state.copy(settings = state.settings.copy(preferencesPendingSync = false)))
    }

    private fun VentLocalState.preferences() = UserPreferencesDto(settings.bookmarks, settings.preferences)
}
