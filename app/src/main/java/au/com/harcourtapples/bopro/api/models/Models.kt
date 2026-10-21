package au.com.harcourtapples.bopro.api.models

import com.google.gson.annotations.SerializedName

// ── Shared ────────────────────────────────────────────────────────────────────

data class Department(val id: Int, val code: String, val name: String)

// ── Stocktake ─────────────────────────────────────────────────────────────────

data class Product(
    val barcode: String,
    val description: String,
    @SerializedName("sell_price")  val sellPrice: Double,
    @SerializedName("cost_price")  val costPrice: Double,
    val unit: String,
    val brand: String?,
    @SerializedName("dept_name")   val deptName: String?,
    @SerializedName("soh_qty")     val sohQty: Double
)

data class Session(
    val id: Int,
    val label: String,
    @SerializedName("dept_name")   val deptName: String?,
    val status: String,
    @SerializedName("started_at")  val startedAt: String,
    @SerializedName("closed_at")   val closedAt: String?,
    @SerializedName("line_count")  val lineCount: Int,
    val notes: String?
)

data class CreateSessionRequest(
    val label: String,
    @SerializedName("department_id") val departmentId: Int?,
    val notes: String = "",
    @SerializedName("created_by")    val createdBy: String = "Android"
)

data class Count(
    val id: Int,
    @SerializedName("session_id")   val sessionId: Int,
    val barcode: String,
    @SerializedName("counted_qty")  val countedQty: Double,
    val description: String,
    @SerializedName("sell_price")   val sellPrice: Double,
    @SerializedName("dept_name")    val deptName: String?,
    @SerializedName("scanned_at")   val scannedAt: String
)

data class AddCountRequest(val barcode: String, val qty: Double)

// ── Receive ───────────────────────────────────────────────────────────────────

data class PurchaseOrder(
    val id: Int,
    @SerializedName("po_number")     val poNumber: String,
    @SerializedName("supplier_name") val supplierName: String,
    val status: String,
    @SerializedName("delivery_date") val deliveryDate: String,
    @SerializedName("created_at")    val createdAt: String,
    @SerializedName("line_count")    val lineCount: Int = 0,
    val lines: List<PoLine> = emptyList(),
    @SerializedName("invoice_number") val invoiceNumber: String = "",
    val notes: String = ""
)

data class PoLine(
    val id: Int,
    val barcode: String,
    @SerializedName("carton_sku")    val cartonSku: String = "",
    @SerializedName("counted_units") val countedUnits: Int? = null,
    val description: String,
    @SerializedName("ordered_qty")   val orderedQty: Double,
    @SerializedName("pack_qty")      val packQty: Int,
    @SerializedName("ordered_units") val orderedUnits: Int,
    @SerializedName("received_qty")  val receivedQty: Double,
    @SerializedName("unit_cost")     val unitCost: Double
)

data class ReceiveLine(
    @SerializedName("line_id")      val lineId: Int,
    @SerializedName("received_qty") val receivedQty: Double
)

data class ReceiveRequest(
    @SerializedName("invoice_number") val invoiceNumber: String,
    val lines: List<ReceiveLine>
)

data class CountLine(
    @SerializedName("line_id") val lineId: Int,
    val units: Int
)

data class CountsRequest(
    @SerializedName("invoice_number") val invoiceNumber: String,
    val lines: List<CountLine>
)

data class CountsResponse(
    @SerializedName("po_number")   val poNumber: String,
    val status: String,
    @SerializedName("lines_saved") val linesSaved: Int
)

data class ReceiveResponse(
    @SerializedName("po_number")     val poNumber: String,
    val status: String,
    @SerializedName("lines_received") val linesReceived: Int
)

// ── Order Prep ────────────────────────────────────────────────────────────────

data class Supplier(val id: Int, val code: String, val name: String)

data class OrderPrepItem(
    val barcode: String,
    val description: String,
    val unit: String,
    @SerializedName("pack_qty")          val packQty: Int,
    @SerializedName("pack_unit")         val packUnit: String,
    @SerializedName("reorder_point")     val reorderPoint: Double,
    @SerializedName("reorder_max")       val reorderMax: Double,
    @SerializedName("cost_price")        val costPrice: Double,
    @SerializedName("on_hand")           val onHand: Double,
    @SerializedName("last_ordered_qty")  val lastOrderedQty: Double?,
    @SerializedName("last_ordered_date") val lastOrderedDate: String
)

data class OrderPrepLine(val barcode: String, val cartons: Double)

data class CreateOrderPrepDraftRequest(
    @SerializedName("supplier_id") val supplierId: Int,
    val notes: String = "",
    val lines: List<OrderPrepLine>
)

data class CreateOrderPrepDraftResponse(
    @SerializedName("po_id")      val poId: Int,
    @SerializedName("po_number")  val poNumber: String,
    @SerializedName("line_count") val lineCount: Int
)
