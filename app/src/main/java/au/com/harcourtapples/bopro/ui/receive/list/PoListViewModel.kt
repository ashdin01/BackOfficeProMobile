package au.com.harcourtapples.bopro.ui.receive.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.com.harcourtapples.bopro.api.models.PurchaseOrder
import au.com.harcourtapples.bopro.repository.ReceiveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PoListUiState(
    val pos: List<PurchaseOrder> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class PoListViewModel(private val repo: ReceiveRepository) : ViewModel() {

    private val _state = MutableStateFlow(PoListUiState())
    val state: StateFlow<PoListUiState> = _state

    fun load(serverUrl: String, apiKey: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                _state.value = _state.value.copy(pos = repo.getPurchaseOrders(serverUrl, apiKey), isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load")
            }
        }
    }

    companion object {
        fun factory(repo: ReceiveRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { PoListViewModel(repo) }
        }
    }
}
