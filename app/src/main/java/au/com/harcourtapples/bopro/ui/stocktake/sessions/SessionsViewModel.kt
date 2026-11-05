package au.com.harcourtapples.bopro.ui.stocktake.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.com.harcourtapples.bopro.api.ApiClient
import au.com.harcourtapples.bopro.api.models.Department
import au.com.harcourtapples.bopro.api.models.ProductGroup
import au.com.harcourtapples.bopro.api.models.Session
import au.com.harcourtapples.bopro.repository.StocktakeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SessionsUiState(
    val sessions: List<Session> = emptyList(),
    val departments: List<Department> = emptyList(),
    val groups: List<ProductGroup> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class SessionsViewModel(private val repo: StocktakeRepository) : ViewModel() {

    private val _state = MutableStateFlow(SessionsUiState())
    val state: StateFlow<SessionsUiState> = _state

    fun load(serverUrl: String, offline: Boolean, apiKey: String = "") {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val sessions = repo.getSessions(offline, serverUrl, apiKey)
                val depts = if (offline) emptyList() else {
                    val r = ApiClient.service(serverUrl, apiKey).getDepartments()
                    if (r.isSuccessful) r.body() ?: emptyList() else emptyList()
                }
                _state.value = _state.value.copy(sessions = sessions, departments = depts, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load")
            }
        }
    }

    fun createSession(serverUrl: String, offline: Boolean, label: String, deptId: Int?, groupId: Int? = null, apiKey: String = "", onSuccess: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val id = repo.createSession(offline, serverUrl, apiKey, label, deptId, groupId)
                onSuccess(id)
                load(serverUrl, offline, apiKey)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    /** Groups belonging to the chosen department (empty when "All Departments"). */
    fun loadGroups(serverUrl: String, apiKey: String, deptId: Int?) {
        if (deptId == null) { _state.value = _state.value.copy(groups = emptyList()); return }
        viewModelScope.launch {
            val groups = try {
                val r = ApiClient.service(serverUrl, apiKey).getDepartmentGroups(deptId)
                if (r.isSuccessful) r.body() ?: emptyList() else emptyList()
            } catch (_: Exception) { emptyList() }
            _state.value = _state.value.copy(groups = groups)
        }
    }

    fun dismissError() { _state.value = _state.value.copy(error = null) }

    companion object {
        fun factory(repo: StocktakeRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SessionsViewModel(repo) }
        }
    }
}
