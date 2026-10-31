package au.com.harcourtapples.bopro.ui.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.com.harcourtapples.bopro.api.models.SetStockResponse
import au.com.harcourtapples.bopro.api.models.StockInfo
import au.com.harcourtapples.bopro.repository.StockChangedException
import au.com.harcourtapples.bopro.repository.StockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class StockState {
    object Scanning  : StockState()
    object LookingUp : StockState()
    data class Found(val info: StockInfo) : StockState()
    data class Adjusting(val info: StockInfo) : StockState()
    object Saving : StockState()
    data class Saved(val description: String, val result: SetStockResponse) : StockState()
    data class NotFound(val barcode: String) : StockState()
    /** SOH moved since the scan: show the fresh figure and make the user re-enter. */
    data class Changed(val info: StockInfo) : StockState()
    data class Error(val message: String) : StockState()
}

class StockCheckViewModel(private val repo: StockRepository) : ViewModel() {

    private val _state = MutableStateFlow<StockState>(StockState.Scanning)
    val state: StateFlow<StockState> = _state
    private var lastBarcode: String? = null

    fun onBarcodeDetected(barcode: String, serverUrl: String, apiKey: String) {
        if (_state.value !is StockState.Scanning) return
        if (barcode == lastBarcode) return
        lastBarcode = barcode
        _state.value = StockState.LookingUp
        viewModelScope.launch {
            try {
                val info = repo.getStockInfo(serverUrl, apiKey, barcode)
                _state.value = if (info != null) StockState.Found(info) else StockState.NotFound(barcode)
            } catch (e: Exception) { _state.value = StockState.Error(e.message ?: "Network error") }
        }
    }

    fun startAdjust(info: StockInfo) { _state.value = StockState.Adjusting(info) }

    /** [newQty] is the new total on hand, not a +/- change. */
    fun save(info: StockInfo, newQty: Double, serverUrl: String, apiKey: String) {
        _state.value = StockState.Saving
        viewModelScope.launch {
            try {
                val result = repo.setStock(serverUrl, apiKey, info.barcode, newQty, info.sohQty)
                _state.value = StockState.Saved(info.description, result)
            } catch (e: StockChangedException) {
                _state.value = StockState.Changed(info.copy(sohQty = e.currentQty))
            } catch (e: Exception) { _state.value = StockState.Error(e.message ?: "Failed to save") }
        }
    }

    fun reset() { lastBarcode = null; _state.value = StockState.Scanning }

    companion object {
        fun factory(repo: StockRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { StockCheckViewModel(repo) }
        }
    }
}
