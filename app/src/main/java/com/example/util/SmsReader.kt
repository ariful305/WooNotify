package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.model.SmsMsg
import java.util.*

object SmsReader {
    private const val TAG = "SmsReader"

    /**
     * Reads the SMS inbox via ContentResolver and parses matching transaction IDs inside each message.
     */
    fun readInbox(context: Context, limit: Int = 100): List<SmsMsg> {
        val list = mutableListOf<SmsMsg>()
        val uri = Uri.parse("content://sms/inbox")
        val projection = arrayOf("_id", "address", "body", "date")

        try {
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "date DESC"
            )

            cursor?.use {
                val idCol = cursor.getColumnIndex("_id")
                val addressCol = cursor.getColumnIndex("address")
                val bodyCol = cursor.getColumnIndex("body")
                val dateCol = cursor.getColumnIndex("date")

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val id = if (idCol != -1) cursor.getString(idCol) else UUID.randomUUID().toString()
                    val sender = if (addressCol != -1) cursor.getString(addressCol) ?: "Unknown" else "Unknown"
                    val body = if (bodyCol != -1) cursor.getString(bodyCol) ?: "" else ""
                    val timestamp = if (dateCol != -1) cursor.getLong(dateCol) else System.currentTimeMillis()

                    val parsedTxnIds = TxnIdParser.parseTxnIds(body)

                    list.add(
                        SmsMsg(
                            id = id,
                            sender = sender,
                            body = body,
                            timestamp = timestamp,
                            parsedTxnIds = parsedTxnIds
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying SMS content resolver inbox: ${e.message}", e)
        }
        return list
    }
}
