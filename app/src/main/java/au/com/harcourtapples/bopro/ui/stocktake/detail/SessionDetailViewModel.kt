package au.com.harcourtapples.bopro.ui.stocktake.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.com.harcourtapples.bopro.api.models.Count
import au.com.harcourtapples.bopro.api.models.Session
import au.com.harcourtapples.bopro.repository.StocktakeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val session: Session? = null,
    val counts: List<Count> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class SessionDetailViewModel(private val repo: StocktakeRepository) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state

    fun load(serverUrl: String, offline: Boolean, apiKey: String = "", sessionId: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val session = repo.getSession(offline, serverUrl, apiKey, sessionId)
                val counts  = repo.getCounts(offline, serverUrl, apiKey, sessionId)
                _state.value = _state.value.copy(session = session, counts = counts, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun deleteCount(serverUrl: String, offline: Boolean, apiKey: String = "", sessionId: Int, countId: Int) {
        viewModelScope.launch {
            try {
                repo.deleteCount(offline, serverUrl, apiKey, sessionId, countId)
                _state.value = _state.value.copy(counts = _state.value.counts.filter { it.id != countId })
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    companion object {
        fun factory(repo: StocktakeRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SessionDetailViewModel(repo) }
        }
    }
}
