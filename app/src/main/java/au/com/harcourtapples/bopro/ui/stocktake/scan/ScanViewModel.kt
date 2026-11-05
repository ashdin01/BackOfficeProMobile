package au.com.harcourtapples.bopro.ui.stocktake.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.com.harcourtapples.bopro.api.models.Product
import au.com.harcourtapples.bopro.repository.StocktakeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ScanState {
    object Scanning  : ScanState()
    object LookingUp : ScanState()
    data class ProductFound(val product: Product, val barcode: String, val existingQty: Double = 0.0) : ScanState()
    data class NotFound(val barcode: String) : ScanState()
    data class OutOfGroup(val description: String, val barcode: String, val groupName: String) : ScanState()
    data class OfflineReady(val barcode: String, val existingQty: Double = 0.0) : ScanState()
    object Saving  : ScanState()
    data class Error(val message: String) : ScanState()
}

class ScanViewModel(private val repo: StocktakeRepository) : ViewModel() {

    private val _state = MutableStateFlow<ScanState>(ScanState.Scanning)
    val state: StateFlow<ScanState> = _state
    private var lastBarcode: String? = null
    private var sessionGroup: Pair<Int?, String?>? = null   // loaded once: (group id, group name)

    fun onBarcodeDetected(barcode: String, sessionId: Int, serverUrl: String, offline: Boolean, apiKey: String = "") {
        if (_state.value !is ScanState.Scanning) return
        if (barcode == lastBarcode) return
        lastBarcode = barcode
        _state.value = ScanState.LookingUp
        viewModelScope.launch {
            try {
                val existingQty = repo.getExistingQty(offline, serverUrl, sessionId, barcode, apiKey)
                if (offline) { _state.value = ScanState.OfflineReady(barcode, existingQty); return@launch }
                val resp = au.com.harcourtapples.bopro.api.ApiClient.service(serverUrl, apiKey).getProduct(barcode)
                val group = sessionGroup ?: repo.getSession(false, serverUrl, apiKey, sessionId)
                    ?.let { it.groupId to it.groupName }.also { sessionGroup = it }
                val product = resp.body()
                _state.value = when {
                    // Session limited to a group: block products outside it (no group counts as outside).
                    resp.isSuccessful && group?.first != null && product != null && product.groupInfoKnown &&
                        product.groupId != group.first ->
                        ScanState.OutOfGroup(product.description, barcode, group.second ?: "the selected group")
                    resp.isSuccessful -> ScanState.ProductFound(product!!, barcode, existingQty)
                    resp.code() == 404 -> ScanState.NotFound(barcode)
                    else -> ScanState.Error("Server error ${resp.code()}")
                }
            } catch (e: Exception) { _state.value = ScanState.Error(e.message ?: "Network error") }
        }
    }

    fun submitCount(sessionId: Int, barcode: String, qty: Double, serverUrl: String, offline: Boolean, apiKey: String = "", description: String = "") {
        _state.value = ScanState.Saving
        viewModelScope.launch {
            try {
                repo.addCount(offline, serverUrl, sessionId, barcode, qty, apiKey, description)
                delay(400)
                reset()
            } catch (e: Exception) { _state.value = ScanState.Error(e.message ?: "Failed to save") }
        }
    }

    fun reset() { lastBarcode = null; _state.value = ScanState.Scanning }

    companion object {
        fun factory(repo: StocktakeRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ScanViewModel(repo) }
        }
    }
}
