package au.com.harcourtapples.bopro.repository

import au.com.harcourtapples.bopro.api.ApiClient
import au.com.harcourtapples.bopro.api.models.PurchaseOrder
import au.com.harcourtapples.bopro.api.models.ReceiveRequest
import au.com.harcourtapples.bopro.api.models.ReceiveResponse
import au.com.harcourtapples.bopro.api.models.ReceiveLine

class ReceiveRepository {

    suspend fun getPurchaseOrders(serverUrl: String, apiKey: String): List<PurchaseOrder> {
        val resp = ApiClient.service(serverUrl, apiKey).getPurchaseOrders()
        if (resp.isSuccessful) return resp.body() ?: emptyList()
        throw Exception("Server error ${resp.code()}")
    }

    suspend fun getPurchaseOrder(serverUrl: String, apiKey: String, poNumber: String): PurchaseOrder {
        val resp = ApiClient.service(serverUrl, apiKey).getPurchaseOrder(poNumber)
        if (resp.isSuccessful) return resp.body() ?: throw Exception("Empty response")
        if (resp.code() == 404) throw Exception("PO '$poNumber' not found")
        throw Exception("Server error ${resp.code()}")
    }

    suspend fun submitReceipt(
        serverUrl: String,
        apiKey: String,
        poId: Int,
        invoiceNumber: String,
        lines: List<ReceiveLine>
    ): ReceiveResponse {
        val resp = ApiClient.service(serverUrl, apiKey).receivePurchaseOrder(
            poId, ReceiveRequest(invoiceNumber, lines)
        )
        if (resp.isSuccessful) return resp.body() ?: throw Exception("Empty response")
        throw Exception("Server error ${resp.code()}")
    }
}
