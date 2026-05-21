package com.example.util

import java.util.regex.Pattern

object TxnIdParser {
    // Standard patterns for various payment gateways/systems
    private val PATTERNS = arrayOf(
        Pattern.compile("(?i)TrxID[:\\s]+([A-Z0-9]{8,12})"),
        Pattern.compile("(?i)TxnID[:\\s]+([A-Z0-9]{8,12})"),
        Pattern.compile("(?i)Transaction ID[:\\s]+([A-Z0-9]{8,12})"),
        Pattern.compile("(?i)TxID[:\\s]+([A-Z0-9]{8,12})"),
        Pattern.compile("(?i)TXN[:\\s]+([A-Z0-9]{8,12})"),
        Pattern.compile("(?i)payment of .* approved.*ref[:\\s]+([A-Z0-9]{6,12})")
    )

    // Standard fallback matcher for alphanumeric strings of length 8-12 that reside in standard payment messages
    private val GENERAL_ALPHANUMERIC_MUTATION = Pattern.compile("\\b([A-Z0-9]{8,12})\\b")

    /**
     * Parses the SMS message body and extracts potential transaction IDs.
     */
    fun parseTxnIds(body: String): List<String> {
        val results = mutableSetOf<String>()

        // 1. Check strict known keyword patterns first
        for (pattern in PATTERNS) {
            val matcher = pattern.matcher(body)
            while (matcher.find()) {
                val txnId = matcher.group(1)
                if (txnId != null && isValidTxnId(txnId)) {
                    results.add(txnId.uppercase())
                }
            }
        }

        // 2. If no Match is made and the text contains typical payment words (bkash, nagad, rocket, pay, cash, money, credit, deposit, tx, trx)
        // scan for general 8-12 uppercase alphanumeric words
        val containsPaymentClues = body.lowercase().run {
            contains("pay") || contains("bkash") || contains("nagad") || 
            contains("rocket") || contains("sender") || contains("received") || 
            contains("trx") || contains("txn") || contains("ref") ||
            contains("trans") || contains("bill") || contains("mfs") ||
            contains("tk") || contains("cash") || contains("amount")
        }

        if (containsPaymentClues) {
            val matcher = GENERAL_ALPHANUMERIC_MUTATION.matcher(body)
            while (matcher.find()) {
                val possibleId = matcher.group(1)
                // Filter out common false positives like "AM", "PM", "USD", "EUR" etc
                if (possibleId != null && isValidTxnId(possibleId) && !isFalsePositive(possibleId)) {
                    results.add(possibleId.uppercase())
                }
            }
        }

        return results.toList()
    }

    private fun isValidTxnId(id: String): Boolean {
        // Must contain at least one digit if it's alphanumeric, or have mixed letters and digits to avoid words
        val hasLetter = id.any { it.isLetter() }
        val hasDigit = id.any { it.isDigit() }
        return id.length >= 6 && (hasDigit || (hasLetter && hasDigit))
    }

    private fun isFalsePositive(id: String): Boolean {
        val list = setOf("JANUARY", "FEBRUARY", "OCTOBER", "NOVEMBER", "DECEMBER", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
        return list.contains(id.uppercase())
    }
}
