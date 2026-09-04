package com.example.beaqua

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class WeeklySubscription(
    var id: String = "",
    var customerUsername: String = "",
    var stationOwnerUsername: String = "",
    var stationName: String = "",
    var productId: String = "",
    var productName: String = "",
    var quantity: Int = 1,
    var containerType: String = "",
    var deliveryDay: String = "",
    var deliveryTimeSlot: String = "",
    var nextDeliveryAt: Long = 0L,
    var createdAt: Long = 0L,
    var active: Boolean = true,
    var lastOrderId: String = "",
    var lastOrderAt: Long = 0L,
    var lastStatus: String = "Scheduled",
    var offeringType: String = OFFERING_PURCHASE,
    var refillServiceId: String = "",
    var refillInstructions: String = "",
    var emptyContainerCount: Int = 0
)
