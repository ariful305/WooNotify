package com.example.data.repository

import android.util.Log
import com.example.data.database.ApiConfigDao
import com.example.data.database.VerifyLogDao
import com.example.data.model.*
import com.example.data.network.VerificationSyncService
import com.example.data.network.WooCommerceService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class VerifierRepository(
    private val apiConfigDao: ApiConfigDao,
    private val verifyLogDao: VerifyLogDao
) {
    private val TAG = "VerifierRepository"

    val configFlow: Flow<ApiConfigEntity?> = apiConfigDao.getConfigFlow()
    val allLogsFlow: Flow<List<VerifyLogEntity>> = verifyLogDao.getAllLogsFlow()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // Base Logging HttpClient
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    }

    private var cachedWooService: WooCommerceService? = null
    private var cachedWooUrl: String? = null

    private suspend fun getWooService(config: ApiConfigEntity): WooCommerceService? {
        if (!config.isValid) return null
        val cleanUrl = formatBaseUrl(config.wooUrl)
        if (cachedWooService != null && cachedWooUrl == cleanUrl) {
            return cachedWooService
        }

        return try {
            val retrofit = Retrofit.Builder()
                .baseUrl(cleanUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
            val service = retrofit.create(WooCommerceService::class.java)
            cachedWooService = service
            cachedWooUrl = cleanUrl
            service
        } catch (e: Exception) {
            Log.e(TAG, "Error building Retrofit for WooCommerce: ${e.message}", e)
            null
        }
    }

    private fun getSyncService(): VerificationSyncService {
        return Retrofit.Builder()
            .baseUrl("https://dummy-base.com/") // overridden dynamically via @Url
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(VerificationSyncService::class.java)
    }

    private fun formatBaseUrl(url: String): String {
        var clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        if (!clean.endsWith("/")) {
            clean = "$clean/"
        }
        return clean
    }

    suspend fun saveConfig(config: ApiConfigEntity) = withContext(Dispatchers.IO) {
        apiConfigDao.saveConfig(config)
        cachedWooService = null
        cachedWooUrl = null
    }

    suspend fun getConfig(): ApiConfigEntity? = withContext(Dispatchers.IO) {
        apiConfigDao.getConfig()
    }

    suspend fun fetchOrders(status: String? = null): List<WooCommerceOrder> = withContext(Dispatchers.IO) {
        val config = apiConfigDao.getConfig() ?: return@withContext emptyList()
        val service = getWooService(config) ?: return@withContext emptyList()

        try {
            service.getOrders(
                status = status,
                consumerKey = config.consumerKey,
                consumerSecret = config.consumerSecret
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching orders: ${e.message}", e)
            throw e
        }
    }

    suspend fun hasTransactionBeenProcessed(txnId: String): Boolean = withContext(Dispatchers.IO) {
        if (txnId.isBlank()) return@withContext false
        verifyLogDao.hasTransactionBeenProcessed(txnId)
    }

    suspend fun verifyAndSync(
        order: WooCommerceOrder,
        smsTxnId: String,
        smsSender: String,
        smsBody: String,
        updateWooCommerce: Boolean,
        syncCustomServer: Boolean
    ): VerifyLogEntity = withContext(Dispatchers.IO) {
        val config = apiConfigDao.getConfig() ?: ApiConfigEntity()
        var wooUpdated = false
        var serverSynced = false
        var serverResponse: String? = null

        // 1. Update WooCommerce order status (mark processing/completed and/or add order note)
        if (updateWooCommerce && config.isValid) {
            try {
                val service = getWooService(config)
                if (service != null) {
                    val noteText = "Verification Match Success! Payment Verified via SMS Sender: $smsSender. [TxID: $smsTxnId]"
                    
                    // Add Custom Note
                    try {
                        service.createOrderNote(
                            orderId = order.id,
                            body = OrderNoteRequest(note = noteText, customerNote = false),
                            consumerKey = config.consumerKey,
                            consumerSecret = config.consumerSecret
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to write WooCommerce note: ${e.message}")
                    }

                    // Update WooCommerce Order Status to "processing" (since we checked the payment!)
                    try {
                        service.updateOrderStatus(
                            orderId = order.id,
                            body = OrderStatusUpdate(status = "processing"),
                            consumerKey = config.consumerKey,
                            consumerSecret = config.consumerSecret
                        )
                        wooUpdated = true
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update WooCommerce status: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "WooCommerce update overall failure: ${e.message}")
            }
        }

        // 2. Sync to custom verification reporting server via POST
        if (syncCustomServer && config.syncServerUrl.isNotBlank()) {
            try {
                val syncService = getSyncService()
                val payload = mapOf(
                    "productId" to "wc_verifier",
                    "orderId" to order.id.toString(),
                    "orderNumber" to order.number,
                    "customerName" to order.billing.fullName,
                    "orderTotal" to order.total,
                    "orderTransactionId" to order.transactionId,
                    "smsTransactionId" to smsTxnId,
                    "smsSender" to smsSender,
                    "smsBody" to smsBody,
                    "status" to "VERIFIED",
                    "verificationTimestamp" to System.currentTimeMillis().toString()
                )

                val response = syncService.sendVerificationToCustomServer(config.syncServerUrl, payload)
                if (response.isSuccessful) {
                    serverSynced = true
                    serverResponse = "HTTP ${response.code()}: Success"
                } else {
                    serverResponse = "HTTP ${response.code()}: ${response.errorBody()?.string() ?: "Unknown error"}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Custom server sync failed: ${e.message}")
                serverResponse = "Error: ${e.message}"
            }
        }

        // 3. Write locally logged record inside Verification DB
        val logEntity = VerifyLogEntity(
            orderId = order.id,
            orderNumber = order.number,
            customerName = order.billing.fullName,
            orderTotal = "${order.currency} ${order.total}",
            orderTransactionId = order.transactionId,
            smsTransactionId = smsTxnId,
            smsSender = smsSender,
            smsBody = smsBody,
            verificationStatus = "VERIFIED",
            wooUpdated = wooUpdated,
            serverSynced = serverSynced,
            serverResponse = serverResponse
        )

        verifyLogDao.insertLog(logEntity)
        logEntity
    }

    suspend fun clearLogs() = withContext(Dispatchers.IO) {
        verifyLogDao.deleteAllLogs()
    }
}
