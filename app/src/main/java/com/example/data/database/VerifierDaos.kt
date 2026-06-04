package com.example.data.database

import androidx.room.*
import com.example.data.model.ApiConfigEntity
import com.example.data.model.CachedOrderEntity
import com.example.data.model.VerifyLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiConfigDao {
    @Query("SELECT * FROM api_configs WHERE id = 1 LIMIT 1")
    fun getConfigFlow(): Flow<ApiConfigEntity?>

    @Query("SELECT * FROM api_configs WHERE id = 1 LIMIT 1")
    suspend fun getConfig(): ApiConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: ApiConfigEntity)
}

@Dao
interface VerifyLogDao {
    @Query("SELECT * FROM verification_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<VerifyLogEntity>>

    @Query("SELECT * FROM verification_logs ORDER BY timestamp DESC")
    suspend fun getAllLogs(): List<VerifyLogEntity>

    @Query("SELECT * FROM verification_logs WHERE orderId = :orderId LIMIT 1")
    suspend fun getLogByOrderId(orderId: Long): VerifyLogEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM verification_logs WHERE smsTransactionId = :txnId OR orderTransactionId = :txnId)")
    suspend fun hasTransactionBeenProcessed(txnId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: VerifyLogEntity)

    @Delete
    suspend fun deleteLog(log: VerifyLogEntity)

    @Query("DELETE FROM verification_logs")
    suspend fun deleteAllLogs()
}

@Dao
interface CachedOrderDao {
    @Query("SELECT * FROM cached_orders ORDER BY id DESC")
    fun getAllCachedOrdersFlow(): Flow<List<CachedOrderEntity>>

    @Query("SELECT * FROM cached_orders ORDER BY id DESC")
    suspend fun getAllCachedOrders(): List<CachedOrderEntity>

    @Query("SELECT * FROM cached_orders WHERE id = :id LIMIT 1")
    suspend fun getCachedOrderById(id: Long): CachedOrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedOrders(orders: List<CachedOrderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedOrder(order: CachedOrderEntity)

    @Query("DELETE FROM cached_orders WHERE id = :id")
    suspend fun deleteCachedOrderById(id: Long)

    @Query("DELETE FROM cached_orders")
    suspend fun clearAllCachedOrders()
}
