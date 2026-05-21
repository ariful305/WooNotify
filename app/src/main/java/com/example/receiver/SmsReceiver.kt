package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.data.model.SmsMsg
import com.example.util.SmsArrivalEventBus
import com.example.util.TxnIdParser
import java.util.*

class SmsReceiver : BroadcastReceiver() {
    private val TAG = "SmsReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (messages.isEmpty()) return

                // Group messages by sender if multi-part, or process each
                val bodyBuilder = java.lang.StringBuilder()
                val sender = messages[0].displayOriginatingAddress ?: "Unknown"
                val timestamp = messages[0].timestampMillis

                for (msg in messages) {
                    bodyBuilder.append(msg.displayMessageBody)
                }

                val fullBody = bodyBuilder.toString()
                val parsedTxnIds = TxnIdParser.parseTxnIds(fullBody)

                Log.d(TAG, "New SMS from: $sender: \"$fullBody\" | Parsed TxnIDs: $parsedTxnIds")

                val smsMsg = SmsMsg(
                    id = UUID.randomUUID().toString(),
                    sender = sender,
                    body = fullBody,
                    timestamp = timestamp,
                    parsedTxnIds = parsedTxnIds
                )

                // Push to live UI bus
                SmsArrivalEventBus.postNewSms(smsMsg)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing incoming SMS intent: ${e.message}", e)
            }
        }
    }
}
