package au.com.harcourtapples.bopro.ui.orderprep.suppliers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.com.harcourtapples.bopro.api.models.Supplier
import au.com.harcourtapples.bopro.repository.OrderPrepRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SupplierListUiState(
    val suppliers: List<Supplier> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class SupplierListViewModel(private val repo: OrderPrepRepository) : ViewModel() {

    private val _state = MutableStateFlow(SupplierListUiState())
    val state: StateFlow<SupplierListUiState> = _state

    fun load(serverUrl: String, apiKey: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                _state.value = _state.value.copy(suppliers = repo.getSuppliers(serverUrl, apiKey), isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load")
            }
        }
    }

    companion object {
        fun factory(repo: OrderPrepRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SupplierListViewModel(repo) }
        }
    }
}
