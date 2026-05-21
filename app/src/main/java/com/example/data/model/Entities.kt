package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_configs")
data class ApiConfigEntity(
    @PrimaryKey val id: Int = 1,
    val wooUrl: String = "",
    val consumerKey: String = "",
    val consumerSecret: String = "",
    val syncServerUrl: String = "",
    val autoVerify: Boolean = false,
    val senderFilter: String = "", // e.g. "bKash,Nagad,16247"
    val testRun: Boolean = false
) {
    val isValid: Boolean
        get() = wooUrl.isNotBlank() && consumerKey.isNotBlank() && consumerSecret.isNotBlank()
}

@Entity(tableName = "verification_logs")
data class VerifyLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: Long,
    val orderNumber: String,
    val customerName: String,
    val orderTotal: String,
    val orderTransactionId: String,  // Entered TxID by customer
    val smsTransactionId: String,    // Matched SMS TxID
    val smsSender: String,
    val smsBody: String,
    val verificationStatus: String,  // "VERIFIED", "MISMATCH", "FAILED"
    val wooUpdated: Boolean,        // Whether we updated WooCommerce order status/note
    val serverSynced: Boolean,      // Whether successfully POSTed to external verification server
    val serverResponse: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
