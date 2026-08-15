package au.com.harcourtapples.bopro.repository

import au.com.harcourtapples.bopro.api.ApiClient
import au.com.harcourtapples.bopro.api.models.CreateOrderPrepDraftRequest
import au.com.harcourtapples.bopro.api.models.CreateOrderPrepDraftResponse
import au.com.harcourtapples.bopro.api.models.OrderPrepItem
import au.com.harcourtapples.bopro.api.models.OrderPrepLine
import au.com.harcourtapples.bopro.api.models.Supplier

class OrderPrepRepository {

    suspend fun getSuppliers(serverUrl: String, apiKey: String): List<Supplier> {
        val resp = ApiClient.service(serverUrl, apiKey).getSuppliers()
        if (resp.isSuccessful) return resp.body() ?: emptyList()
        throw Exception("Server error ${resp.code()}")
    }

    suspend fun getItems(serverUrl: String, apiKey: String, supplierId: Int): List<OrderPrepItem> {
        val resp = ApiClient.service(serverUrl, apiKey).getOrderPrepItems(supplierId)
        if (resp.isSuccessful) return resp.body() ?: emptyList()
        throw Exception("Server error ${resp.code()}")
    }

    suspend fun createDraft(
        serverUrl: String,
        apiKey: String,
        supplierId: Int,
        notes: String,
        lines: List<OrderPrepLine>
    ): CreateOrderPrepDraftResponse {
        val resp = ApiClient.service(serverUrl, apiKey)
            .createOrderPrepDraft(CreateOrderPrepDraftRequest(supplierId, notes, lines))
        if (resp.isSuccessful) return resp.body() ?: throw Exception("Empty response")
        throw Exception("Server error ${resp.code()}")
    }
}
