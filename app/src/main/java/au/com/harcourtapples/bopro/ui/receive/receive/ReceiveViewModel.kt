package au.com.harcourtapples.bopro.ui.receive.receive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.com.harcourtapples.bopro.api.models.PoLine
import au.com.harcourtapples.bopro.api.models.PurchaseOrder
import au.com.harcourtapples.bopro.api.models.CountLine
import au.com.harcourtapples.bopro.repository.ReceiveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Scan overlay state while the camera is open
sealed class LineScan {
    object Idle : LineScan()
    object Active : LineScan()
    data class Edit(val line: PoLine) : LineScan()          // manual correction sheet
    data class NotOnOrder(val barcode: String) : LineScan()
}

/** Last auto-added scan, shown as a banner while the camera keeps running. */
data class ScanFlash(val description: String, val added: Int, val total: Int, val isCarton: Boolean)

sealed class SubmitResult {
    data class Success(val linesSaved: Int) : SubmitResult()
    data class Failure(val message: String) : SubmitResult()
}

data class ReceiveUiState(
    val po: PurchaseOrder? = null,
    // units counted in this delivery (not yet received on the desktop), keyed by line_id
    val countedUnits: Map<Int, Int> = emptyMap(),
    val invoiceNumber: String = "",
    val scan: LineScan = LineScan.Idle,
    val lastScan: ScanFlash? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val submitResult: SubmitResult? = null,
    val error: String? = null
)

/** What a scanned code means for a PO line. */
enum class ScanKind { CARTON, UNIT }

private fun digitsKey(code: String) = code.trim().trimStart('0')

/**
 * GS1-128 labels (e.g. JC's cartons) encode "01" + 14-digit GTIN, optionally followed by
 * more fields (batch, best-before, count...). The "(01)" brackets are print-only and are
 * not in the scanned text. Returns just the GTIN for those; any other code is unchanged.
 * Real EAN-13 / ITF-14 codes are 13-14 digits, so the 16+ digit length rules out confusion.
 */
fun extractGtin(raw: String): String {
    val code = raw.trim().replace("(", "").replace(")", "")
    return if (code.length >= 16 && code.startsWith("01") && code.take(16).all(Char::isDigit))
        code.substring(2, 16) else raw
}

/**
 * Match a scanned code to a PO line. Exact matches win (carton SKU before product
 * barcode, since a carton SKU must never be read as a single unit); then a
 * leading-zero-insensitive match so GTIN-14 / EAN-13 forms of the same code agree.
 */
fun findLineForScan(lines: List<PoLine>, rawCode: String): Pair<PoLine, ScanKind>? {
    val code = extractGtin(rawCode)
    lines.firstOrNull { !it.cartonSku.isNullOrBlank() && it.cartonSku == code }?.let { return it to ScanKind.CARTON }
    lines.firstOrNull { it.barcode == code }?.let { return it to ScanKind.UNIT }
    val key = digitsKey(code)
    if (key.isEmpty()) return null
    lines.firstOrNull { !it.cartonSku.isNullOrBlank() && digitsKey(it.cartonSku) == key }?.let { return it to ScanKind.CARTON }
    lines.firstOrNull { digitsKey(it.barcode) == key }?.let { return it to ScanKind.UNIT }
    return null
}

class ReceiveViewModel(private val repo: ReceiveRepository) : ViewModel() {

    private val _state = MutableStateFlow(ReceiveUiState())
    val state: StateFlow<ReceiveUiState> = _state

    fun loadPo(serverUrl: String, apiKey: String, poNumber: String) {
        viewModelScope.launch {
            _state.value = ReceiveUiState(isLoading = true)
            try {
                val po = repo.getPurchaseOrder(serverUrl, apiKey, poNumber)
                // Resume any counts already staged for this PO
                val staged = po.lines.mapNotNull { l -> l.countedUnits?.takeIf { it > 0 }?.let { l.id to it } }.toMap()
                _state.value = _state.value.copy(po = po, countedUnits = staged,
                    invoiceNumber = po.invoiceNumber, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load PO")
            }
        }
    }

    fun startScanning()  { _state.value = _state.value.copy(scan = LineScan.Active, lastScan = null) }
    fun stopScanning()   { _state.value = _state.value.copy(scan = LineScan.Idle) }
    fun dismissScan()    { _state.value = _state.value.copy(scan = LineScan.Active) }
    fun cancelEdit() { _state.value = _state.value.copy(scan = LineScan.Idle) }
    fun editLine(line: PoLine) { _state.value = _state.value.copy(scan = LineScan.Edit(line)) }

    /** Carton SKU adds packQty units; product barcode adds 1 unit. Over-receiving is allowed. */
    fun onBarcodeDetected(code: String) {
        val po = _state.value.po ?: return
        val match = findLineForScan(po.lines, code)
        if (match == null) {
            _state.value = _state.value.copy(scan = LineScan.NotOnOrder(code))
            return
        }
        val (line, kind) = match
        val add = if (kind == ScanKind.CARTON) maxOf(1, line.packQty) else 1
        val total = (_state.value.countedUnits[line.id] ?: 0) + add
        _state.value = _state.value.copy(
            countedUnits = _state.value.countedUnits + (line.id to total),
            lastScan = ScanFlash(line.description, add, total, kind == ScanKind.CARTON)
        )
    }

    fun setLineUnits(lineId: Int, units: Int) {
        val updated = _state.value.countedUnits.toMutableMap()
        if (units > 0) updated[lineId] = units else updated.remove(lineId)
        _state.value = _state.value.copy(countedUnits = updated, scan = LineScan.Idle)
    }

    fun setInvoiceNumber(n: String) { _state.value = _state.value.copy(invoiceNumber = n) }

    /** Stage the counts on the server. The desktop Receive dialog confirms stock, costs and status. */
    fun submit(serverUrl: String, apiKey: String) {
        val po = _state.value.po ?: return
        val lines = _state.value.countedUnits
            .filter { (_, units) -> units > 0 }
            .map { (lineId, units) -> CountLine(lineId, units) }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, submitResult = null)
            try {
                val result = repo.saveCounts(serverUrl, apiKey, po.id, _state.value.invoiceNumber, lines)
                _state.value = _state.value.copy(isSubmitting = false,
                    submitResult = SubmitResult.Success(result.linesSaved))
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSubmitting = false,
                    submitResult = SubmitResult.Failure(e.message ?: "Save failed"))
            }
        }
    }

    companion object {
        fun factory(repo: ReceiveRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReceiveViewModel(repo) }
        }
    }
}
