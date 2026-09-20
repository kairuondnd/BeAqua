package com.example.beaqua

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

const val OFFERING_PURCHASE = "PURCHASE"
const val OFFERING_REFILL = "REFILL"
const val PENDING_ORDER_TIMEOUT_MILLIS = 24L * 60L * 60L * 1000L

@IgnoreExtraProperties
data class Order(
    var id: String = "",
    var productId: String = "",
    var productName: String = "",
    var imageUri: String? = null,
    var customerName: String = "",
    var customerAddress: String = "",
    var stationOwnerUsername: String = "",
    var stationName: String = "",
    var quantity: Int = 0,
    var totalPrice: Double = 0.0,
    var containerType: String = "",
    var deliveryTimeSlot: String = "",
    var paymentMethod: String = "Cash on Delivery",
    var status: String = "Pending",
    var pendingExpiresAt: Long = 0L,
    var autoCancelledAt: Long = 0L,
    var cancellationReason: String = "",
    var customerLat: Double? = null,
    var customerLon: Double? = null,
    var timestamp: Long = 0,
    
    @get:PropertyName("isRated")
    @set:PropertyName("isRated")
    var isRated: Boolean = false,
    
    @get:PropertyName("isRushOrder")
    @set:PropertyName("isRushOrder")
    var isRushOrder: Boolean = false,
    
    var rushOrderFee: Double = 0.0,
    var deliveryFee: Double = 0.0,
    
    @get:PropertyName("isPaid")
    @set:PropertyName("isPaid")
    var isPaid: Boolean = false,

    @get:PropertyName("isSubscriptionOrder")
    @set:PropertyName("isSubscriptionOrder")
    var isSubscriptionOrder: Boolean = false,

    var subscriptionId: String = "",
    var scheduledDeliveryDate: Long = 0L,
    var estimatedDeliveryDate: Long = 0L,
    var estimatedDeliveryTimeZoneId: String = DeliveryEta.DEFAULT_TIME_ZONE_ID,
    var offeringType: String = OFFERING_PURCHASE,
    var refillServiceId: String = "",
    var refillInstructions: String = "",
    var emptyContainerCount: Int = 0
)

fun Order.isRefill(): Boolean = offeringType == OFFERING_REFILL

fun Order.pendingDeadline(): Long {
    if (pendingExpiresAt > 0L) return pendingExpiresAt
    if (timestamp <= 0L) return Long.MAX_VALUE
    return timestamp + PENDING_ORDER_TIMEOUT_MILLIS
}

fun Order.isExpiredPending(now: Long = System.currentTimeMillis()): Boolean {
    return status == "Pending" && pendingDeadline() <= now
}
