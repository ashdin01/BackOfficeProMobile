package au.com.harcourtapples.bopro.ui.warehouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.com.harcourtapples.bopro.api.models.WarehouseOrderItem
import au.com.harcourtapples.bopro.api.models.WarehouseOrderLine
import au.com.harcourtapples.bopro.repository.WarehouseOrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What the last scan turned up and is waiting on the user. */
sealed class WarehouseScanResult {
    /** A listed item; [currentQty] is what's already on the order (0 on first scan). */
    data class Listed(val item: WarehouseOrderItem, val currentQty: Double) : WarehouseScanResult()
    data class NotListed(val barcode: String) : WarehouseScanResult()
}

sealed class WarehouseSubmitResult {
    data class Success(val lineCount: Int, val totalUnits: Double, val csv: String, val pdf: String) : WarehouseSubmitResult()
    data class Failure(val message: String) : WarehouseSubmitResult()
}

data class WarehouseOrderUiState(
    val items: List<WarehouseOrderItem> = emptyList(),
    val qty: Map<String, Double> = emptyMap(),          // barcode -> quantity on the order (> 0 only)
    val notes: String = "",
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val scanning: Boolean = false,
    val scanResult: WarehouseScanResult? = null,
    val submitResult: WarehouseSubmitResult? = null,
    val error: String? = null
) {
    val lineCount: Int get() = qty.size
    val totalUnits: Double get() = qty.values.sum()
}

class WarehouseOrderViewModel(private val repo: WarehouseOrderRepository) : ViewModel() {

    private val _state = MutableStateFlow(WarehouseOrderUiState())
    val state: StateFlow<WarehouseOrderUiState> = _state

    fun load(serverUrl: String, apiKey: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val items = repo.getItems(serverUrl, apiKey)
                val listed = items.map { it.barcode }.toSet()
                // Drop anything saved from an earlier session that's no longer flagged on the server.
                val qty = repo.loadDraft().filter { (b, q) -> b in listed && q > 0 }
                _state.value = _state.value.copy(items = items, qty = qty, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load items")
            }
        }
    }

    fun startScanning() { _state.value = _state.value.copy(scanning = true, scanResult = null) }
    fun stopScanning()  { _state.value = _state.value.copy(scanning = false, scanResult = null) }
    fun dismissScanResult() { _state.value = _state.value.copy(scanResult = null) }

    /** Called for every camera detection; ignored while a result is awaiting the user. */
    fun onBarcodeDetected(raw: String) {
        val s = _state.value
        // GS1-128 "01"+GTIN-14, or a GTIN-14 with leading 0 (= the EAN-13 inside) -> plain product barcode
        var barcode = raw
        if (barcode.length >= 16 && barcode.startsWith("01") && barcode.take(16).all(Char::isDigit)) barcode = barcode.substring(2, 16)
        if (barcode.length == 14 && barcode.startsWith("0") && barcode.all(Char::isDigit) && s.items.none { it.barcode == barcode }) barcode = barcode.substring(1)
        if (!s.scanning || s.scanResult != null) return
        // Carton barcode (carton_sku) resolves to the same product line
        val item = s.items.firstOrNull { it.barcode == barcode }
            ?: s.items.firstOrNull { !it.cartonSku.isNullOrBlank() && it.cartonSku == barcode }
        _state.value = s.copy(
            scanResult = if (item == null) WarehouseScanResult.NotListed(barcode)
                         else WarehouseScanResult.Listed(item, s.qty[item.barcode] ?: 0.0)
        )
    }

    /** Rescan at the second shop location: the quantity is added to what's already on the order. */
    fun addQty(barcode: String, add: Double) {
        if (add <= 0) return
        setQtyInternal(barcode, (_state.value.qty[barcode] ?: 0.0) + add)
        _state.value = _state.value.copy(scanResult = null)
    }

    /** Overwrite a line's quantity from the list; 0 (or less) removes it. */
    fun setQty(barcode: String, qty: Double) = setQtyInternal(barcode, qty)

    private fun setQtyInternal(barcode: String, qty: Double) {
        val updated = _state.value.qty.toMutableMap()
        if (qty > 0) updated[barcode] = qty else updated.remove(barcode)
        _state.value = _state.value.copy(qty = updated)
        repo.saveDraft(updated)
    }

    fun setNotes(notes: String) { _state.value = _state.value.copy(notes = notes) }

    fun clearResult() { _state.value = _state.value.copy(submitResult = null) }

    fun submit(serverUrl: String, apiKey: String) {
        val s = _state.value
        if (s.qty.isEmpty() || s.isSubmitting) return
        val lines = s.qty.map { (b, q) -> WarehouseOrderLine(b, q) }
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, submitResult = null)
            try {
                val r = repo.submit(serverUrl, apiKey, s.notes, lines)
                repo.clearDraft()   // only once the PC confirms the files are written
                _state.value = _state.value.copy(
                    isSubmitting = false, qty = emptyMap(), notes = "", scanning = false,
                    submitResult = WarehouseSubmitResult.Success(r.lineCount, r.totalUnits, r.csv, r.pdf)
                )
            } catch (e: Exception) {
                // Draft stays on the phone so nothing is lost.
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    submitResult = WarehouseSubmitResult.Failure(e.message ?: "Could not send the order")
                )
            }
        }
    }

    companion object {
        fun factory(repo: WarehouseOrderRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { WarehouseOrderViewModel(repo) }
        }
    }
}
