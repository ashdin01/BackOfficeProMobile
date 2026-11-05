package au.com.harcourtapples.bopro.ui.receive.receive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.com.harcourtapples.bopro.api.models.PoLine
import au.com.harcourtapples.bopro.api.models.PurchaseOrder
import au.com.harcourtapples.bopro.api.models.CountLine
import au.com.harcourtapples.bopro.repository.ReceiveDraft
import au.com.harcourtapples.bopro.repository.ReceiveRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// Scan overlay state while the camera is open
sealed class LineScan {
    object Idle : LineScan()
    object Active : LineScan()
    data class Edit(val line: PoLine) : LineScan()          // manual correction sheet
    data class NotOnOrder(val barcode: String) : LineScan()
    /** Just-scanned line whose product group needs a use-by date and has none yet. */
    data class UseBy(val line: PoLine) : LineScan()
}

/** Last auto-added scan, shown as a banner while the camera keeps running. */
data class ScanFlash(val description: String, val added: Int, val total: Int, val isCarton: Boolean,
                     val raw: String = "", val scannedDate: String? = null)

sealed class SubmitResult {
    data class Success(val linesSaved: Int) : SubmitResult()
    data class Failure(val message: String) : SubmitResult()
}

data class ReceiveUiState(
    val po: PurchaseOrder? = null,
    // units counted in this delivery (not yet received on the desktop), keyed by line_id
    val countedUnits: Map<Int, Int> = emptyMap(),
    // use-by/best-before dates (ISO yyyy-MM-dd) keyed by line_id
    val useByDates: Map<Int, String> = emptyMap(),
    // flagged lines the user chose to leave without a date, so scans don't re-prompt
    val useBySkipped: Set<Int> = emptySet(),
    val invoiceNumber: String = "",
    val scan: LineScan = LineScan.Idle,
    val lastScan: ScanFlash? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val submitResult: SubmitResult? = null,
    val restoredFromDraft: Boolean = false,
    val error: String? = null
)

/** True when counts/dates/invoice differ from what the server already has staged for this PO. */
fun ReceiveUiState.hasUnsentChanges(): Boolean {
    val po = po ?: return false
    if (isLoading || submitResult != null) return false
    val savedUnits = po.lines.mapNotNull { l -> l.countedUnits?.takeIf { it > 0 }?.let { l.id to it } }.toMap()
    val savedDates = po.lines.mapNotNull { l -> l.countedUseBy?.takeIf { it.isNotBlank() }?.let { l.id to it } }.toMap()
    return countedUnits.filterValues { it > 0 } != savedUnits || useByDates != savedDates || invoiceNumber != po.invoiceNumber
}

/** What a scanned code means for a PO line. */
enum class ScanKind { CARTON, UNIT }

private fun digitsKey(code: String) = code.trim().trimStart('0')

/**
 * GS1-128 labels (e.g. JC's cartons) encode "01" + 14-digit GTIN, optionally followed by
 * more fields (batch, best-before, count...). The "(01)" brackets are print-only and are
 * not in the scanned text. Returns just the GTIN for those; any other code is unchanged.
 * Real EAN-13 / ITF-14 codes are 13-14 digits, so the 16+ digit length rules out confusion.
 */
fun extractGtin(raw: String): String = parseGs1(raw)?.gtin ?: raw

/** What a GS1-128 carton label carries that we use: the GTIN-14 and an optional expiry date (ISO). */
data class Gs1Scan(val gtin: String, val expiryIso: String?)

/**
 * Parse a GS1-128 scan: "01" + GTIN-14, then optionally (15) best-before or (17) use-by,
 * both fixed-length YYMMDD so no separator is needed. Other AIs (10 batch, 37 count...) end
 * the walk, since they are variable length. Returns null if this isn't a GS1-128 string.
 */
fun parseGs1(raw: String): Gs1Scan? {
    val code = raw.trim().replace("(", "").replace(")", "")
    if (code.length < 16 || !code.startsWith("01") || !code.take(16).all(Char::isDigit)) return null
    var expiry: String? = null
    var i = 16
    while (i + 8 <= code.length) {
        val ai = code.substring(i, i + 2)
        if (ai != "15" && ai != "17") break
        val ymd = code.substring(i + 2, i + 8)
        if (!ymd.all(Char::isDigit)) break
        val iso = gs1DateToIso(ymd)
        // If both are present prefer use-by (17): it is the hard limit.
        if (iso != null && (expiry == null || ai == "17")) expiry = iso
        i += 8
    }
    return Gs1Scan(code.substring(2, 16), expiry)
}

