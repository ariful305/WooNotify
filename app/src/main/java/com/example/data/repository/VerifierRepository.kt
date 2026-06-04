package com.example.data.repository

import android.util.Log
import com.example.data.database.ApiConfigDao
import com.example.data.database.CachedOrderDao
import com.example.data.database.VerifyLogDao
import com.example.data.model.*
import com.example.data.network.VerificationSyncService
import com.example.data.network.WooCommerceService
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class VerifierRepository(
    private val apiConfigDao: ApiConfigDao,
    private val verifyLogDao: VerifyLogDao,
    private val cachedOrderDao: CachedOrderDao
) {
    private val TAG = "VerifierRepository"

    val configFlow: Flow<ApiConfigEntity?> = apiConfigDao.getConfigFlow()
    val allLogsFlow: Flow<List<VerifyLogEntity>> = verifyLogDao.getAllLogsFlow()

    val allCachedOrdersFlow: Flow<List<WooCommerceOrder>> = cachedOrderDao.getAllCachedOrdersFlow()
        .map { list ->
            list.map { mapToWooOrder(it) }
        }
        .flowOn(Dispatchers.IO)

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private fun mapToCachedEntity(order: WooCommerceOrder): CachedOrderEntity {
        val itemsSummary = order.lineItems.joinToString(", ") { "${it.quantity}x ${it.name}" }
        val adapter = moshi.adapter(WooCommerceOrder::class.java)
        val rawJson = adapter.toJson(order)
        return CachedOrderEntity(
            id = order.id,
            number = order.number,
            status = order.status,
            total = order.total,
            currency = order.currency,
            dateCreated = order.dateCreated,
            paymentMethod = order.paymentMethod,
            paymentMethodTitle = order.paymentMethodTitle,
            transactionId = order.transactionId,
            customerName = order.billing.fullName,
            customerEmail = order.billing.email,
            customerPhone = order.billing.phone,
            itemsSummary = itemsSummary,
            rawJson = rawJson
        )
    }

    private fun mapToWooOrder(entity: CachedOrderEntity): WooCommerceOrder {
        val adapter = moshi.adapter(WooCommerceOrder::class.java)
        return try {
            adapter.fromJson(entity.rawJson) ?: WooCommerceOrder(
                id = entity.id,
                number = entity.number,
                status = entity.status,
                total = entity.total,
                currency = entity.currency,
                dateCreated = entity.dateCreated,
                paymentMethod = entity.paymentMethod,
                paymentMethodTitle = entity.paymentMethodTitle,
                transactionId = entity.transactionId,
                billing = BillingAddress(
                    firstName = entity.customerName.split(" ").firstOrNull() ?: "",
                    lastName = entity.customerName.split(" ").drop(1).joinToString(" "),
                    phone = entity.customerPhone,
                    email = entity.customerEmail
                ),
                lineItems = emptyList()
            )
        } catch (e: Exception) {
            Log.e("VerifierRepository", "Error deserializing cache: ${e.message}")
            WooCommerceOrder(
                id = entity.id,
                number = entity.number,
                status = entity.status,
                total = entity.total,
                currency = entity.currency,
                dateCreated = entity.dateCreated,
                paymentMethod = entity.paymentMethod,
                paymentMethodTitle = entity.paymentMethodTitle,
                transactionId = entity.transactionId,
                billing = BillingAddress(
                    firstName = entity.customerName.split(" ").firstOrNull() ?: "",
                    lastName = entity.customerName.split(" ").drop(1).joinToString(" "),
                    phone = entity.customerPhone,
                    email = entity.customerEmail
                ),
                lineItems = emptyList()
            )
        }
    }

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
            val orders = service.getOrders(
                status = status,
                consumerKey = config.consumerKey,
                consumerSecret = config.consumerSecret
            )
            val entities = orders.map { mapToCachedEntity(it) }
            cachedOrderDao.insertCachedOrders(entities)

            // Cache Sync & Cleanup: Remove trashed or deleted WooCommerce orders from our local DB cache
            try {
                if (status.isNullOrBlank()) {
                    // Fetching all (or default query with no status restrictions).
                    // Any order stored locally that is not returned in the fetched active list is deleted.
                    val fetchedIds = orders.map { it.id }.toSet()
                    val allCached = cachedOrderDao.getAllCachedOrders()
                    val staleEntities = allCached.filter { it.id !in fetchedIds }
                    for (stale in staleEntities) {
                        cachedOrderDao.deleteCachedOrderById(stale.id)
                        Log.d(TAG, "Sync: Removed trashed/deleted order #${stale.number} from cache.")
                    }
                } else {
                    // Fetching a specific subset of statuses (e.g. "pending,on-hold,processing").
                    // If a locally cached order has one of these status types, but is NOT returned in the fresh fetch,
                    // it means it has been trashed/deleted on the web or transitioned to another status (e.g. completed).
                    val fetchedIds = orders.map { it.id }.toSet()
                    val filterStatuses = status.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                    if (filterStatuses.isNotEmpty()) {
                        val allCached = cachedOrderDao.getAllCachedOrders()
                        val staleEntities = allCached.filter { cached ->
                            cached.status.lowercase() in filterStatuses && cached.id !in fetchedIds
                        }
                        for (stale in staleEntities) {
                            cachedOrderDao.deleteCachedOrderById(stale.id)
                            Log.d(TAG, "Sync status update: Removed stale order #${stale.number} from cache.")
                        }
                    }
                }
            } catch (cacheEx: Exception) {
                Log.e(TAG, "Failsafe: error cleaning redundant cached orders: ${cacheEx.message}")
            }

            orders
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching orders: ${e.message}", e)
            throw e
        }
    }

    suspend fun updateOrderStatus(orderId: Long, newStatus: String): WooCommerceOrder = withContext(Dispatchers.IO) {
        val config = apiConfigDao.getConfig() ?: throw Exception("API configuration is not set up")
        val service = getWooService(config) ?: throw Exception("Cannot initialize WooCommerce service")

        try {
            val updatedOrder = service.updateOrderStatus(
                orderId = orderId,
                body = OrderStatusUpdate(status = newStatus),
                consumerKey = config.consumerKey,
                consumerSecret = config.consumerSecret
            )
            // Save updated order directly in the database so the UI gets it instantly
            cachedOrderDao.insertCachedOrder(mapToCachedEntity(updatedOrder))
            updatedOrder
        } catch (e: Exception) {
            Log.e(TAG, "Error updating order status: ${e.message}", e)
            throw e
        }
    }

    suspend fun deleteCachedOrder(orderId: Long) = withContext(Dispatchers.IO) {
        cachedOrderDao.deleteCachedOrderById(orderId)
    }

    suspend fun clearCachedOrders() = withContext(Dispatchers.IO) {
        cachedOrderDao.clearAllCachedOrders()
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
