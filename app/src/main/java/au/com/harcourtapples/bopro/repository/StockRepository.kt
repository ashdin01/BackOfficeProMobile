package au.com.harcourtapples.bopro.repository

import au.com.harcourtapples.bopro.api.ApiClient
import au.com.harcourtapples.bopro.api.models.AddBatchRequest
import au.com.harcourtapples.bopro.api.models.PrintLabelRequest
import au.com.harcourtapples.bopro.api.models.SetStockRequest
import au.com.harcourtapples.bopro.api.models.SetStockResponse
import au.com.harcourtapples.bopro.api.models.StockChangedBody
import au.com.harcourtapples.bopro.api.models.StockInfo
import com.google.gson.Gson

private data class ErrorBody(val message: String?)

/** Thrown when SOH moved on the server between the scan and the save. */
class StockChangedException(val currentQty: Double) : Exception("Stock on hand changed")

class StockRepository {

    /** Null when the barcode isn't a known active product. */
    suspend fun getStockInfo(serverUrl: String, apiKey: String, barcode: String): StockInfo? {
        val resp = ApiClient.service(serverUrl, apiKey).getStockInfo(barcode)
        if (resp.isSuccessful) return resp.body() ?: throw Exception("Empty response")
        if (resp.code() == 404) return null
        throw Exception("Server error ${resp.code()}")
    }

    /** Records a use-by batch (alerting only; stock on hand is unchanged). */
    suspend fun addUseBy(serverUrl: String, apiKey: String, barcode: String, useBy: String, qty: Double) {
        val resp = ApiClient.service(serverUrl, apiKey).addUseByBatch(barcode, AddBatchRequest(useBy, qty))
        if (!resp.isSuccessful) throw Exception("Server error ${resp.code()}")
    }

    /** Prints a shelf-edge label on the printer set up in BackOfficePro. */
    suspend fun printLabel(serverUrl: String, apiKey: String, barcode: String, copies: Int) {
        val resp = ApiClient.service(serverUrl, apiKey).printLabel(barcode, PrintLabelRequest(copies))
        if (!resp.isSuccessful) {
            val msg = try { Gson().fromJson(resp.errorBody()?.string(), ErrorBody::class.java)?.message } catch (_: Exception) { null }
            throw Exception(msg ?: "Print failed (server error ${resp.code()})")
        }
    }

    /** Sets SOH to [newQty]; the server records the difference as a movement. */
    suspend fun setStock(serverUrl: String, apiKey: String, barcode: String,
                         newQty: Double, expectedQty: Double): SetStockResponse {
        val resp = ApiClient.service(serverUrl, apiKey).setStock(barcode, SetStockRequest(newQty, expectedQty))
        if (resp.isSuccessful) return resp.body() ?: throw Exception("Empty response")
        if (resp.code() == 409) {
            val current = try { Gson().fromJson(resp.errorBody()?.string(), StockChangedBody::class.java)?.currentQty } catch (_: Exception) { null }
            if (current != null) throw StockChangedException(current)
        }
        throw Exception("Server error ${resp.code()}")
    }
}
