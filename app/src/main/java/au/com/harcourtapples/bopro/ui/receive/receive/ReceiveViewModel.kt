package au.com.harcourtapples.bopro.ui.receive.receive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.com.harcourtapples.bopro.api.models.PoLine
import au.com.harcourtapples.bopro.api.models.PurchaseOrder
import au.com.harcourtapples.bopro.api.models.ReceiveLine
import au.com.harcourtapples.bopro.repository.ReceiveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Scan overlay state while the camera is open
sealed class LineScan {
    object Idle : LineScan()
    object Active : LineScan()
    data class Found(val line: PoLine, val alreadyReceived: Double) : LineScan()
    data class NotOnOrder(val barcode: String) : LineScan()
}

sealed class SubmitResult {
    data class Success(val status: String, val linesReceived: Int) : SubmitResult()
    data class Failure(val message: String) : SubmitResult()
}

data class ReceiveUiState(
    val po: PurchaseOrder? = null,
    // cartons received in THIS session, keyed by line_id
    val receivedQtys: Map<Int, Double> = emptyMap(),
    val invoiceNumber: String = "",
    val scan: LineScan = LineScan.Idle,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val submitResult: SubmitResult? = null,
    val error: String? = null
)

class ReceiveViewModel(private val repo: ReceiveRepository) : ViewModel() {

    private val _state = MutableStateFlow(ReceiveUiState())
    val state: StateFlow<ReceiveUiState> = _state

    fun loadPo(serverUrl: String, apiKey: String, poNumber: String) {
        viewModelScope.launch {
            _state.value = ReceiveUiState(isLoading = true)
            try {
                val po = repo.getPurchaseOrder(serverUrl, apiKey, poNumber)
                // Pre-populate with quantities already received (for PARTIAL POs)
                val existingQtys = po.lines.associate { it.id to it.receivedQty }
                _state.value = _state.value.copy(po = po, receivedQtys = existingQtys, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load PO")
            }
        }
    }

    fun startScanning()  { _state.value = _state.value.copy(scan = LineScan.Active) }
    fun stopScanning()   { _state.value = _state.value.copy(scan = LineScan.Idle) }
    fun dismissScan()    { _state.value = _state.value.copy(scan = LineScan.Active) } // return to active scanning

    fun onBarcodeDetected(barcode: String) {
        val po = _state.value.po ?: return
        val line = po.lines.find { it.barcode == barcode }
        if (line != null) {
            val alreadyReceived = _state.value.receivedQtys[line.id] ?: 0.0
            _state.value = _state.value.copy(scan = LineScan.Found(line, alreadyReceived))
        } else {
            _state.value = _state.value.copy(scan = LineScan.NotOnOrder(barcode))
        }
    }

    fun setLineQty(lineId: Int, qty: Double) {
        val updated = _state.value.receivedQtys.toMutableMap()
        if (qty > 0) updated[lineId] = qty else updated.remove(lineId)
        _state.value = _state.value.copy(receivedQtys = updated, scan = LineScan.Active)
    }

    fun setInvoiceNumber(n: String) { _state.value = _state.value.copy(invoiceNumber = n) }

    fun submit(serverUrl: String, apiKey: String) {
        val po = _state.value.po ?: return
        val lines = _state.value.receivedQtys
            .filter { (_, qty) -> qty > 0 }
            .map { (lineId, qty) -> ReceiveLine(lineId, qty) }
        if (lines.isEmpty()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, submitResult = null)
            try {
                val result = repo.submitReceipt(serverUrl, apiKey, po.id, _state.value.invoiceNumber, lines)
                _state.value = _state.value.copy(isSubmitting = false,
                    submitResult = SubmitResult.Success(result.status, result.linesReceived))
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSubmitting = false,
                    submitResult = SubmitResult.Failure(e.message ?: "Submission failed"))
            }
        }
    }

    companion object {
        fun factory(repo: ReceiveRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReceiveViewModel(repo) }
        }
    }
}
