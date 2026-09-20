package com.example.beaqua

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class RecurringDeliveryItem(
    var productId: String = "",
    var productName: String = "",
    var quantity: Int = 1,
    var containerType: String = ""
)

@IgnoreExtraProperties
data class WeeklySubscription(
    var id: String = "",
    var customerUsername: String = "",
    var stationOwnerUsername: String = "",
    var stationName: String = "",
    var productId: String = "",
    var productName: String = "",
    var quantity: Int = 1,
    var repeatEveryDays: Int = 7,
    var containerType: String = "",
    var deliveryDay: String = "",
    var deliveryTimeSlot: String = "",
    var nextDeliveryAt: Long = 0L,
    var createdAt: Long = 0L,
    var active: Boolean = true,
    var lastOrderId: String = "",
    var lastOrderIds: List<String> = emptyList(),
    var lastOrderAt: Long = 0L,
    var lastStatus: String = "Scheduled",
    var items: List<RecurringDeliveryItem> = emptyList(),
    var offeringType: String = OFFERING_PURCHASE,
    var refillServiceId: String = "",
    var refillInstructions: String = "",
    var emptyContainerCount: Int = 0
)

fun WeeklySubscription.deliveryItems(): List<RecurringDeliveryItem> {
    val savedItems = items
        .filter { it.productId.isNotBlank() && it.quantity > 0 }
        .distinctBy { it.productId }
    if (savedItems.isNotEmpty()) return savedItems
    if (offeringType != OFFERING_PURCHASE || productId.isBlank() || quantity <= 0) return emptyList()
    return listOf(
        RecurringDeliveryItem(
            productId = productId,
            productName = productName,
            quantity = quantity,
            containerType = containerType.ifBlank { productName }
        )
    )
}
