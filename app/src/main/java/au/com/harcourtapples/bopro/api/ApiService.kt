package au.com.harcourtapples.bopro.api

import au.com.harcourtapples.bopro.api.models.*
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ── Health / settings ──────────────────────────────────────────────────────

    @GET("api/v1/health")
    suspend fun health(): Response<Map<String, String>>

    @GET("api/v1/departments")
    suspend fun getDepartments(): Response<List<Department>>

    // ── Stocktake ──────────────────────────────────────────────────────────────

    @GET("api/v1/products/{barcode}")
    suspend fun getProduct(@Path("barcode") barcode: String): Response<Product>

    @GET("api/v1/sessions")
    suspend fun getSessions(): Response<List<Session>>

    @POST("api/v1/sessions")
    suspend fun createSession(@Body request: CreateSessionRequest): Response<Map<String, Int>>

    @GET("api/v1/sessions/{id}")
    suspend fun getSession(@Path("id") id: Int): Response<Session>

    @GET("api/v1/sessions/{id}/counts")
    suspend fun getCounts(@Path("id") id: Int): Response<List<Count>>

    @POST("api/v1/sessions/{id}/counts")
    suspend fun addCount(@Path("id") id: Int, @Body request: AddCountRequest): Response<Map<String, Any>>

    @GET("api/v1/sessions/{id}/counts/barcode/{barcode}")
    suspend fun getCountForBarcode(
        @Path("id") id: Int,
        @Path("barcode") barcode: String
    ): Response<Map<String, Double>>

    @DELETE("api/v1/sessions/{id}/counts/{countId}")
    suspend fun deleteCount(@Path("id") id: Int, @Path("countId") countId: Int): Response<Map<String, Any>>

    // ── Receive ────────────────────────────────────────────────────────────────

    @GET("api/v1/purchase-orders")
    suspend fun getPurchaseOrders(): Response<List<PurchaseOrder>>

    @GET("api/v1/purchase-orders/{po_number}")
    suspend fun getPurchaseOrder(@Path("po_number") poNumber: String): Response<PurchaseOrder>

    @POST("api/v1/purchase-orders/{po_id}/receive")
    suspend fun receivePurchaseOrder(
        @Path("po_id") poId: Int,
        @Body request: ReceiveRequest
    ): Response<ReceiveResponse>
}
