package au.com.harcourtapples.bopro.repository

import android.content.Context
import au.com.harcourtapples.bopro.api.ApiClient
import au.com.harcourtapples.bopro.api.models.SubmitWarehouseOrderRequest
import au.com.harcourtapples.bopro.api.models.SubmitWarehouseOrderResponse
import au.com.harcourtapples.bopro.api.models.WarehouseOrderItem
import au.com.harcourtapples.bopro.api.models.WarehouseOrderLine
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * The in-progress Warehouse Order (barcode -> quantity), kept on the phone so it survives the app
 * being killed or backed out of. Nothing is stored on the server; the order only leaves the phone
 * on "Confirm order & send", and the draft is cleared once the PC has written the files.
 */
class WarehouseOrderDraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("warehouse_order_draft", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val type = object : TypeToken<Map<String, Double>>() {}.type

    fun load(): Map<String, Double> = try {
        prefs.getString(KEY, null)?.let { gson.fromJson<Map<String, Double>>(it, type) } ?: emptyMap()
    } catch (_: Exception) { emptyMap() }

    fun save(qty: Map<String, Double>) {
        prefs.edit().putString(KEY, gson.toJson(qty)).apply()
    }

    fun clear() { prefs.edit().remove(KEY).apply() }

    private companion object { const val KEY = "draft" }
}

class WarehouseOrderRepository(private val drafts: WarehouseOrderDraftStore) {

    fun loadDraft(): Map<String, Double> = drafts.load()
    fun saveDraft(qty: Map<String, Double>) = drafts.save(qty)
    fun clearDraft() = drafts.clear()

    suspend fun getItems(serverUrl: String, apiKey: String): List<WarehouseOrderItem> {
        val resp = ApiClient.service(serverUrl, apiKey).getWarehouseOrderItems()
        if (resp.isSuccessful) return resp.body() ?: emptyList()
        throw Exception("Server error ${resp.code()}")
    }

    suspend fun submit(
        serverUrl: String, apiKey: String, notes: String, lines: List<WarehouseOrderLine>
    ): SubmitWarehouseOrderResponse {
        val resp = ApiClient.service(serverUrl, apiKey)
            .submitWarehouseOrder(SubmitWarehouseOrderRequest(notes, lines))
        if (resp.isSuccessful) return resp.body() ?: throw Exception("Empty response")
        if (resp.code() == 500) throw Exception("The PC couldn't write the order files. Check the output folder, then try again.")
        if (resp.code() == 400) throw Exception("The server rejected the order. Refresh the list and try again.")
        throw Exception("Server error ${resp.code()}")
    }
}
