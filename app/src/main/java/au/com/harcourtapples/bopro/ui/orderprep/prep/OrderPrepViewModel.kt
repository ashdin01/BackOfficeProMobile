package au.com.harcourtapples.bopro.ui.orderprep.prep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import au.com.harcourtapples.bopro.api.models.OrderPrepItem
import au.com.harcourtapples.bopro.api.models.OrderPrepLine
import au.com.harcourtapples.bopro.repository.OrderPrepRepository
import kotlin.math.ceil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Per-item editable state, keyed by barcode.
data class OrderPrepEdit(
    val onHand: Double,
    val cartons: Int,
    val included: Boolean
)

sealed class OrderPrepSubmitResult {
    data class Success(val poNumber: String, val lineCount: Int) : OrderPrepSubmitResult()
    data class Failure(val message: String) : OrderPrepSubmitResult()
}

data class OrderPrepUiState(
    val supplierId: Int = 0,
    val supplierName: String = "",
    val items: List<OrderPrepItem> = emptyList(),
    val edits: Map<String, OrderPrepEdit> = emptyMap(),
    val notes: String = "",
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val submitResult: OrderPrepSubmitResult? = null,
    val error: String? = null
)

class OrderPrepViewModel(private val repo: OrderPrepRepository) : ViewModel() {

    private val _state = MutableStateFlow(OrderPrepUiState())
    val state: StateFlow<OrderPrepUiState> = _state

    fun load(serverUrl: String, apiKey: String, supplierId: Int, supplierName: String) {
        viewModelScope.launch {
            _state.value = OrderPrepUiState(
                supplierId = supplierId, supplierName = supplierName, isLoading = true
            )
            try {
                val items = repo.getItems(serverUrl, apiKey, supplierId)
                val edits = items.associate { item ->
                    val cartons = suggestedCartons(item, item.onHand)
                    item.barcode to OrderPrepEdit(onHand = item.onHand, cartons = cartons, included = cartons > 0)
                }
                _state.value = _state.value.copy(items = items, edits = edits, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load items")
            }
        }
    }

    fun setOnHand(item: OrderPrepItem, onHand: Double) {
        val cartons = suggestedCartons(item, onHand)
        updateEdit(item.barcode) { it.copy(onHand = onHand, cartons = cartons, included = cartons > 0) }
    }

    fun setCartons(barcode: String, cartons: Int) {
        updateEdit(barcode) { it.copy(cartons = cartons, included = cartons > 0) }
    }

    fun setIncluded(barcode: String, included: Boolean) {
        updateEdit(barcode) { it.copy(included = included) }
    }

    fun setNotes(notes: String) {
        _state.value = _state.value.copy(notes = notes)
    }

    private fun updateEdit(barcode: String, transform: (OrderPrepEdit) -> OrderPrepEdit) {
        val current = _state.value.edits[barcode] ?: return
        val updated = _state.value.edits.toMutableMap()
        updated[barcode] = transform(current)
        _state.value = _state.value.copy(edits = updated)
    }

    fun submit(serverUrl: String, apiKey: String) {
        val s = _state.value
        val lines = s.edits.filterValues { it.included && it.cartons > 0 }
            .map { (barcode, edit) -> OrderPrepLine(barcode, edit.cartons.toDouble()) }
        if (lines.isEmpty()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, submitResult = null)
            try {
                val result = repo.createDraft(serverUrl, apiKey, s.supplierId, s.notes, lines)
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    submitResult = OrderPrepSubmitResult.Success(result.poNumber, result.lineCount)
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    submitResult = OrderPrepSubmitResult.Failure(e.message ?: "Submission failed")
                )
            }
        }
    }

    companion object {
        /**
         * Mirrors the desktop's reorder-point trigger (models below the minimum
         * only) and BackOfficePro's calc_order_units/cartons_needed: top up to
         * reorder_max if set, otherwise default to 1 carton.
         */
        fun suggestedCartons(item: OrderPrepItem, onHand: Double): Int {
            if (onHand >= item.reorderPoint) return 0
            val packQty = if (item.packQty > 0) item.packQty else 1
            val neededUnits = if (item.reorderMax > 0) (item.reorderMax - onHand).coerceAtLeast(0.0) else 1.0
            return maxOf(1, ceil(neededUnits / packQty).toInt())
        }

        fun factory(repo: OrderPrepRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { OrderPrepViewModel(repo) }
        }
    }
}
