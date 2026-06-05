package au.com.harcourtapples.bopro.ui.stocktake.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.com.harcourtapples.bopro.repository.StocktakeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SyncUiState(
    val pendingSessions: Int = 0,
    val pendingCounts: Int = 0,
    val isSyncing: Boolean = false,
    val syncedSessions: Int = 0,
    val error: String? = null,
    val done: Boolean = false
)

class SyncViewModel(private val repo: StocktakeRepository) : ViewModel() {

    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state

    fun loadPending() {
        viewModelScope.launch {
            val sessions = repo.getUnsyncedSessions()
            var totalCounts = 0
            sessions.forEach { totalCounts += repo.getUnsyncedCountsForSession(it.id).size }
            _state.value = _state.value.copy(pendingSessions = sessions.size, pendingCounts = totalCounts, done = false, error = null)
        }
    }

    fun sync(serverUrl: String, apiKey: String = "") {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSyncing = true, error = null, syncedSessions = 0)
            try {
                val sessions = repo.getUnsyncedSessions()
                var synced = 0
                for (session in sessions) {
                    repo.syncSession(serverUrl, apiKey, session)
                    synced++
                    _state.value = _state.value.copy(syncedSessions = synced)
                }
                _state.value = _state.value.copy(isSyncing = false, pendingSessions = 0, pendingCounts = 0, done = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSyncing = false, error = e.message ?: "Sync failed")
            }
        }
    }

    companion object {
        fun factory(repo: StocktakeRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SyncViewModel(repo) }
        }
    }
}
