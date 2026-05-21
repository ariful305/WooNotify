package com.example.data.model

data class SmsMsg(
    val id: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val parsedTxnIds: List<String> = emptyList()
)
