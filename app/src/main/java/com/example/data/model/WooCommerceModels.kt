package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WooCommerceOrder(
    @Json(name = "id") val id: Long,
    @Json(name = "number") val number: String,
    @Json(name = "status") val status: String,
    @Json(name = "total") val total: String,
    @Json(name = "currency") val currency: String,
    @Json(name = "date_created") val dateCreated: String,
    @Json(name = "payment_method") val paymentMethod: String,
    @Json(name = "payment_method_title") val paymentMethodTitle: String,
    @Json(name = "transaction_id") val transactionId: String,
    @Json(name = "billing") val billing: BillingAddress,
    @Json(name = "line_items") val lineItems: List<LineItem>
)

@JsonClass(generateAdapter = true)
data class BillingAddress(
    @Json(name = "first_name") val firstName: String = "",
    @Json(name = "last_name") val lastName: String = "",
    @Json(name = "company") val company: String = "",
    @Json(name = "address_1") val address1: String = "",
    @Json(name = "address_2") val address2: String = "",
    @Json(name = "city") val city: String = "",
    @Json(name = "state") val state: String = "",
    @Json(name = "postcode") val postcode: String = "",
    @Json(name = "country") val country: String = "",
    @Json(name = "email") val email: String = "",
    @Json(name = "phone") val phone: String = ""
) {
    val fullName: String
        get() = "$firstName $lastName".trim()
}

@JsonClass(generateAdapter = true)
data class LineItem(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "product_id") val productId: Long,
    @Json(name = "variation_id") val variationId: Long,
    @Json(name = "quantity") val quantity: Int,
    @Json(name = "tax_class") val taxClass: String = "",
    @Json(name = "subtotal") val subtotal: String = "",
    @Json(name = "subtotal_tax") val subtotalTax: String = "",
    @Json(name = "total") val total: String = "",
    @Json(name = "total_tax") val totalTax: String = "",
    @Json(name = "price") val price: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class OrderStatusUpdate(
    @Json(name = "status") val status: String,
    @Json(name = "note") val note: String? = null
)

@JsonClass(generateAdapter = true)
data class OrderNoteRequest(
    @Json(name = "note") val note: String,
    @Json(name = "customer_note") val customerNote: Boolean = false
)