/** GS1 YYMMDD -> ISO. Day "00" means the last day of that month. Null if not a real date. */
fun gs1DateToIso(yymmdd: String): String? {
    val y = 2000 + yymmdd.substring(0, 2).toInt()
    val m = yymmdd.substring(2, 4).toInt()
    var d = yymmdd.substring(4, 6).toInt()
    if (m !in 1..12) return null
    val last = java.util.Calendar.getInstance().apply { clear(); set(y, m - 1, 1) }
        .getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    if (d == 0) d = last
    if (d > last) return null
    return "%04d-%02d-%02d".format(java.util.Locale.US, y, m, d)
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
    private var draftKey: String? = null

    init {
        // Keep an on-phone copy of unsent counts, so a killed app or a stray back doesn't lose them.
        viewModelScope.launch {
            _state.map { st -> if (st.hasUnsentChanges()) ReceiveDraft(st.countedUnits.filterValues { it > 0 }, st.useByDates,
                st.useBySkipped.toList(), st.invoiceNumber) else null }
                .distinctUntilChanged()
                .collect { draft ->
                    val key = draftKey ?: return@collect
                    if (draft != null) repo.drafts.save(key, draft) else if (_state.value.po != null) repo.drafts.clear(key)
                }
        }
    }

    fun loadPo(serverUrl: String, apiKey: String, poNumber: String) {
        viewModelScope.launch {
            _state.value = ReceiveUiState(isLoading = true)
            try {
                val po = repo.getPurchaseOrder(serverUrl, apiKey, poNumber)
                // Resume any counts already staged for this PO
                val staged = po.lines.mapNotNull { l -> l.countedUnits?.takeIf { it > 0 }?.let { l.id to it } }.toMap()
                val dates = po.lines.mapNotNull { l -> l.countedUseBy?.takeIf { it.isNotBlank() }?.let { l.id to it } }.toMap()
                val key = "$serverUrl|${po.id}".also { draftKey = it }
                // Unsent counts from an earlier visit win over what the server has staged, but only
                // while the PO can still be received and only for lines that are still on it.
                val receivable = po.status == "SENT" || po.status == "PARTIAL"
                val draft = if (receivable) repo.drafts.load(key) else null
                if (!receivable) repo.drafts.clear(key)
                val ids = po.lines.map { it.id }.toSet()
                val dUnits = draft?.units?.filterKeys { it in ids }.orEmpty()
                if (draft != null && dUnits.isNotEmpty()) {
                    _state.value = _state.value.copy(po = po, countedUnits = dUnits,
                        useByDates = draft.useBy.filterKeys { it in ids }, useBySkipped = draft.skipped.filter { it in ids }.toSet(),
                        invoiceNumber = draft.invoice.ifBlank { po.invoiceNumber }, restoredFromDraft = true, isLoading = false)
                } else {
                    if (draft != null) repo.drafts.clear(key)
                    _state.value = _state.value.copy(po = po, countedUnits = staged, useByDates = dates,
                        invoiceNumber = po.invoiceNumber, isLoading = false)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load PO")
            }
        }
    }

    /** Drop the unsent counts (user chose Discard). */
    fun discardDraft() { draftKey?.let { repo.drafts.clear(it) } }

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
        val st = _state.value
        // A date printed on the label fills the line automatically, whether or not the group is flagged to track use-by.
        // One date is stored per line, so if cartons differ we keep the earliest.
        val scanned = parseGs1(code)?.expiryIso
        val existing = st.useByDates[line.id]
        val dates = if (scanned != null && (existing == null || scanned < existing))
            st.useByDates + (line.id to scanned) else st.useByDates
        val needsDate = line.trackUseBy == true && line.id !in dates && line.id !in st.useBySkipped
        _state.value = st.copy(
            countedUnits = st.countedUnits + (line.id to total),
            useByDates = dates,
            useBySkipped = if (scanned != null) st.useBySkipped - line.id else st.useBySkipped,
            lastScan = ScanFlash(line.description, add, total, kind == ScanKind.CARTON, raw = code, scannedDate = scanned),
            scan = if (needsDate) LineScan.UseBy(line) else st.scan
        )
    }

    /** null = no date: remembered so the same line isn't prompted again this session. */
    fun setUseBy(lineId: Int, iso: String?) {
        val st = _state.value
        _state.value = if (iso != null)
            st.copy(useByDates = st.useByDates + (lineId to iso), useBySkipped = st.useBySkipped - lineId, scan = LineScan.Active)
        else
            st.copy(useByDates = st.useByDates - lineId, useBySkipped = st.useBySkipped + lineId, scan = LineScan.Active)
    }

    fun setLineUnits(lineId: Int, units: Int, useBy: String? = null) {
        val st = _state.value
        val updated = st.countedUnits.toMutableMap()
        if (units > 0) updated[lineId] = units else updated.remove(lineId)
        val line = st.po?.lines?.firstOrNull { it.id == lineId }
        _state.value = st.copy(
            countedUnits = updated,
            useByDates = if (useBy != null) st.useByDates + (lineId to useBy) else st.useByDates - lineId,
            useBySkipped = if (useBy == null && line?.trackUseBy == true) st.useBySkipped + lineId else st.useBySkipped - lineId,
            scan = LineScan.Idle
        )
    }

    fun setInvoiceNumber(n: String) { _state.value = _state.value.copy(invoiceNumber = n) }

    /** Stage the counts on the server. The desktop Receive dialog confirms stock, costs and status. */
    fun submit(serverUrl: String, apiKey: String) {
        val po = _state.value.po ?: return
        val lines = _state.value.countedUnits
            .filter { (_, units) -> units > 0 }
            .map { (lineId, units) -> CountLine(lineId, units, _state.value.useByDates[lineId]) }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, submitResult = null)
            try {
                val result = repo.saveCounts(serverUrl, apiKey, po.id, _state.value.invoiceNumber, lines)
                draftKey?.let { repo.drafts.clear(it) }
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
