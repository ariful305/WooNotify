package com.example.data.network

import com.example.data.model.*
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface WooCommerceService {
    @GET("wp-json/wc/v3/orders")
    suspend fun getOrders(
        @Query("status") status: String? = null,
        @Query("per_page") perPage: Int = 50,
        @Query("page") page: Int = 1,
        @Query("consumer_key") consumerKey: String,
        @Query("consumer_secret") consumerSecret: String
    ): List<WooCommerceOrder>

    @PUT("wp-json/wc/v3/orders/{id}")
    suspend fun updateOrderStatus(
        @Path("id") orderId: Long,
        @Body body: OrderStatusUpdate,
        @Query("consumer_key") consumerKey: String,
        @Query("consumer_secret") consumerSecret: String
    ): WooCommerceOrder

    @POST("wp-json/wc/v3/orders/{id}/notes")
    suspend fun createOrderNote(
        @Path("id") orderId: Long,
        @Body body: OrderNoteRequest,
        @Query("consumer_key") consumerKey: String,
        @Query("consumer_secret") consumerSecret: String
    ): ResponseBody
}

interface VerificationSyncService {
    @POST
    suspend fun sendVerificationToCustomServer(
        @Url url: String,
        @Body body: Map<String, String>
    ): Response<ResponseBody>
}
